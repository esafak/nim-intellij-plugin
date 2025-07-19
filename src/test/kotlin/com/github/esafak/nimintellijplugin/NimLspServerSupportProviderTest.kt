package com.github.esafak.nimintellijplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NimLspServerSupportProviderTest : BasePlatformTestCase() {

    fun testServerIsStartedForNimFile() {
        myFixture.configureByText("main.nim", "echo \"hello world\"")
        // How to check if the server is started?
    }
}
