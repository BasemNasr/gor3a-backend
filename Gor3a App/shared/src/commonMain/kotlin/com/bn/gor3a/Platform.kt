package com.bn.gor3a

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform