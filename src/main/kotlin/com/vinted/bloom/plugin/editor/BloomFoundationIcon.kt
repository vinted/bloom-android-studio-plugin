package com.vinted.bloom.plugin.editor

import com.vinted.bloom.plugin.model.FoundationPreview
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.AlphaComposite
import java.io.ByteArrayInputStream
import java.awt.image.BufferedImage
import java.awt.geom.Path2D
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import javax.imageio.ImageIO
import javax.swing.Icon

internal object BloomFoundationIconFactory {
    fun create(preview: FoundationPreview): Icon = when (preview) {
        is FoundationPreview.ColorSwatch -> SwatchIcon(preview.color)
        is FoundationPreview.ColorWithOpacity -> ColorWithOpacityIcon(preview.color, preview.opacity)
        is FoundationPreview.Opacity -> OpacityIcon(preview.value)
        is FoundationPreview.Shadow -> ShadowIcon(preview.dp)
        is FoundationPreview.Alignment -> AlignmentIcon(preview.bias)
        is FoundationPreview.SpacerSize -> SpacerSizeIcon(preview.dp)
        is FoundationPreview.BorderRadius -> BorderRadiusIcon(preview.dp)
        is FoundationPreview.BorderWidth -> BorderWidthIcon(preview.dp)
        is FoundationPreview.TextHighlight -> TextHighlightIcon(preview)
        is FoundationPreview.Typography -> TypographyIcon(preview)
        is FoundationPreview.DrawableFile -> DrawableIcon(preview.path, preview.content, preview.contentBytes)
        FoundationPreview.Metric -> MetricIcon()
    }
}

private const val SIZE = 16
private val INK = Color(0x4A4A4A)

private class SwatchIcon(private val color: Color) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (color.alpha < 255) drawCheckerboard(graphics, x, y, 15, 15)
        graphics.color = color
        graphics.fillRoundRect(x, y, 15, 15, 4, 4)
        graphics.dispose()
    }
}

private class ColorWithOpacityIcon(private val color: Color, private val opacity: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        drawCheckerboard(graphics, x, y, 15, 15)
        graphics.color = Color(color.red, color.green, color.blue, (opacity.coerceIn(0f, 1f) * 255).toInt())
        graphics.fillRoundRect(x, y, 15, 15, 4, 4)
        graphics.dispose()
    }
}

private class OpacityIcon(private val opacity: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        drawCheckerboard(graphics, x, y, 15, 15)
        graphics.color = Color(0, 0, 0, (opacity.coerceIn(0f, 1f) * 255).toInt())
        graphics.fillRoundRect(x + 1, y + 1, 13, 13, 4, 4)
        graphics.dispose()
    }
}

private class ShadowIcon(private val dp: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRoundRect(x, y, SIZE, SIZE, 4, 4)
        val strength = (0.16f + dp / 12f * 0.42f).coerceIn(0.16f, 0.58f)
        for (blur in 5 downTo 1) {
            val alpha = (strength * (6 - blur) / 5f * 255).toInt().coerceIn(0, 255)
            graphics.color = Color(0, 0, 0, alpha)
            graphics.fillRoundRect(x + 3 - blur / 2, y + 4 + blur / 2, 10 + blur, 9 + blur, 3 + blur, 3 + blur)
        }
        graphics.composite = AlphaComposite.SrcOver
        graphics.color = Color(0xD9D9D9)
        graphics.fillRoundRect(x + 4, y + 3, 8, 8, 2, 2)
        graphics.dispose()
    }
}

private class AlignmentIcon(private val bias: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRoundRect(x, y, SIZE, SIZE, 4, 4)
        graphics.color = Color(0xD8D8D8)
        graphics.fillRoundRect(x + 1, y + 3, 14, 10, 3, 3)
        val dotX = when {
            bias < -0.33f -> x + 3
            bias > 0.33f -> x + 10
            else -> x + 6
        }
        graphics.color = INK
        graphics.fillOval(dotX, y + 6, 4, 4)
        graphics.dispose()
    }
}

private class SpacerSizeIcon(private val dp: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRoundRect(x, y, SIZE, SIZE, 4, 4)
        val gap = (dp.coerceIn(2f, 12f) / 12f * 7f).coerceAtLeast(2f)
        val blockWidth = 4f
        val center = x + SIZE / 2f
        val left = center - gap / 2f - blockWidth
        val right = center + gap / 2f
        graphics.color = Color(0xBDBDBD)
        graphics.fillRoundRect(left.toInt(), y + 2, blockWidth.toInt(), 8, 2, 2)
        graphics.fillRoundRect(right.toInt(), y + 2, blockWidth.toInt(), 8, 2, 2)
        graphics.color = INK
        graphics.stroke = BasicStroke(1f)
        val chevron = Path2D.Double()
        val start = left + blockWidth + 1f
        val end = right - 1f
        val midpoint = (start + end) / 2f
        val braceY = y + 13f
        chevron.moveTo((midpoint - 2f).toDouble(), (braceY - 2f).toDouble())
        chevron.lineTo(midpoint.toDouble(), (braceY + 1f).toDouble())
        chevron.lineTo((midpoint + 2f).toDouble(), (braceY - 2f).toDouble())
        graphics.draw(chevron)
        graphics.dispose()
    }
}

private class BorderRadiusIcon(private val dp: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val radius = when {
            dp <= 0f -> 0.0
            dp >= 100f -> 7.0
            else -> (dp / 12f * 5.0).coerceIn(1.25, 5.0)
        }
        val left = x + 3.0
        val top = y + 3.0
        val bottom = y + 13.0
        val right = x + 13.0
        val path = Path2D.Double()
        path.moveTo(left, top)
        path.lineTo(left, bottom - radius)
        if (radius == 0.0) {
            path.lineTo(left, bottom)
        } else {
            val control = radius * 0.5522848
            path.curveTo(
                left,
                bottom - radius + control,
                left + radius - control,
                bottom,
                left + radius,
                bottom,
            )
        }
        path.lineTo(right, bottom)
        graphics.color = INK
        graphics.stroke = BasicStroke(1.5f)
        graphics.draw(path)
        graphics.dispose()
    }
}

private class BorderWidthIcon(private val dp: Float) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = INK
        graphics.stroke = BasicStroke(dp.coerceIn(0.5f, 5f) * 1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics.drawLine(x + 2, y + 8, x + 14, y + 8)
        graphics.dispose()
    }
}

private class TextHighlightIcon(private val preview: FoundationPreview.TextHighlight) : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRoundRect(x, y, SIZE, SIZE, 4, 4)
        graphics.color = Color(
            preview.backgroundColor.red,
            preview.backgroundColor.green,
            preview.backgroundColor.blue,
            (preview.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt(),
        )
        graphics.fillRoundRect(x + 1, y + 2, 13, 12, 2, 2)
        graphics.color = preview.textColor
        graphics.font = Font("Dialog", Font.BOLD, 12)
        graphics.drawString("A", x + 4, y + 12)
        graphics.dispose()
    }
}

private class TypographyIcon(private val preview: FoundationPreview.Typography) : Icon {
    private val font = preview.fontPath?.let { loadFont(it) } ?: Font("Dialog", Font.PLAIN, 12)

    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRect(x, y, SIZE, SIZE)
        graphics.color = preview.textColor ?: INK
        graphics.font = font.deriveFont(11f)
        graphics.drawString("Aa", x + 1, y + 12)
        graphics.dispose()
    }

    private fun loadFont(path: Path): Font? = runCatching {
        Font.createFont(Font.TRUETYPE_FONT, path.toFile())
    }.getOrNull()
}

private class DrawableIcon(private val path: Path?, content: String?, contentBytes: ByteArray?) : Icon {
    init {
        ImageIO.scanForPlugins()
    }

    private val image = contentBytes?.let { bytes -> runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() }
        ?: path?.takeIf { Files.isRegularFile(it) }
            ?.takeIf { it.fileName.toString().substringAfterLast('.').lowercase() in setOf("webp", "png", "jpg", "jpeg", "gif") }
            ?.let { runCatching { ImageIO.read(it.toFile()) }.getOrNull() }
    private val vector = content?.let { AndroidVectorDrawable.parse(it) }

    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        graphics.color = Color.WHITE
        graphics.fillRoundRect(x, y, SIZE, SIZE, 4, 4)
        when {
            image != null -> drawImagePreservingAspectRatio(graphics, image, x, y, SIZE, SIZE)
            vector != null -> vector.paint(graphics, x, y, SIZE, SIZE)
            else -> drawMissingDrawable(graphics, x, y)
        }
        graphics.dispose()
    }
}

internal fun createDrawablePreviewImage(
    preview: FoundationPreview.DrawableFile,
    width: Int,
    height: Int = width,
): BufferedImage? {
    ImageIO.scanForPlugins()
    val source = preview.contentBytes?.let { bytes ->
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    } ?: preview.path
        ?.takeIf { Files.isRegularFile(it) }
        ?.takeIf { it.fileName.toString().substringAfterLast('.').lowercase() in setOf("webp", "png", "jpg", "jpeg", "gif") }
        ?.let { runCatching { ImageIO.read(it.toFile()) }.getOrNull() }
    if (source != null) {
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { canvas ->
            val graphics = canvas.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            drawImagePreservingAspectRatio(graphics, source, 0, 0, width, height)
            graphics.dispose()
        }
    }
    val vector = preview.content?.let { AndroidVectorDrawable.parse(it) } ?: return null
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { canvas ->
        val graphics = canvas.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        vector.paint(graphics, 0, 0, width, height)
        graphics.dispose()
    }
}

private class MetricIcon : Icon {
    override fun getIconWidth() = SIZE
    override fun getIconHeight() = SIZE

    override fun paintIcon(c: java.awt.Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        graphics.color = Color(0x8A8A8A)
        graphics.stroke = BasicStroke(1.4f)
        graphics.drawLine(x + 2, y + 8, x + 14, y + 8)
        graphics.drawLine(x + 2, y + 5, x + 2, y + 11)
        graphics.drawLine(x + 14, y + 5, x + 14, y + 11)
        graphics.dispose()
    }
}

private fun drawCheckerboard(graphics: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    graphics.color = Color.WHITE
    graphics.fillRect(x, y, width, height)
    graphics.color = Color(0xE6E6E6)
    val tile = 3
    for (row in 0 until height step tile) {
        for (column in 0 until width step tile) {
            if ((row / tile + column / tile) % 2 == 0) graphics.fillRect(x + column, y + row, tile, tile)
        }
    }
}

private fun drawMissingDrawable(graphics: Graphics2D, x: Int, y: Int) {
    graphics.color = Color(0xE0E0E0)
    graphics.fillRoundRect(x + 1, y + 1, 14, 14, 3, 3)
    graphics.color = Color(0x6E6E6E)
    graphics.stroke = BasicStroke(1.2f)
    graphics.drawLine(x + 4, y + 11, x + 7, y + 7)
    graphics.drawLine(x + 7, y + 7, x + 10, y + 10)
    graphics.drawLine(x + 10, y + 10, x + 12, y + 8)
}

private data class ParsedVector(
    val viewportWidth: Double,
    val viewportHeight: Double,
    val viewportX: Double,
    val viewportY: Double,
    val paths: List<Pair<Path2D.Double, Color>>,
) {
    fun paint(graphics: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
        val copy = graphics.create() as Graphics2D
        val availableWidth = width - 2.0
        val availableHeight = height - 2.0
        val scale = min(availableWidth / viewportWidth, availableHeight / viewportHeight)
        val renderedWidth = viewportWidth * scale
        val renderedHeight = viewportHeight * scale
        copy.translate(
            x + 1.0 + (availableWidth - renderedWidth) / 2.0,
            y + 1.0 + (availableHeight - renderedHeight) / 2.0,
        )
        copy.scale(scale, scale)
        copy.translate(-viewportX, -viewportY)
        paths.forEach { (path, color) ->
            copy.color = color
            copy.fill(path)
        }
        copy.dispose()
    }
}

private fun drawImagePreservingAspectRatio(
    graphics: Graphics2D,
    image: java.awt.Image,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) {
    val imageWidth = image.getWidth(null)
    val imageHeight = image.getHeight(null)
    if (imageWidth <= 0 || imageHeight <= 0) return
    val availableWidth = (width - 2).coerceAtLeast(1)
    val availableHeight = (height - 2).coerceAtLeast(1)
    val scale = min(availableWidth.toDouble() / imageWidth, availableHeight.toDouble() / imageHeight)
    val renderedWidth = (imageWidth * scale).roundToInt().coerceAtLeast(1)
    val renderedHeight = (imageHeight * scale).roundToInt().coerceAtLeast(1)
    val left = x + (width - renderedWidth) / 2
    val top = y + (height - renderedHeight) / 2
    graphics.drawImage(image, left, top, renderedWidth, renderedHeight, null)
}

private object AndroidVectorDrawable {
    private data class Viewport(val x: Double, val y: Double, val width: Double, val height: Double)

    private val androidViewport = Regex("android:viewportWidth=\"([^\"]+)\"[^>]*android:viewportHeight=\"([^\"]+)\"")
    private val svgViewBox = Regex(
        "viewBox\\s*=\\s*[\"']\\s*([-+]?\\d+(?:\\.\\d+)?)\\s+([-+]?\\d+(?:\\.\\d+)?)\\s+([-+]?\\d+(?:\\.\\d+)?)\\s+([-+]?\\d+(?:\\.\\d+)?)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val pathTag = Regex("<path\\b([^>]+)/?>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val androidPathData = Regex("android:pathData\\s*=\\s*[\"']([^\"']+)[\"']")
    private val svgPathData = Regex("(?:^|\\s)d\\s*=\\s*[\"']([^\"']+)[\"']")
    private val androidFillColor = Regex("android:fillColor\\s*=\\s*[\"']([^\"']+)[\"']")
    private val svgFillColor = Regex("(?:^|\\s)fill\\s*=\\s*[\"']([^\"']+)[\"']")
    private val androidFillType = Regex("android:fillType\\s*=\\s*[\"']([^\"']+)[\"']")
    private val svgFillRule = Regex("(?:^|\\s)fill-rule\\s*=\\s*[\"']([^\"']+)[\"']")

    fun parse(xml: String): ParsedVector? {
        val viewport = androidViewport.find(xml)?.let { match ->
            Viewport(
                x = 0.0,
                y = 0.0,
                width = match.groupValues[1].toDoubleOrNull() ?: return null,
                height = match.groupValues[2].toDoubleOrNull() ?: return null,
            )
        } ?: svgViewBox.find(xml)?.let { match ->
            Viewport(
                x = match.groupValues[1].toDoubleOrNull() ?: return@let null,
                y = match.groupValues[2].toDoubleOrNull() ?: return@let null,
                width = match.groupValues[3].toDoubleOrNull() ?: return@let null,
                height = match.groupValues[4].toDoubleOrNull() ?: return@let null,
            )
        } ?: return null
        val paths = pathTag.findAll(xml).mapNotNull { tag ->
            val data = androidPathData.find(tag.value)?.groupValues?.get(1)
                ?: svgPathData.find(tag.value)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val path = AndroidPathParser.parse(data) ?: return@mapNotNull null
            val fillType = androidFillType.find(tag.value)?.groupValues?.get(1)
                ?: svgFillRule.find(tag.value)?.groupValues?.get(1)
            if (fillType.equals("evenOdd", ignoreCase = true) || fillType.equals("evenodd", ignoreCase = true)) {
                path.windingRule = Path2D.WIND_EVEN_ODD
            }
            val color = parseColor(
                androidFillColor.find(tag.value)?.groupValues?.get(1)
                    ?: svgFillColor.find(tag.value)?.groupValues?.get(1),
            ) ?: Color.DARK_GRAY
            path to color
        }.toList()
        return paths.takeIf { it.isNotEmpty() }?.let {
            ParsedVector(viewport.width, viewport.height, viewport.x, viewport.y, it)
        }
    }

    private fun parseColor(value: String?): Color? {
        val hex = value?.removePrefix("#") ?: return null
        return runCatching {
            when (hex.length) {
                3 -> Color(hex.map { "$it$it" }.joinToString("").toInt(16))
                4 -> {
                    val argb = hex.map { "$it$it" }.joinToString("").toLong(16)
                    Color((argb shr 16).toInt(), (argb shr 8 and 0xFF).toInt(), (argb and 0xFF).toInt(), (argb shr 24).toInt())
                }
                6 -> Color(hex.toInt(16))
                8 -> {
                    val argb = hex.toLong(16)
                    Color((argb shr 16 and 0xFF).toInt(), (argb shr 8 and 0xFF).toInt(), (argb and 0xFF).toInt(), (argb shr 24).toInt())
                }
                else -> null
            }
        }.getOrNull()
    }
}

private object AndroidPathParser {
    private val token = Regex("[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")

    fun parse(data: String): Path2D.Double? {
        val tokens = token.findAll(data).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val path = Path2D.Double()
        var index = 0
        var command = 'M'
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        var previousControlX = 0.0
        var previousControlY = 0.0

        fun isCommand(value: String) = value.length == 1 && value[0].isLetter()
        fun number(): Double? = tokens.getOrNull(index)?.toDoubleOrNull()?.also { index++ }
        fun relative(value: Double, axis: Double, relativeCommand: Boolean) = if (relativeCommand) axis + value else value

        while (index < tokens.size) {
            if (isCommand(tokens[index])) command = tokens[index++][0]
            val relativeCommand = command.isLowerCase()
            when (command.lowercaseChar()) {
                'm' -> {
                    val nx = number() ?: break
                    val ny = number() ?: break
                    x = relative(nx, x, relativeCommand)
                    y = relative(ny, y, relativeCommand)
                    path.moveTo(x, y)
                    startX = x
                    startY = y
                    previousControlX = x
                    previousControlY = y
                    command = if (relativeCommand) 'l' else 'L'
                }
                'l' -> {
                    val nx = number() ?: break
                    val ny = number() ?: break
                    x = relative(nx, x, relativeCommand)
                    y = relative(ny, y, relativeCommand)
                    path.lineTo(x, y)
                    previousControlX = x
                    previousControlY = y
                }
                'h' -> {
                    x = relative(number() ?: break, x, relativeCommand)
                    path.lineTo(x, y)
                    previousControlX = x
                    previousControlY = y
                }
                'v' -> {
                    y = relative(number() ?: break, y, relativeCommand)
                    path.lineTo(x, y)
                    previousControlX = x
                    previousControlY = y
                }
                'c' -> {
                    val c1x = relative(number() ?: break, x, relativeCommand)
                    val c1y = relative(number() ?: break, y, relativeCommand)
                    val c2x = relative(number() ?: break, x, relativeCommand)
                    val c2y = relative(number() ?: break, y, relativeCommand)
                    x = relative(number() ?: break, x, relativeCommand)
                    y = relative(number() ?: break, y, relativeCommand)
                    path.curveTo(c1x, c1y, c2x, c2y, x, y)
                    previousControlX = c2x
                    previousControlY = c2y
                }
                's' -> {
                    val c1x = 2 * x - previousControlX
                    val c1y = 2 * y - previousControlY
                    val c2x = relative(number() ?: break, x, relativeCommand)
                    val c2y = relative(number() ?: break, y, relativeCommand)
                    x = relative(number() ?: break, x, relativeCommand)
                    y = relative(number() ?: break, y, relativeCommand)
                    path.curveTo(c1x, c1y, c2x, c2y, x, y)
                    previousControlX = c2x
                    previousControlY = c2y
                }
                'q' -> {
                    val cx = relative(number() ?: break, x, relativeCommand)
                    val cy = relative(number() ?: break, y, relativeCommand)
                    x = relative(number() ?: break, x, relativeCommand)
                    y = relative(number() ?: break, y, relativeCommand)
                    path.quadTo(cx, cy, x, y)
                    previousControlX = cx
                    previousControlY = cy
                }
                't' -> {
                    val cx = 2 * x - previousControlX
                    val cy = 2 * y - previousControlY
                    x = relative(number() ?: break, x, relativeCommand)
                    y = relative(number() ?: break, y, relativeCommand)
                    path.quadTo(cx, cy, x, y)
                    previousControlX = cx
                    previousControlY = cy
                }
                'a' -> {
                    val radiusX = number() ?: break
                    val radiusY = number() ?: break
                    val rotation = number() ?: break
                    val largeArc = (number() ?: break) != 0.0
                    val sweep = (number() ?: break) != 0.0
                    val nextX = relative(number() ?: break, x, relativeCommand)
                    val nextY = relative(number() ?: break, y, relativeCommand)
                    appendArc(path, x, y, radiusX, radiusY, rotation, largeArc, sweep, nextX, nextY)
                    x = nextX
                    y = nextY
                    previousControlX = x
                    previousControlY = y
                }
                'z' -> {
                    path.closePath()
                    x = startX
                    y = startY
                    previousControlX = x
                    previousControlY = y
                }
                else -> return null
            }
        }
        return path
    }

    private fun appendArc(
        path: Path2D.Double,
        startX: Double,
        startY: Double,
        radiusX: Double,
        radiusY: Double,
        rotationDegrees: Double,
        largeArc: Boolean,
        sweep: Boolean,
        endX: Double,
        endY: Double,
    ) {
        var rx = abs(radiusX)
        var ry = abs(radiusY)
        if (rx == 0.0 || ry == 0.0 || (startX == endX && startY == endY)) {
            path.lineTo(endX, endY)
            return
        }

        val phi = Math.toRadians(rotationDegrees % 360.0)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val deltaX = (startX - endX) / 2.0
        val deltaY = (startY - endY) / 2.0
        val transformedX = cosPhi * deltaX + sinPhi * deltaY
        val transformedY = -sinPhi * deltaX + cosPhi * deltaY

        val radiiCorrection = transformedX * transformedX / (rx * rx) + transformedY * transformedY / (ry * ry)
        if (radiiCorrection > 1.0) {
            val correction = sqrt(radiiCorrection)
            rx *= correction
            ry *= correction
        }

        val denominator = rx * rx * transformedY * transformedY + ry * ry * transformedX * transformedX
        val numerator = rx * rx * ry * ry - denominator
        val coefficient = if (denominator == 0.0) {
            0.0
        } else {
            val sign = if (largeArc == sweep) -1.0 else 1.0
            sign * sqrt((numerator / denominator).coerceAtLeast(0.0))
        }
        val centerPrimeX = coefficient * rx * transformedY / ry
        val centerPrimeY = coefficient * -ry * transformedX / rx
        val centerX = cosPhi * centerPrimeX - sinPhi * centerPrimeY + (startX + endX) / 2.0
        val centerY = sinPhi * centerPrimeX + cosPhi * centerPrimeY + (startY + endY) / 2.0

        val startVectorX = (transformedX - centerPrimeX) / rx
        val startVectorY = (transformedY - centerPrimeY) / ry
        val endVectorX = (-transformedX - centerPrimeX) / rx
        val endVectorY = (-transformedY - centerPrimeY) / ry
        var deltaAngle = atan2(
            startVectorX * endVectorY - startVectorY * endVectorX,
            startVectorX * endVectorX + startVectorY * endVectorY,
        )
        if (!sweep && deltaAngle > 0.0) deltaAngle -= 2.0 * Math.PI
        if (sweep && deltaAngle < 0.0) deltaAngle += 2.0 * Math.PI

        val segments = ceil(abs(deltaAngle) / (Math.PI / 2.0)).toInt().coerceAtLeast(1)
        val segmentAngle = deltaAngle / segments
        val cubicFactor = 4.0 / 3.0 * tan(segmentAngle / 4.0)

        fun point(angle: Double): Pair<Double, Double> {
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            return (
                centerX + cosPhi * rx * cosAngle - sinPhi * ry * sinAngle
            ) to (
                centerY + sinPhi * rx * cosAngle + cosPhi * ry * sinAngle
            )
        }

        fun derivative(angle: Double): Pair<Double, Double> {
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            return (
                -cosPhi * rx * sinAngle - sinPhi * ry * cosAngle
            ) to (
                -sinPhi * rx * sinAngle + cosPhi * ry * cosAngle
            )
        }

        val startAngle = atan2(startVectorY, startVectorX)
        repeat(segments) { segment ->
            val firstAngle = startAngle + segment * segmentAngle
            val secondAngle = firstAngle + segmentAngle
            val firstPoint = point(firstAngle)
            val secondPoint = point(secondAngle)
            val firstDerivative = derivative(firstAngle)
            val secondDerivative = derivative(secondAngle)
            path.curveTo(
                firstPoint.first + cubicFactor * firstDerivative.first,
                firstPoint.second + cubicFactor * firstDerivative.second,
                secondPoint.first - cubicFactor * secondDerivative.first,
                secondPoint.second - cubicFactor * secondDerivative.second,
                secondPoint.first,
                secondPoint.second,
            )
        }
    }
}
