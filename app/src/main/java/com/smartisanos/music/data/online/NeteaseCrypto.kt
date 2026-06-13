package com.smartisanos.music.data.online

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object NeteaseCrypto {
    private const val EApiKey = "e82ckenh8dichen8"
    private const val WeApiPresetKey = "0CoJUm6Qyw8W8jud"
    private const val WeApiIv = "0102030405060708"
    private const val WeApiBase62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val WeApiPublicKeyPem =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/" +
            "aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/" +
            "DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"

    private val random = SecureRandom()

    fun encryptEApiParams(path: String, payloadJson: String): String {
        val digest = "nobody${path}use${payloadJson}md5forencrypt".md5Hex()
        val message = "$path-36cd479b6b5-$payloadJson-36cd479b6b5-$digest"
        return message.aesEcbHex(EApiKey)
    }

    fun encryptWeApiParams(payloadJson: String): Map<String, String> {
        val secretKey = randomWeApiKey()
        val firstPass = payloadJson.aesCbcBase64(WeApiPresetKey, WeApiIv)
        return mapOf(
            "params" to firstPass.aesCbcBase64(secretKey, WeApiIv),
            "encSecKey" to rsaEncryptWeApiKey(secretKey.reversed()),
        )
    }

    private fun randomWeApiKey(): String {
        return buildString {
            repeat(16) {
                append(WeApiBase62[random.nextInt(WeApiBase62.length)])
            }
        }
    }

    private fun rsaEncryptWeApiKey(text: String): String {
        val publicKeyBytes = Base64.getDecoder().decode(WeApiPublicKeyPem)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes)) as RSAPublicKey
        val message = BigInteger(1, text.toByteArray(StandardCharsets.UTF_8))
        val encrypted = message.modPow(publicKey.publicExponent, publicKey.modulus)
        val keySize = (publicKey.modulus.bitLength() + 7) / 8
        val rawBytes = encrypted.toByteArray()
        val normalizedBytes = when {
            rawBytes.size == keySize -> rawBytes
            rawBytes.size > keySize -> rawBytes.copyOfRange(rawBytes.size - keySize, rawBytes.size)
            else -> ByteArray(keySize - rawBytes.size) + rawBytes
        }
        return normalizedBytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun String.md5Hex(): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun String.aesCbcBase64(key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return Base64.getEncoder().encodeToString(cipher.doFinal(toByteArray(StandardCharsets.UTF_8)))
    }

    private fun String.aesEcbHex(key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
            .uppercase(Locale.US)
    }
}
