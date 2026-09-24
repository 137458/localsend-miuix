package org.localsend.miuix.core

import kotlinx.serialization.json.Json

/**
 * Shared kotlinx.serialization config for LocalSend protocol DTOs and local persistence.
 * ignoreUnknownKeys keeps us compatible with official LocalSend fields we do not model yet.
 */
object AppJson {
    val default: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
}
