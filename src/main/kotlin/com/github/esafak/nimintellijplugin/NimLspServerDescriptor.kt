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
import java.util.concurrent.TimeUnit

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
        val pathFromShell = getPathFromShell()
        val commandLine = GeneralCommandLine(path)
        commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        if (!areCommandsInPath(setOf("nimble", "nimsuggest"), pathFromShell)) {
             issueError("`nimble` or `nimsuggest` not found in PATH. Cannot start Nim langserver.")
        }
        commandLine.withEnvironment("PATH", pathFromShell!!)
        return commandLine
    }

    private fun areCommandsInPath(commands: Set<String>, path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val commandsLeft = commands.toMutableSet()
        path.split(File.pathSeparator).forEach { dir ->
            val iter = commandsLeft.iterator()
            while (iter.hasNext()) {
                val command = iter.next()
                val file = File(dir, command)
                if (file.exists() && file.canExecute())
                    iter.remove()
            }
            if (commandsLeft.isEmpty()) return true
        }
        return false
    }

    private fun getPathFromShell(): String? {
        val osName = System.getProperty("os.name")
        val shell: String
        val args: List<String>

        // TODO: Test the Windows case
        if (osName.startsWith("Windows")) {
            shell = "cmd.exe"
            args = listOf("/c", "echo %PATH%")
        } else {
            shell = "sh"
            args = listOf("-cl", "echo \$PATH")
        }

        return try {
            return ProcessBuilder(shell, *args.toTypedArray()).start().also {
                it.waitFor(1, TimeUnit.SECONDS)
            }.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            issueError("Failed to get PATH from shell: ${e.message}")
            null
        }
    }

    private fun issueError(message: String) {
        Notifications.Bus.notify(
            Notification(
                "NimLangServer",
                "PATH issue",
                message,
                NotificationType.ERROR
            )
        )
    }
}