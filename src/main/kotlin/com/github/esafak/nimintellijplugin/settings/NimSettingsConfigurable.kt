package com.github.esafak.nimintellijplugin.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class NimSettingsConfigurable : Configurable {
    private var mySettingsComponent: NimSettingsComponent? = null

    override fun getDisplayName(): String {
        return "Nim"
    }

    override fun createComponent(): JComponent? {
        mySettingsComponent = NimSettingsComponent()
        return mySettingsComponent?.panel
    }

    override fun isModified(): Boolean {
        val settings = NimSettingsState.instance
        return mySettingsComponent?.nimlangserverPath != settings.nimlangserverPath
    }

    override fun apply() {
        val settings = NimSettingsState.instance
        settings.nimlangserverPath = mySettingsComponent?.nimlangserverPath ?: ""
    }

    override fun reset() {
        val settings = NimSettingsState.instance
        mySettingsComponent?.nimlangserverPath = settings.nimlangserverPath
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}
