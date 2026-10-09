package com.vinted.bloom.plugin.catalog

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.components.Service
import com.vinted.bloom.plugin.model.FoundationKind
import com.vinted.bloom.plugin.model.FoundationPreview
import com.vinted.bloom.plugin.model.FoundationValue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile

@Service(Service.Level.PROJECT)
class FoundationValueCatalog(private val project: Project) {
    private var snapshot: CatalogSnapshot? = null

    fun resolve(expression: String): FoundationValue? {
        val current = snapshot()
        return current.resolve(expression)
    }

    fun containsAlias(alias: String): Boolean = snapshot().aliases.containsKey(alias)

    fun invalidate() {
        snapshot = null
    }

    private fun snapshot(): CatalogSnapshot {
        val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
        val existing = snapshot
        if (existing != null && existing.modificationCount == modificationCount) return existing
        return synchronized(this) {
            val refreshed = snapshot
            if (refreshed != null && refreshed.modificationCount == modificationCount) {
                refreshed
            } else {
                CatalogSnapshot.build(project, modificationCount).also { snapshot = it }
            }
        }
    }

    private data class CatalogSnapshot(
        val modificationCount: Long,
        val values: Map<String, FoundationValue>,
        val aliases: Map<String, String>,
        val resources: ResourceValueResolver,
    ) {
        fun resolve(input: String): FoundationValue? {
            val expression = input.trim().trimEnd(',', ';', ')')
            val visited = mutableSetOf<String>()
            var candidate = expression
                .removeSuffix(".color")
                .removeSuffix(".dp")
                .removeSuffix(".rawValue")
                .removeSuffix(".id")
            while (aliases.containsKey(candidate) && visited.add(candidate)) {
                candidate = aliases.getValue(candidate)
            }

            values[candidate]?.let { value ->
                val resolvedDimension = value.resolvedValue
                    .removePrefix("dimensions.")
                    .let { values["BloomTheme.dimensions.$it"]?.resolvedValue }
                return value.copy(
                    expression = expression,
                    resolvedValue = resolvedDimension?.let { "$it dp" } ?: value.resolvedValue,
                )
            }

            val resource = RESOURCE_EXPRESSION.matchEntire(candidate)
            if (resource != null) {
                return resourceValue(
                    expression = expression,
                    type = resource.groupValues[1],
                    name = resource.groupValues[2],
                )
            }

            val xmlResource = XML_RESOURCE_EXPRESSION.matchEntire(candidate)
            if (xmlResource != null) {
                return resourceValue(
                    expression = expression,
                    type = xmlResource.groupValues[1],
                    name = xmlResource.groupValues[2],
                )
            }

            if (candidate.startsWith("BloomIcon.")) {
                val assetName = candidate.substringAfter('.').substringBefore('.')
                val resourceName = camelToSnake(assetName)
                val entry = resourcesEntry(resources, "drawable", resourceName) ?: return null
                return FoundationValue(
                    expression = expression,
                    kind = FoundationKind.ICON,
                    label = assetName,
                    resolvedValue = entry?.let { "drawable/${it.name}" } ?: "asset dependency (resource not indexed)",
                    sourcePath = entry?.path,
                    preview = FoundationPreview.DrawableFile(entry?.path, entry?.content, entry?.contentBytes),
                )
            }

            if (candidate.startsWith("BloomIllustration.")) {
                val assetName = candidate.substringAfter('.').substringBefore('.')
                val resourceName = camelToSnake(assetName)
                val entry = resourcesEntry(resources, "drawable", resourceName) ?: return null
                return FoundationValue(
                    expression = expression,
                    kind = FoundationKind.DRAWABLE,
                    label = assetName,
                    resolvedValue = entry?.let { "drawable/${it.name}" } ?: "asset dependency (resource not indexed)",
                    sourcePath = entry?.path,
                    preview = FoundationPreview.DrawableFile(entry?.path, entry?.content, entry?.contentBytes),
                )
            }

            return null
        }

        private fun resourceValue(expression: String, type: String, name: String): FoundationValue? {
            val resource = resources.resolve(type, name)
            val kind = resources.kindFor(type) ?: return null
            val color = if (kind == FoundationKind.COLOR) resources.color(type, name) else null
            val entry = resourcesEntry(resources, type, name)
            return FoundationValue(
                expression = expression,
                kind = kind,
                label = name,
                resolvedValue = resource ?: "unresolved",
                sourcePath = entry?.path,
                preview = when {
                    color != null -> FoundationPreview.ColorSwatch(color)
                    kind == FoundationKind.DRAWABLE -> FoundationPreview.DrawableFile(entry?.path, entry?.content, entry?.contentBytes)
                    else -> FoundationPreview.Metric
                },
            )
        }

        private fun resourcesEntry(
            resolver: ResourceValueResolver,
            type: String,
            name: String,
        ): ResourceEntry? = resolverEntries[resolver]?.get("$type:$name")

        private fun darkResourcesEntry(
            resolver: ResourceValueResolver,
            type: String,
            name: String,
        ): ResourceEntry? = darkResolverEntries[resolver]?.get("$type:$name")

        companion object {
            private val RESOURCE_EXPRESSION = Regex("(?:[A-Za-z_][\\w]*\\.)?R\\.(color|dimen|drawable)\\.(\\w+)")
            private val XML_RESOURCE_EXPRESSION = Regex("@(?:android:)?(color|dimen|drawable)/(\\w+)")
            private val resolverEntries = java.util.WeakHashMap<ResourceValueResolver, Map<String, ResourceEntry>>()
            private val darkResolverEntries = java.util.WeakHashMap<ResourceValueResolver, Map<String, ResourceEntry>>()

            fun build(project: Project, modificationCount: Long): CatalogSnapshot {
                val values = linkedMapOf<String, FoundationValue>()
                val aliases = linkedMapOf<String, String>()
                val resourceValues = linkedMapOf<String, ResourceValue>()
                val resourceEntries = linkedMapOf<String, ResourceEntry>()
                val darkResourceEntries = linkedMapOf<String, ResourceEntry>()

                ProjectFileIndex.getInstance(project).iterateContent { file ->
                    if (file.isDirectory) return@iterateContent true
                    when {
                        file.parent?.name?.startsWith("drawable") == true -> {
                            val key = "drawable:${file.nameWithoutExtension}"
                            val entry = ResourceEntry(
                                "drawable",
                                file.nameWithoutExtension,
                                VfsUtil.virtualToIoFile(file).toPath(),
                                "",
                                file.takeIf { it.extension?.lowercase() in setOf("xml", "svg") }
                                    ?.let { runCatching { VfsUtil.loadText(it) }.getOrNull() },
                                null,
                            )
                            resourceEntries.putIfAbsent(key, entry)
                            if (isDarkResourcePath(file.parent?.name.orEmpty())) {
                                darkResourceEntries.putIfAbsent(key, entry)
                            }
                            resourceValues.putIfAbsent(key, ResourceValue("drawable", file.nameWithoutExtension, ""))
                        }
                        file.parent?.name?.startsWith("font") == true -> {
                            val key = "font:${file.nameWithoutExtension}"
                            resourceEntries.putIfAbsent(
                                key,
                                ResourceEntry(
                                    "font",
                                    file.nameWithoutExtension,
                                    VfsUtil.virtualToIoFile(file).toPath(),
                                    "",
                                    null,
                                    null,
                                ),
                            )
                        }
                        file.extension == "kt" || file.extension == "java" -> {
                            parseGeneratedFoundation(file, values)
                            parseAliases(file, aliases)
                        }
                        file.extension == "xml" -> parseResources(file, resourceValues, resourceEntries)
                    }
                    true
                }

                scanBloomFoundationSourceJars(values)
                scanBloomAssetAars(resourceValues, resourceEntries, darkResourceEntries)
                decorateFoundationPreviews(values, resourceEntries)

                val resolver = ResourceValueResolver(resourceValues)
                resolverEntries[resolver] = resourceEntries
                darkResolverEntries[resolver] = darkResourceEntries
                return CatalogSnapshot(modificationCount, values, aliases, resolver)
            }

            private fun decorateFoundationPreviews(
                values: MutableMap<String, FoundationValue>,
                resources: Map<String, ResourceEntry>,
            ) {
                values.keys.toList().forEach { key ->
                    val value = values[key] ?: return@forEach
                    val documentation = value.resolvedValue
                    val preview = when (value.kind) {
                        FoundationKind.BORDER_THEME -> {
                            val color = colorFromReference(documentation, "backgroundColor", values)
                            val opacity = opacityFromReference(documentation, "opacity", values)
                            color?.let { FoundationPreview.ColorWithOpacity(it, opacity ?: 1f) }
                        }
                        FoundationKind.BORDER_RADIUS -> value.resolvedValue
                            .removeSuffix(" dp")
                            .toFloatOrNull()
                            ?.let { FoundationPreview.BorderRadius(it) }
                        FoundationKind.BORDER_WIDTH -> value.resolvedValue
                            .removeSuffix(" dp")
                            .toFloatOrNull()
                            ?.let { FoundationPreview.BorderWidth(it) }
                        FoundationKind.OPACITY -> value.resolvedValue
                            .removeSuffix("%")
                            .toFloatOrNull()
                            ?.let { FoundationPreview.Opacity(if (it > 1f) it / 100f else it) }
                        FoundationKind.SHADOW -> dimensionValue(value.resolvedValue, values)
                            ?.let { FoundationPreview.Shadow(it) }
                        FoundationKind.ALIGNMENT -> value.resolvedValue
                            .toFloatOrNull()
                            ?.let { FoundationPreview.Alignment(it) }
                        FoundationKind.SPACER_SIZE -> dimensionValue(value.resolvedValue, values)
                            ?.let { FoundationPreview.SpacerSize(it) }
                        FoundationKind.TEXT_HIGHLIGHT -> {
                            val textColor = colorFromReference(documentation, "textColor", values)
                            val backgroundColor = colorFromReference(documentation, "backgroundColor", values)
                            if (textColor != null && backgroundColor != null) {
                                FoundationPreview.TextHighlight(
                                    textColor = textColor,
                                    backgroundColor = backgroundColor,
                                    backgroundOpacity = opacityFromReference(documentation, "backgroundOpacity", values) ?: 1f,
                                )
                            } else {
                                null
                            }
                        }
                        FoundationKind.TYPOGRAPHY -> {
                            val fontName = Regex("typefaceRes:\\s*R\\.font\\.(\\w+)")
                                .find(documentation)
                                ?.groupValues
                                ?.get(1)
                            val color = colorFromReference(documentation, "color", values)
                            val fontSize = Regex("fontSize:\\s*dimensions\\.(\\w+)")
                                .find(documentation)
                                ?.groupValues
                                ?.get(1)
                                ?.let { name -> values["BloomTheme.dimensions.$name"]?.resolvedValue }
                                ?.removeSuffix(" dp")
                                ?.toFloatOrNull()
                            if (fontName != null || color != null) {
                                val fontPath = fontName?.let { name ->
                                    resources["font:$name"]?.path
                                        ?: resources.values.firstOrNull { it.type == "font" && it.name == name }?.path
                                }
                                FoundationPreview.Typography(fontPath, color, fontSize)
                            } else {
                                null
                            }
                        }
                        else -> null
                    }
                    if (preview != null) values[key] = value.copy(preview = preview)
                }
            }

            private fun colorFromReference(
                documentation: String,
                field: String,
                values: Map<String, FoundationValue>,
            ): java.awt.Color? {
                val name = Regex("$field:\\s*colors\\.(\\w+)")
                    .find(documentation)
                    ?.groupValues
                    ?.get(1)
                    ?: return null
                return (values["BloomTheme.colors.$name"]?.preview as? FoundationPreview.ColorSwatch)?.color
            }

            private fun opacityFromReference(
                documentation: String,
                field: String,
                values: Map<String, FoundationValue>,
            ): Float? {
                val name = Regex("$field:\\s*opacities\\.(\\w+)")
                    .find(documentation)
                    ?.groupValues
                    ?.get(1)
                    ?: return null
                val raw = values["BloomTheme.opacities.$name"]?.resolvedValue ?: return null
                return raw.removeSuffix("%").toFloatOrNull()?.let { if (it > 1f) it / 100f else it }
            }

            private fun dimensionValue(raw: String, values: Map<String, FoundationValue>): Float? {
                raw.removeSuffix(" dp").toFloatOrNull()?.let { return it }
                val name = Regex("dimensions\\.(\\w+)").find(raw)?.groupValues?.get(1) ?: return null
                return values["BloomTheme.dimensions.$name"]?.resolvedValue
                    ?.removeSuffix(" dp")
                    ?.toFloatOrNull()
            }

            private fun parseGeneratedFoundation(file: VirtualFile, values: MutableMap<String, FoundationValue>) {
                val text = runCatching { VfsUtil.loadText(file) }.getOrNull() ?: return
                parseGeneratedFoundation(
                    text = text,
                    fileName = file.nameWithoutExtension,
                    sourcePath = VfsUtil.virtualToIoFile(file).toPath(),
                    values = values,
                    overwrite = true,
                )
            }

            private fun parseGeneratedFoundation(
                text: String,
                fileName: String,
                sourcePath: Path,
                values: MutableMap<String, FoundationValue>,
                overwrite: Boolean,
            ) {
                val type = fileName.removePrefix("Bloom")
                val theme = themeProperty(type) ?: return
                PROPERTY_WITH_DOC.findAll(text).forEach { match ->
                    val documentation = match.groupValues[1].trim()
                    val property = match.groupValues[2]
                    val raw = extractDocumentationValue(documentation)
                    if (raw.isEmpty()) return@forEach
                    val kind = foundationKind(type) ?: return@forEach
                    val resolved = when {
                        kind == FoundationKind.COLOR -> raw
                        raw.startsWith("dimensions.") -> raw
                        else -> raw
                    }
                    val color = if (kind == FoundationKind.COLOR) ResourceValueResolver.parseColor(resolved) else null
                    val value = FoundationValue(
                        expression = "BloomTheme.$theme.$property",
                        kind = kind,
                        label = property,
                        resolvedValue = displayRaw(kind, resolved),
                        sourcePath = sourcePath,
                        preview = color?.let { FoundationPreview.ColorSwatch(it) } ?: FoundationPreview.Metric,
                    )
                    if (overwrite) {
                        values["BloomTheme.$theme.$property"] = value
                    } else {
                        values.putIfAbsent("BloomTheme.$theme.$property", value)
                    }
                }
            }

            private fun parseAliases(file: VirtualFile, aliases: MutableMap<String, String>) {
                val text = runCatching { VfsUtil.loadText(file) }.getOrNull() ?: return
                ALIAS.findAll(text).forEach { match ->
                    aliases[match.groupValues[1]] = match.groupValues[2].trim()
                }
            }

            private fun parseResources(
                file: VirtualFile,
                values: MutableMap<String, ResourceValue>,
                entries: MutableMap<String, ResourceEntry>,
            ) {
                val text = runCatching { VfsUtil.loadText(file) }.getOrNull() ?: return
                RESOURCE.findAll(text).forEach { match ->
                    val type = match.groupValues[1]
                    val name = match.groupValues[2]
                    val raw = match.groupValues[3].trim()
                    val key = "$type:$name"
                    values[key] = ResourceValue(type, name, raw)
                    entries[key] = ResourceEntry(type, name, VfsUtil.virtualToIoFile(file).toPath(), raw, null, null)
                }
            }

            private fun scanBloomFoundationSourceJars(values: MutableMap<String, FoundationValue>) {
                val roots = bloomGradleCacheRoots()
                val sourceJars = roots.flatMap { root ->
                    runCatching {
                        Files.walk(root).use { stream ->
                            stream
                                .filter { path ->
                                    path.fileName.toString().endsWith("-sources.jar") &&
                                        path.fileName.toString().startsWith("android-bloom-")
                                }
                                .toList()
                        }
                    }.getOrDefault(emptyList())
                }

                sourceJars.forEach { sourceJar ->
                    runCatching {
                        ZipFile(sourceJar.toFile()).use { zip ->
                            zip.entries().asSequence()
                                .filter { entry ->
                                    !entry.isDirectory &&
                                        entry.name.startsWith("com/vinted/bloom/") &&
                                        entry.name.substringAfterLast('/').startsWith("Bloom") &&
                                        entry.name.endsWith(".kt")
                                }
                                .forEach { source ->
                                    val text = zip.getInputStream(source).use { it.readBytes().toString(Charsets.UTF_8) }
                                    parseGeneratedFoundation(
                                        text = text,
                                        fileName = source.name.substringAfterLast('/').removeSuffix(".kt"),
                                        sourcePath = sourceJar,
                                        values = values,
                                        overwrite = false,
                                    )
                                }
                        }
                    }
                }
            }

            private fun scanBloomAssetAars(
                values: MutableMap<String, ResourceValue>,
                entries: MutableMap<String, ResourceEntry>,
                darkEntries: MutableMap<String, ResourceEntry>,
            ) {
                val roots = bloomGradleCacheRoots()
                roots.flatMap { root ->
                    runCatching {
                        Files.walk(root).use { stream ->
                            stream.filter { path ->
                                path.fileName.toString().startsWith("android-bloom-assets-") && path.toString().endsWith(".aar")
                            }.toList()
                        }
                    }.getOrDefault(emptyList())
                }.forEach { aar ->
                    runCatching {
                        ZipFile(aar.toFile()).use { zip ->
                            zip.entries().asSequence()
                                .filter { entry ->
                                    val resourcePath = entry.name.substringBeforeLast('/')
                                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                                    resourcePath.startsWith("res/drawable") && extension in setOf("xml", "svg", "webp", "png", "jpg", "jpeg", "gif")
                                }
                                .forEach { resource ->
                                    val name = resource.name.substringAfterLast('/').removeSuffix(".xml")
                                        .removeSuffix(".svg")
                                        .removeSuffix(".webp")
                                        .removeSuffix(".png")
                                        .removeSuffix(".jpg")
                                        .removeSuffix(".jpeg")
                                        .removeSuffix(".gif")
                                    val key = "drawable:$name"
                                    val extension = resource.name.substringAfterLast('.', "").lowercase()
                                    val bytes = zip.getInputStream(resource).use { it.readBytes() }
                                    val content = bytes.takeIf { extension == "xml" || extension == "svg" }
                                        ?.toString(Charsets.UTF_8)
                                    val densityRank = drawableDensityRank(resource.name)
                                    val entry = ResourceEntry(
                                        type = "drawable",
                                        name = name,
                                        path = aar,
                                        rawValue = "",
                                        content = content,
                                        contentBytes = bytes.takeUnless { extension == "xml" || extension == "svg" },
                                        densityRank = densityRank,
                                    )
                                    val target = if (isDarkResourcePath(resource.name)) darkEntries else entries
                                    val existing = target[key]
                                    if (existing == null || (existing.path == aar && densityRank > existing.densityRank)) {
                                        values.putIfAbsent(key, ResourceValue("drawable", name, ""))
                                        target[key] = entry
                                    }
                                }
                        }
                    }
                }
            }

            private fun bloomGradleCacheRoots(): List<Path> = sequenceOf(
                System.getenv("GRADLE_USER_HOME")?.let { Paths.get(it, "caches/modules-2/files-2.1/com.vinted") },
                System.getProperty("user.home")?.let { Paths.get(it, ".gradle/caches/modules-2/files-2.1/com.vinted") },
            ).filterNotNull().distinct().filter { Files.isDirectory(it) }.toList()

            private fun drawableDensityRank(resourceName: String): Int = when {
                "-xxxhdpi" in resourceName -> 4
                "-xxhdpi" in resourceName -> 3
                "-xhdpi" in resourceName -> 2
                "-hdpi" in resourceName -> 1
                "-mdpi" in resourceName -> 1
                "/drawable/" in resourceName -> 5
                else -> 0
            }

            private fun isDarkResourcePath(path: String): Boolean {
                val directory = path.substringBeforeLast('/').substringAfterLast('/')
                return "night" in directory || "dark" in directory
            }

            private fun themeProperty(type: String): String? = when (type) {
                "Colors" -> "colors"
                "Dimensions" -> "dimensions"
                "Shadows" -> "shadows"
                "MediaSizes" -> "mediaSizes"
                "Opacities" -> "opacities"
                "BorderRadii" -> "borderRadii"
                "BorderWidths" -> "borderWidths"
                "SpacerSizes" -> "spacerSizes"
                "Alignments" -> "alignments"
                "BorderThemes" -> "borderThemes"
                "TextHighlights" -> "textHighlights"
                "Typographies" -> "typographies"
                else -> null
            }

            private fun foundationKind(type: String): FoundationKind? = when (type) {
                "Colors" -> FoundationKind.COLOR
                "Dimensions" -> FoundationKind.DIMENSION
                "Shadows" -> FoundationKind.SHADOW
                "MediaSizes" -> FoundationKind.MEDIA_SIZE
                "Opacities" -> FoundationKind.OPACITY
                "BorderRadii" -> FoundationKind.BORDER_RADIUS
                "BorderWidths" -> FoundationKind.BORDER_WIDTH
                "SpacerSizes" -> FoundationKind.SPACER_SIZE
                "Alignments" -> FoundationKind.ALIGNMENT
                "BorderThemes" -> FoundationKind.BORDER_THEME
                "TextHighlights" -> FoundationKind.TEXT_HIGHLIGHT
                "Typographies" -> FoundationKind.TYPOGRAPHY
                else -> null
            }

            private fun extractDocumentationValue(documentation: String): String = when {
                documentation.contains("typefaceRes:") -> documentation
                documentation.contains("backgroundColor:") -> documentation
                documentation.startsWith("raw value:") -> documentation.substringAfter(':').trim()
                documentation.startsWith("dimension:") -> documentation.substringAfter(':').trim()
                else -> documentation.substringAfter(':', missingDelimiterValue = "").trim()
            }

            private fun displayRaw(kind: FoundationKind, raw: String): String = when (kind) {
                FoundationKind.OPACITY -> raw.toDoubleOrNull()?.let { "${it * 100}%" } ?: raw
                FoundationKind.DIMENSION,
                FoundationKind.SHADOW,
                FoundationKind.MEDIA_SIZE,
                FoundationKind.BORDER_RADIUS,
                FoundationKind.BORDER_WIDTH,
                FoundationKind.SPACER_SIZE -> "$raw dp"
                else -> raw
            }

            private fun camelToSnake(value: String): String = value
                .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                .replace(Regex("([A-Za-z])([0-9])"), "$1_$2")
                .lowercase()

            private val PROPERTY_WITH_DOC = Regex("""/\*\*\s*([^*]*?)\*/\s*(?:@[^\n]*\n\s*)*val\s+(\w+)""", setOf(RegexOption.DOT_MATCHES_ALL))
            private val ALIAS = Regex("""(?:val|var)\s+(\w+)\s*=\s*([^\n]+)""")
            private val RESOURCE = Regex("""<(color|dimen|drawable)\s+name=\"([^\"]+)\"\s*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL))
        }
    }

    private data class ResourceEntry(
        val type: String,
        val name: String,
        val path: Path,
        val rawValue: String,
        val content: String?,
        val contentBytes: ByteArray?,
        val densityRank: Int = 0,
    )
}
