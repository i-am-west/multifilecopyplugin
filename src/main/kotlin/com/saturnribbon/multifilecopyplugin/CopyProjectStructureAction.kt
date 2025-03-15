package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.StructureExtractionUtil

class CopyProjectStructureAction : AnAction() {
    init {
        templatePresentation.text = "Copy Project Structure to Clipboard"
        templatePresentation.description = "Copies the structure of the project (classes, methods, fields) without implementation details"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = FileContentUtils.getSelectedFiles(e)
        if (selectedFiles.isEmpty()) return

        val content = StructureExtractionUtil.extractStructure(
            selectedFiles,
            project,
            StructureExtractionUtil.DetailLevel.PROJECT_STRUCTURE,
            selectedFiles.toSet()
        )
        FileContentUtils.copyToClipboard(content)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
} 