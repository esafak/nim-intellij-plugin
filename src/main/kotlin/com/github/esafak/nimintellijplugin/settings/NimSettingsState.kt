package com.github.esafak.nimintellijplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.esafak.nimintellijplugin.settings.NimSettingsState",
    storages = [Storage("NimSettings.xml")]
)
class NimSettingsState : PersistentStateComponent<NimSettingsState> {
    var nimlangserverPath: String = ""

    override fun getState(): NimSettingsState {
        return this
    }

    override fun loadState(state: NimSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: NimSettingsState
            get() = ApplicationManager.getApplication().getService(NimSettingsState::class.java)
    }
}
