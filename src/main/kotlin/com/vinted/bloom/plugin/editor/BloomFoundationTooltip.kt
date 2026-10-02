package com.vinted.bloom.plugin.editor

import com.vinted.bloom.plugin.model.FoundationValue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

internal object BloomFoundationTooltip {
    private const val PREVIEW_WIDTH = 320
    private const val PREVIEW_HEIGHT = 140
    private val imageCache = ConcurrentHashMap<String, Path>()

    fun forValue(value: FoundationValue): String {
        val imagePath = imageCache.getOrPut(cacheKey(value)) {
            val image = createFoundationTooltipImage(value, PREVIEW_WIDTH, PREVIEW_HEIGHT)
                ?: return@getOrPut Path.of("")
            runCatching {
                Files.createTempFile("bloom-foundation-tooltip-", ".png").also { path ->
                    ImageIO.write(image, "png", path.toFile())
                }
            }.getOrDefault(Path.of(""))
        }
        if (imagePath.toString().isEmpty() || !Files.isRegularFile(imagePath)) return value.tooltip
        val text = escapeHtml(value.tooltip).replace("\n", "<br/>")
        return "<html><div style='padding:4px'><img src='${imagePath.toUri()}' width='$PREVIEW_WIDTH' height='$PREVIEW_HEIGHT'><br/>$text</div></html>"
    }

    private fun cacheKey(value: FoundationValue): String = buildString {
        append(value.expression)
        append('|')
        append(value.kind)
        append('|')
        append(value.resolvedValue)
        append('|')
        append(value.preview.hashCode())
        append('|')
        append(value.sourcePath?.toAbsolutePath())
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
