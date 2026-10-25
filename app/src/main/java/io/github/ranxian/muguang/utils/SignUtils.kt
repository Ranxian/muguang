package io.github.ranxian.muguang.utils

import java.security.MessageDigest

/**
 * 趣智校园短信验证码签名工具。
 *
 * 官方 App 发验证码时的 secret 并非随机值，而是按手机号计算得出：
 *   raw    = 手机号前 3 位 + 后 4 位 + "klcx"
 *   secret = MD5(raw)   （32 位小写十六进制）
 *
 * 例：13800138000 → "138" + "8000" + "klcx" = "1388000klcx"
 *     → MD5 → f000554d9cb90c44bd0ae2ded9a847aa
 *
 * 算法逆向自官方 APK，因此任何手机号都能在本地算出自己的 secret，
 * 无需抓包，短信验证码登录对所有人可用。
 */
object SignUtils {
    /** 根据手机号计算短信验证码接口所需的 secret */
    fun smsSecret(telephone: String): String {
        require(telephone.length == 11) { "请输入 11 位手机号" }
        val raw = telephone.take(3) + telephone.takeLast(4) + "klcx"
        return md5(raw)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
