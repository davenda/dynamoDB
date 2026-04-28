package com.yourplugin.dynamodb

object BuildConfig {
    val VERSION: String by lazy {
        runCatching {
            val props = java.util.Properties()
            BuildConfig::class.java.getResourceAsStream("/version.properties")
                ?.use { props.load(it) }
            props.getProperty("version", "1.0.0")
        }.getOrElse { "1.0.0" }
    }
}
