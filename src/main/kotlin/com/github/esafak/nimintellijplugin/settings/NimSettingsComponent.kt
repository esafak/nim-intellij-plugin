package com.github.esafak.nimintellijplugin.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.io.File
import javax.swing.JPanel

class NimSettingsComponent {
    val panel: JPanel
    private val nimlangserverPathField = JBTextField()
    private val versionLabel = JBLabel()

    init {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Nim langserver path:"), nimlangserverPathField, 1, false)
            .addComponent(versionLabel)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        nimlangserverPathField.addActionListener { updateVersionLabel() }
    }

    private fun updateVersionLabel() {
        val path = nimlangserverPathField.text
        if (path.isNullOrEmpty()) {
            versionLabel.text = ""
            return
        }

        val file = File(path)
        if (!file.exists() || !file.canExecute()) {
            versionLabel.text = "Invalid path"
            return
        }

        try {
            val process = ProcessBuilder(path, "--version").start()
            val version = process.inputStream.bufferedReader().readText()
            versionLabel.text = "Version: $version"
        } catch (e: Exception) {
            versionLabel.text = "Error: ${e.message}"
        }
    }

    var nimlangserverPath: String?
        get() = nimlangserverPathField.text
        set(value) {
            nimlangserverPathField.text = value
            updateVersionLabel()
        }
}
