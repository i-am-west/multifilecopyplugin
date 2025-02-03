package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.FileProcessingUtil

class CopyOpenTabsAction : AnAction("Copy Open Tabs to Clipboard") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val openFiles = FileEditorManager.getInstance(project).openFiles

        val content = FileProcessingUtil.processFiles(openFiles, project, openFiles.toSet())
        FileContentUtils.copyToClipboard(content)
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if we have a project and there are open files
        val project = e.project
        e.presentation.isEnabled = project != null &&
            FileEditorManager.getInstance(project).openFiles.isNotEmpty()
    }
}
