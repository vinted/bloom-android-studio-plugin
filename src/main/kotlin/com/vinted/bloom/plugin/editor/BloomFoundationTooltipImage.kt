package com.vinted.bloom.plugin.editor

import com.vinted.bloom.plugin.model.FoundationKind
import com.vinted.bloom.plugin.model.FoundationPreview
import com.vinted.bloom.plugin.model.FoundationValue
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.math.max
import kotlin.math.min

internal fun createFoundationTooltipImage(value: FoundationValue, width: Int, height: Int): BufferedImage? {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    graphics.color = Color.WHITE
    graphics.fillRect(0, 0, width, height)
    when (val preview = value.preview) {
        is FoundationPreview.ColorSwatch -> drawColor(graphics, preview.color, width, height)
        is FoundationPreview.ColorWithOpacity -> drawColorWithOpacity(graphics, preview.color, preview.opacity, width, height)
        is FoundationPreview.Opacity -> drawOpacity(graphics, preview.value, width, height)
        is FoundationPreview.Shadow -> drawShadow(graphics, preview.dp, width, height)
        is FoundationPreview.Alignment -> drawAlignment(graphics, preview.bias, width, height)
        is FoundationPreview.SpacerSize -> drawSpacer(graphics, preview.dp, value.resolvedValue, width, height)
        is FoundationPreview.BorderRadius -> drawBorderRadius(graphics, preview.dp, width, height)
        is FoundationPreview.BorderWidth -> drawBorderWidth(graphics, preview.dp, width, height)
        is FoundationPreview.TextHighlight -> drawTextHighlight(graphics, preview, width, height)
        is FoundationPreview.Typography -> drawTypography(graphics, preview, width, height)
        is FoundationPreview.DrawableFile -> drawDrawable(graphics, preview, width, height)
        FoundationPreview.Metric -> when (value.kind) {
            FoundationKind.SPACER_SIZE -> drawSpacer(graphics, number(value.resolvedValue) ?: 24f, value.resolvedValue, width, height)
            FoundationKind.DIMENSION -> drawDimension(graphics, value, width, height)
            FoundationKind.MEDIA_SIZE -> drawMediaSize(graphics, value, width, height)
            FoundationKind.ALIGNMENT -> drawAlignment(graphics, number(value.resolvedValue) ?: 0f, width, height)
            else -> drawMetric(graphics, value, width, height)
        }
    }
    graphics.dispose()
    return image
}

private fun drawColor(graphics: Graphics2D, color: Color, width: Int, height: Int) {
    graphics.color = color
    graphics.fillRoundRect(width / 2 - 45, height / 2 - 45, 90, 90, 16, 16)
}

private fun drawColorWithOpacity(graphics: Graphics2D, color: Color, opacity: Float, width: Int, height: Int) {
    drawCheckerboard(graphics, width / 2 - 55, height / 2 - 45, 110, 90)
    graphics.color = Color(color.red, color.green, color.blue, (opacity.coerceIn(0f, 1f) * 255).toInt())
    graphics.fillRoundRect(width / 2 - 55, height / 2 - 45, 110, 90, 16, 16)
}

private fun drawOpacity(graphics: Graphics2D, opacity: Float, width: Int, height: Int) {
    drawCheckerboard(graphics, width / 2 - 55, height / 2 - 45, 110, 90)
    graphics.color = Color(0, 0, 0, (opacity.coerceIn(0f, 1f) * 255).toInt())
    graphics.fillRoundRect(width / 2 - 55, height / 2 - 45, 110, 90, 16, 16)
}

private fun drawShadow(graphics: Graphics2D, dp: Float, width: Int, height: Int) {
    val left = width / 2 - 42
    val top = height / 2 - 38
    val strength = (0.12f + dp / 12f * 0.42f).coerceIn(0.12f, 0.56f)
    for (blur in 14 downTo 1) {
        val alpha = (strength * (15 - blur) / 14f * 255).toInt().coerceIn(0, 255)
        graphics.color = Color(0, 0, 0, alpha)
        graphics.fillRoundRect(left - blur / 2, top + blur / 2, 84 + blur, 70 + blur, 12 + blur, 12 + blur)
    }
    graphics.color = Color(0xD9D9D9)
    graphics.fillRoundRect(left, top, 84, 70, 10, 10)
}

private fun drawBorderRadius(graphics: Graphics2D, dp: Float, width: Int, height: Int) {
    val left = width / 2 - 50.0
    val top = height / 2 - 42.0
    val right = width / 2 + 50.0
    val bottom = height / 2 + 42.0
    val radius = when {
        dp <= 0f -> 0.0
        dp >= 100f -> 42.0
        else -> (dp / 12f * 30.0).coerceIn(8.0, 30.0)
    }
    graphics.color = Color(0x4A4A4A)
    graphics.stroke = BasicStroke(4f)
    graphics.drawRoundRect(left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
}

private fun drawBorderWidth(graphics: Graphics2D, dp: Float, width: Int, height: Int) {
    val stroke = (dp.coerceIn(0.5f, 8f) * 1.7f).coerceIn(1f, 12f)
    graphics.color = Color(0x4A4A4A)
    graphics.stroke = BasicStroke(stroke)
    graphics.drawRoundRect(width / 2 - 55, height / 2 - 38, 110, 76, 10, 10)
}

private fun drawTextHighlight(graphics: Graphics2D, preview: FoundationPreview.TextHighlight, width: Int, height: Int) {
    graphics.color = Color(
        preview.backgroundColor.red,
        preview.backgroundColor.green,
        preview.backgroundColor.blue,
        (preview.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt(),
    )
    graphics.fillRoundRect(width / 2 - 75, height / 2 - 42, 150, 84, 8, 8)
    graphics.color = preview.textColor
    graphics.font = Font("Dialog", Font.BOLD, 58)
    graphics.drawString("A", width / 2 - 20, height / 2 + 21)
}

private fun drawTypography(graphics: Graphics2D, preview: FoundationPreview.Typography, width: Int, height: Int) {
    val font = preview.fontPath?.takeIf { Files.isRegularFile(it) }?.let {
        runCatching { Font.createFont(Font.TRUETYPE_FONT, it.toFile()) }.getOrNull()
    } ?: Font("Dialog", Font.PLAIN, 54)
    graphics.color = preview.textColor ?: Color(0x4A4A4A)
    val fontSize = preview.fontSize?.let { (it / 16f * 54f).coerceIn(28f, 68f) } ?: 54f
    graphics.font = font.deriveFont(fontSize)
    graphics.drawString("Aa", width / 2 - 47, height / 2 + 20)
}

private fun drawDrawable(graphics: Graphics2D, preview: FoundationPreview.DrawableFile, width: Int, height: Int) {
    val drawable = createDrawablePreviewImage(preview, width - 40, height - 20) ?: return
    graphics.drawImage(drawable, 20, 10, null)
}

private fun drawSpacer(graphics: Graphics2D, dp: Float, label: String, width: Int, height: Int) {
    val gap = dp.coerceIn(4f, 92f) * 1.25f
    val center = width / 2f
    val blockWidth = 36f
    val left = center - gap / 2f - blockWidth
    val right = center + gap / 2f
    graphics.color = Color(0xBDBDBD)
    graphics.fillRoundRect(left.toInt(), 38, blockWidth.toInt(), 58, 8, 8)
    graphics.fillRoundRect(right.toInt(), 38, blockWidth.toInt(), 58, 8, 8)
    graphics.color = Color(0x4A4A4A)
    graphics.stroke = BasicStroke(2f)
    drawChevron(graphics, (left + blockWidth + right) / 2f, 101f)
    drawCenteredText(graphics, label, center, 132, 16)
}

private fun drawDimension(graphics: Graphics2D, value: FoundationValue, width: Int, height: Int) {
    graphics.color = Color(0xBDBDBD)
    graphics.fillRoundRect(width / 2 - 55, 35, 110, 55, 8, 8)
    graphics.color = Color(0x4A4A4A)
    graphics.stroke = BasicStroke(2f)
    drawArrow(graphics, (width / 2 - 55).toFloat(), 105, (width / 2 + 55).toFloat(), 105)
    drawCenteredText(graphics, value.resolvedValue, width / 2f, 128, 16)
}

private fun drawMediaSize(graphics: Graphics2D, value: FoundationValue, width: Int, height: Int) {
    val size = number(value.resolvedValue)?.coerceIn(12f, 72f) ?: 40f
    graphics.color = Color(0xBDBDBD)
    graphics.fillRoundRect(width / 2 - size.toInt() / 2, height / 2 - size.toInt() / 2 - 8, size.toInt(), size.toInt(), 10, 10)
    drawCenteredText(graphics, value.resolvedValue, width / 2f, 126, 16)
}

private fun drawAlignment(graphics: Graphics2D, bias: Float, width: Int, height: Int) {
    graphics.color = Color(0xD8D8D8)
    graphics.fillRoundRect(width / 2 - 110, 38, 220, 58, 8, 8)
    val x = when {
        bias > 0.33f -> width / 2 + 82
        bias < -0.33f -> width / 2 - 82
        else -> width / 2
    }
    graphics.color = Color(0x4A4A4A)
    graphics.fillOval(x - 12, 55, 24, 24)
    drawCenteredText(graphics, bias.toString(), width / 2f, 126, 16)
}

private fun drawMetric(graphics: Graphics2D, value: FoundationValue, width: Int, height: Int) {
    graphics.color = Color(0x4A4A4A)
    graphics.stroke = BasicStroke(4f)
    graphics.drawLine(width / 2 - 70, height / 2, width / 2 + 70, height / 2)
    drawCenteredText(graphics, value.resolvedValue, width / 2f, 120, 16)
}

private fun drawCheckerboard(graphics: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    graphics.color = Color.WHITE
    graphics.fillRect(x, y, width, height)
    graphics.color = Color(0xE6E6E6)
    for (row in 0 until height step 10) {
        for (column in 0 until width step 10) {
            if ((row / 10 + column / 10) % 2 == 0) graphics.fillRect(x + column, y + row, 10, 10)
        }
    }
}

private fun drawArrow(graphics: Graphics2D, startX: Float, y: Int, endX: Float, endY: Int) {
    graphics.drawLine(startX.toInt(), y, endX.toInt(), endY)
    graphics.drawLine(startX.toInt(), y, (startX + 8).toInt(), y - 5)
    graphics.drawLine(startX.toInt(), y, (startX + 8).toInt(), y + 5)
    graphics.drawLine(endX.toInt(), endY, (endX - 8).toInt(), endY - 5)
    graphics.drawLine(endX.toInt(), endY, (endX - 8).toInt(), endY + 5)
}

private fun drawChevron(graphics: Graphics2D, centerX: Float, y: Float) {
    val path = Path2D.Double()
    path.moveTo((centerX - 9f).toDouble(), (y - 5f).toDouble())
    path.lineTo(centerX.toDouble(), (y + 5f).toDouble())
    path.lineTo((centerX + 9f).toDouble(), (y - 5f).toDouble())
    graphics.draw(path)
}

private fun drawCenteredText(graphics: Graphics2D, text: String, centerX: Float, baseline: Int, size: Int) {
    graphics.font = Font("Dialog", Font.PLAIN, size)
    val width = graphics.fontMetrics.stringWidth(text)
    graphics.color = Color(0x4A4A4A)
    graphics.drawString(text, (centerX - width / 2f).toInt(), baseline)
}

private fun number(value: String): Float? = Regex("-?\\d+(?:\\.\\d+)?").find(value)?.value?.toFloatOrNull()
