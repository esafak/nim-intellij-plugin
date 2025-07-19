package com.github.esafak.nimintellijplugin

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NimIcons {
    private fun load(path: String): Icon = IconLoader.getIcon(path, NimIcons::class.java)

    val CONSTANT = load("/icons/constant.svg")
    val CONVERTER = load("/icons/converter.svg")
    val ENUM = load("/icons/enum.svg")
    val ENUM_MEMBER = load("/icons/enum_member.svg")
    val FIELD = load("/icons/field.svg")
    val ITERATOR = load("/icons/iterator.svg")
    val MACRO = load("/icons/macro.svg")
    val METHOD = load("/icons/method.svg")
    val MODULE = load("/icons/module.svg")
    val PARAMETER = load("/icons/parameter.svg")
    val PROCEDURE = load("/icons/proc.svg")
    val TEMPLATE = load("/icons/template.svg")
    val TYPE = load("/icons/type.svg")
    val VARIABLE = load("/icons/variable.svg")
}