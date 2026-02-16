package com.lostinspacebar.hinode

class Greeting {
    private val platform = getPlatform()

    fun greet(): String {
        return "Hey, ${platform.name}!"
    }
}
