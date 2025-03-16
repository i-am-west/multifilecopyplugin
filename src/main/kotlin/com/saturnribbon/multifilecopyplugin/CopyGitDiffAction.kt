package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class CopyGitDiffAction : AnAction("Copy Git Diff to Clipboard", "Copies the git diff between the current branch and master to the clipboard", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        try {
            val process = ProcessBuilder("git", "diff", "master")
                .directory(File(basePath))
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val diffText = reader.readText()
            process.waitFor()

            if (diffText.isBlank()) {
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff", "No differences found.", NotificationType.INFORMATION),
                    project
                )
            } else {
                FileContentUtils.copyToClipboard(diffText)
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff", "Git diff copied to clipboard.", NotificationType.INFORMATION),
                    project
                )
            }
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("GitDiff", "Copy Git Diff", "Error occurred: ${ex.message}", NotificationType.ERROR),
                project
            )
        }
    }
}
