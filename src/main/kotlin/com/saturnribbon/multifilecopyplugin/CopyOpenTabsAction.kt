package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.StructureExtractionUtil

class CopyOpenTabsAction : AnAction() {
    init {
        templatePresentation.text = "Copy Tab Group to Clipboard"
        templatePresentation.description = "Copy all files from the current tab group to clipboard"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return

        // Get the window/splitter containing the current file
        val currentWindow = editorManager.getWindow(currentFile) ?: return

        // Get all files in the current window
        val filesInGroup = editorManager.getOpenFiles(currentWindow)

        val content = StructureExtractionUtil.extractStructure(
            filesInGroup,
            project,
            StructureExtractionUtil.DetailLevel.FULL_CONTENT,
            filesInGroup.toSet()
        )
        FileContentUtils.copyToClipboard(content)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val currentFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        e.presentation.isEnabled = project != null && 
            currentFile != null &&
            FileEditorManager.getInstance(project).isFileOpen(currentFile)
    }

    private fun FileEditorManagerImpl.getWindow(file: VirtualFile): Int? {
        val windows = this.windows
        return windows.indices.find { windowIndex ->
            this.getOpenFiles(windowIndex).contains(file)
        }
    }

    private fun FileEditorManagerImpl.getOpenFiles(windowIndex: Int): Array<VirtualFile> {
        return this.windows.getOrNull(windowIndex)?.files ?: emptyArray()
    }
}
