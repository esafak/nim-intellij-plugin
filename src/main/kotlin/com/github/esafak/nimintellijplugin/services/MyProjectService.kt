package com.github.esafak.nimintellijplugin.services

import com.intellij.openapi.project.Project
import com.github.esafak.nimintellijplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }

    fun getRandomNumber(): Int = (1..100).random()
}