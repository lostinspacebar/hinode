package com.lostinspacebar.hinode

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform