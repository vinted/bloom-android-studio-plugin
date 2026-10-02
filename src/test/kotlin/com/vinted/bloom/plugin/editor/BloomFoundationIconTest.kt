package com.vinted.bloom.plugin.editor

import com.vinted.bloom.plugin.model.FoundationPreview
import org.junit.Assert.assertEquals
import org.junit.Test

class BloomFoundationIconTest {
    @Test
    fun svgEvenOddPathsKeepTheirHoles() {
        val image = createDrawablePreviewImage(
            FoundationPreview.DrawableFile(
                path = null,
                content = """
                    <svg viewBox="0 0 64 64">
                        <path d="M8 8H56V56H8Z M24 24H40V40H24Z" fill="#000000" fill-rule="evenodd"/>
                    </svg>
                """.trimIndent(),
            ),
            width = 64,
        )

        assertEquals(0xFFFFFFFF.toInt(), image?.getRGB(32, 32))
        assertEquals(0xFF000000.toInt(), image?.getRGB(12, 12))
    }

    @Test
    fun androidEvenOddPathsKeepTheirHoles() {
        val image = createDrawablePreviewImage(
            FoundationPreview.DrawableFile(
                path = null,
                content = """
                    <vector android:viewportWidth="64" android:viewportHeight="64">
                        <path android:pathData="M8 8H56V56H8Z M24 24H40V40H24Z" android:fillColor="#000000" android:fillType="evenOdd"/>
                    </vector>
                """.trimIndent(),
            ),
            width = 64,
        )

        assertEquals(0xFFFFFFFF.toInt(), image?.getRGB(32, 32))
        assertEquals(0xFF000000.toInt(), image?.getRGB(12, 12))
    }
}
