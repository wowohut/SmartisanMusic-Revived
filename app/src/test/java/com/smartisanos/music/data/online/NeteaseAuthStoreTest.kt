package com.smartisanos.music.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseAuthStoreTest {

    @Test
    fun cookieHeaderParserKeepsLoginCookieAndCsrfToken() {
        val cookies = parseNeteaseCookieHeader(
            " MUSIC_U=login-token ; __csrf=csrf-token ; ignored ; session=value=with=equals ",
        )

        assertEquals("login-token", cookies["MUSIC_U"])
        assertEquals("csrf-token", cookies["__csrf"])
        assertEquals("value=with=equals", cookies["session"])
    }

    @Test
    fun cookieValidationRequiresMusicUForLoginState() {
        val validation = validateNeteaseCookies(
            mapOf(
                "__csrf" to "csrf-token",
                "NMTID" to "device-token",
            ),
        )

        assertFalse(validation.isAccepted)
        assertEquals("pc", validation.cookies["os"])
        assertEquals("8.10.35", validation.cookies["appver"])
    }

    @Test
    fun cookieValidationAcceptsMusicUAndAddsClientHints() {
        val validation = validateNeteaseCookies(
            mapOf(
                "MUSIC_U" to "login-token",
                "__csrf" to "csrf-token",
            ),
        )

        assertTrue(validation.isAccepted)
        assertEquals("login-token", validation.cookies["MUSIC_U"])
        assertEquals("pc", validation.cookies["os"])
        assertEquals("8.10.35", validation.cookies["appver"])
    }

    @Test
    fun cookieValidationRejectsUnsafeCookieValues() {
        val validation = validateNeteaseCookies(
            mapOf(
                "MUSIC_U" to "login-token",
                "bad;key" to "value",
                "badValue" to "a;b",
                "blank" to " ",
            ),
        )

        assertTrue(validation.isAccepted)
        assertEquals(listOf("bad;key", "badValue", "blank"), validation.rejectedKeys)
        assertFalse(validation.cookies.containsKey("bad;key"))
        assertFalse(validation.cookies.containsKey("badValue"))
        assertFalse(validation.cookies.containsKey("blank"))
    }
}
