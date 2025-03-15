package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.StructureExtractionUtil

class CopyOpenTabsAction : AnAction() {
    init {
        templatePresentation.text = "Copy Open Tabs to Clipboard"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val openFiles = FileEditorManager.getInstance(project).openFiles

        val content = StructureExtractionUtil.extractStructure(
            openFiles,
            project,
            StructureExtractionUtil.DetailLevel.FULL_CONTENT,
            openFiles.toSet()
        )
        FileContentUtils.copyToClipboard(content)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            FileEditorManager.getInstance(project).openFiles.isNotEmpty()
    }
}
