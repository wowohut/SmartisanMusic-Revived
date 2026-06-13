@file:Suppress("DEPRECATION")

package com.smartisanos.music.data.online

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

private const val NeteaseAuthPrefsName = "netease_auth"
private const val NeteaseAuthCookieJsonKey = "cookie_json"
private const val NeteaseAuthSavedAtKey = "saved_at"
private const val NeteaseAuthProfileJsonKey = "profile_json"
private const val NeteaseLoginCookieKey = "MUSIC_U"

internal data class NeteaseAuthState(
    val cookies: Map<String, String>,
    val savedAt: Long,
    val profile: NeteaseAccountProfile?,
) {
    val isLoggedIn: Boolean
        get() = !cookies[NeteaseLoginCookieKey].isNullOrBlank()
}

internal data class NeteaseCookieValidationResult(
    val cookies: Map<String, String>,
    val rejectedKeys: List<String>,
) {
    val isAccepted: Boolean
        get() = cookies.isNotEmpty() && !cookies[NeteaseLoginCookieKey].isNullOrBlank()
}

internal class NeteaseAuthStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = openPreferences(appContext)

    fun load(): NeteaseAuthState {
        val cookieJson = prefs.getString(NeteaseAuthCookieJsonKey, null).orEmpty()
        val cookies = runCatching {
            parseCookieJson(cookieJson)
        }.getOrDefault(emptyMap())
        return NeteaseAuthState(
            cookies = cookies,
            savedAt = prefs.getLong(NeteaseAuthSavedAtKey, 0L),
            profile = prefs.getString(NeteaseAuthProfileJsonKey, null)
                ?.let { profileJson -> runCatching { parseNeteaseAccountProfileJson(profileJson) }.getOrNull() },
        )
    }

    fun getCookies(): Map<String, String> {
        return load().cookies
    }

    fun validateCookies(cookies: Map<String, String>): NeteaseCookieValidationResult {
        return validateNeteaseCookies(cookies)
    }

    fun saveCookies(cookies: Map<String, String>, savedAt: Long = System.currentTimeMillis()): Boolean {
        val validation = validateCookies(cookies)
        if (!validation.isAccepted) {
            return false
        }
        prefs.edit()
            .putString(NeteaseAuthCookieJsonKey, validation.cookies.toCookieJson())
            .putLong(NeteaseAuthSavedAtKey, savedAt)
            .remove(NeteaseAuthProfileJsonKey)
            .apply()
        return true
    }

    fun saveCookieJson(cookieJson: String, savedAt: Long = System.currentTimeMillis()): Boolean {
        return saveCookies(parseCookieJson(cookieJson), savedAt)
    }

    fun saveProfile(profile: NeteaseAccountProfile) {
        prefs.edit()
            .putString(NeteaseAuthProfileJsonKey, profile.toProfileJson())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(NeteaseAuthCookieJsonKey)
            .remove(NeteaseAuthSavedAtKey)
            .remove(NeteaseAuthProfileJsonKey)
            .apply()
    }
}

private fun openPreferences(context: Context): SharedPreferences {
    return runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            NeteaseAuthPrefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences(NeteaseAuthPrefsName, Context.MODE_PRIVATE)
    }
}

internal fun parseNeteaseCookieHeader(rawCookieHeader: String): Map<String, String> {
    if (rawCookieHeader.isBlank()) {
        return emptyMap()
    }
    val cookies = linkedMapOf<String, String>()
    rawCookieHeader
        .split(';')
        .map(String::trim)
        .filter { part -> part.isNotBlank() && '=' in part }
        .forEach { part ->
            val separatorIndex = part.indexOf('=')
            val key = part.substring(0, separatorIndex).trim()
            val value = part.substring(separatorIndex + 1).trim()
            if (key.isNotEmpty()) {
                cookies[key] = value
            }
        }
    return cookies
}

internal fun validateNeteaseCookies(cookies: Map<String, String>): NeteaseCookieValidationResult {
    val sanitized = linkedMapOf<String, String>()
    val rejected = linkedSetOf<String>()
    cookies.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        val rejectedKey = key.ifBlank { "<blank>" }
        when {
            key.isBlank() -> rejected += rejectedKey
            !NeteaseCookieNameRegex.matches(key) -> rejected += rejectedKey
            value.isBlank() -> rejected += rejectedKey
            value.any(Char::isISOControl) -> rejected += rejectedKey
            ';' in value -> rejected += rejectedKey
            else -> sanitized[key] = value
        }
    }
    if (sanitized.isNotEmpty()) {
        sanitized.putIfAbsent("os", "pc")
        sanitized.putIfAbsent("appver", "8.10.35")
    }
    return NeteaseCookieValidationResult(
        cookies = sanitized,
        rejectedKeys = rejected.toList(),
    )
}

internal fun parseCookieJson(cookieJson: String): Map<String, String> {
    if (cookieJson.isBlank()) {
        return emptyMap()
    }
    val root = JSONObject(cookieJson)
    val cookies = linkedMapOf<String, String>()
    val keys = root.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = root.optString(key, "").takeIf(String::isNotBlank)
        if (value != null) {
            cookies[key] = value
        }
    }
    return cookies
}

private fun Map<String, String>.toCookieJson(): String {
    return JSONObject().also { root ->
        forEach { (key, value) -> root.put(key, value) }
    }.toString()
}

private fun NeteaseAccountProfile.toProfileJson(): String {
    return JSONObject()
        .put("userId", userId)
        .put("nickname", nickname)
        .apply {
            avatarUrl?.takeIf(String::isNotBlank)?.let { url -> put("avatarUrl", url) }
        }
        .toString()
}

private val NeteaseCookieNameRegex = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
