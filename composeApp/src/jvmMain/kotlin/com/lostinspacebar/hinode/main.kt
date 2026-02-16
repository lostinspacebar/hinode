package com.lostinspacebar.hinode

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Hinode ALPHA v0.0.1",
        alwaysOnTop = true
    ) {
        App()
    }
}
