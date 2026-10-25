package io.github.ranxian.muguang.utils

import java.security.MessageDigest

object MD5Utils {
    fun encryptPassword(password: String): String {
        val md5 = md5(password)
        return if (md5.length >= 10) {
            md5.substring(md5.length - 10).uppercase()
        } else {
            md5.uppercase()
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
