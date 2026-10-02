package com.vinted.bloom.plugin.action

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.vinted.bloom.plugin.catalog.FoundationValueCatalog

class RefreshBloomFoundationCatalogAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.service<FoundationValueCatalog>().invalidate()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
