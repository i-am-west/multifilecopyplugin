package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.StructureExtractionUtil

class CopySelectedFilesAction : AnAction() {
    init {
        templatePresentation.text = "Copy Multiple Files to Clipboard"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        val content = StructureExtractionUtil.extractStructure(
            selectedFiles,
            project,
            StructureExtractionUtil.DetailLevel.FULL_CONTENT,
            selectedFiles.toSet()
        )
        FileContentUtils.copyToClipboard(content)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}