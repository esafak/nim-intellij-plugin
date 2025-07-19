package com.github.esafak.nimintellijplugin

import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Suppress("UnstableApiUsage")
class NimLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (file.extension == "nim") {
            serverStarter.ensureServerStarted(NimLspServerDescriptor(project))
        }
    }
}
