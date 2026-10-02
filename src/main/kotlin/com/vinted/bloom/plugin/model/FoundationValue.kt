package com.vinted.bloom.plugin.model

import java.awt.Color
import java.nio.file.Path

enum class FoundationKind(
    val displayName: String,
) {
    COLOR("Color"),
    DIMENSION("Dimension"),
    SHADOW("Shadow"),
    MEDIA_SIZE("Media size"),
    OPACITY("Opacity"),
    BORDER_RADIUS("Border radius"),
    BORDER_WIDTH("Border width"),
    SPACER_SIZE("Spacer size"),
    ALIGNMENT("Alignment"),
    BORDER_THEME("Border theme"),
    TEXT_HIGHLIGHT("Text highlight"),
    TYPOGRAPHY("Typography"),
    DRAWABLE("Drawable"),
    ICON("Icon"),
}

sealed interface FoundationPreview {
    data class ColorSwatch(val color: Color) : FoundationPreview
    data class ColorWithOpacity(val color: Color, val opacity: Float) : FoundationPreview
    data class Opacity(val value: Float) : FoundationPreview
    data class Shadow(val dp: Float) : FoundationPreview
    data class Alignment(val bias: Float) : FoundationPreview
    data class SpacerSize(val dp: Float) : FoundationPreview
    data class BorderRadius(val dp: Float) : FoundationPreview
    data class BorderWidth(val dp: Float) : FoundationPreview
    data class TextHighlight(
        val textColor: Color,
        val backgroundColor: Color,
        val backgroundOpacity: Float,
    ) : FoundationPreview
    data class Typography(
        val fontPath: Path?,
        val textColor: Color?,
        val fontSize: Float? = null,
    ) : FoundationPreview
    data class DrawableFile(
        val path: Path?,
        val content: String? = null,
        val contentBytes: ByteArray? = null,
    ) : FoundationPreview
    data object Metric : FoundationPreview
}

data class FoundationValue(
    val expression: String,
    val kind: FoundationKind,
    val label: String,
    val resolvedValue: String,
    val sourcePath: Path? = null,
    val preview: FoundationPreview = FoundationPreview.Metric,
) {
    val tooltip: String
        get() = buildString {
            append(kind.displayName)
            append(" · ")
            append(label)
            append(" = ")
            append(resolvedValue)
            if (sourcePath != null) {
                append("\n")
                append(sourcePath.fileName)
            }
        }
}
