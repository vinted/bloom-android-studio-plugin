package com.vinted.bloom.plugin.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeHighlighting.Pass
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.vinted.bloom.plugin.catalog.FoundationValueCatalog

class BloomFoundationLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.textLength == 0 || element.firstChild != null) return null
        val project = element.project
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return null
        val line = document.getLineNumber(element.textRange.startOffset)
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val lineText = document.getText(TextRange(lineStart, lineEnd))
        val catalog = project.service<FoundationValueCatalog>()
        val match = ExpressionPatterns.find(lineText) { catalog.containsAlias(it) } ?: return null
        val matchOffset = lineStart + match.start
        if (element.textRange.startOffset != matchOffset) return null

        val value = catalog.resolve(match.expression) ?: return null
        return LineMarkerInfo(
            element,
            TextRange(element.textRange.startOffset, element.textRange.endOffset),
            BloomFoundationIconFactory.create(value.preview),
            Pass.LINE_MARKERS,
            { BloomFoundationTooltip.forValue(value) },
            null,
            GutterIconRenderer.Alignment.CENTER,
        )
    }
}

private object ExpressionPatterns {
    private val patterns = listOf(
        Regex("BloomTheme\\.\\w+\\.\\w+(?:\\.\\w+)*"),
        Regex("(?:[A-Za-z_]\\w*\\.)?R\\.(?:color|dimen|drawable)\\.\\w+"),
        Regex("@(?:android:)?(?:color|dimen|drawable)/\\w+"),
        Regex("BloomIcon\\.\\w+(?:\\.id)?"),
        Regex("BloomIllustration\\.\\w+(?:\\.id)?"),
    )

    fun find(line: String, isAlias: (String) -> Boolean): Match? {
        val explicit = patterns.asSequence()
            .flatMap { it.findAll(line).asSequence() }
            .map { Match(it.range.first, it.value) }
            .minByOrNull { it.start }
        if (explicit != null) return explicit

        return Regex("\\b[A-Za-z_]\\w*\\b").findAll(line)
            .map { Match(it.range.first, it.value) }
            .firstOrNull { isAlias(it.expression) }
    }

    data class Match(val start: Int, val expression: String)
}
