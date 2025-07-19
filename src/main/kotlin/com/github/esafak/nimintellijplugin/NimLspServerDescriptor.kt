@file:Suppress("UnstableApiUsage")

package com.github.esafak.nimintellijplugin

import com.github.esafak.nimintellijplugin.settings.NimSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.NotificationType
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor

import java.io.File

class NimLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nim") {
    
    override fun isSupportedFile(file: VirtualFile) = file.extension == "nim"

    override fun createCommandLine(): GeneralCommandLine {
        val path = NimSettingsState.instance.nimlangserverPath
        if (path.isEmpty() || !File(path).exists()) {
            Notifications.Bus.notify(
                Notification(
                    "NimLangServer",
                    "Nim langserver not found",
                    "Please configure the path to the nimlangserver executable in the settings.",
                    NotificationType.ERROR
                )
            )
            return GeneralCommandLine()
        }
        return GeneralCommandLine(path)
    }
}
