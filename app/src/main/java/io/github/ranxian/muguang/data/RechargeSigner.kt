package io.github.ranxian.muguang.data

import java.math.BigDecimal
import java.security.MessageDigest

/**
 * 充值下单签名（官方 `ek.e0.e`）。
 *
 * ```
 * signature = MD5( 按 key 升序拼好的 "key+value" + "sign-kailu=" )
 * ```
 *
 * ## 这个 key 是怎么确定的
 *
 * 官方把 `getSk()` 放在 native 库 `libklcxkjencry.so` 里。反编译之后它其实只有一个
 * 尾巴调用 —— `NewStringUTF` 返回两段 .rodata 之一：
 *
 * - `0xa2133` = `keep&1!`
 * - `0xa213b` = `sign-kailu=`
 *
 * 光看静态看不出该取哪一段（选择位在 .bss 里，初值为 0），所以对着线上一份
 * **只读**接口（`settlement/qrcode/info`，同一个签名函数）各打一发验证：
 * `keep&1!` 被服务端以 `170 签名错误` 拒掉，`sign-kailu=` 通过并走了业务逻辑
 * （`180 二维码信息不存在`）。因此取后者。
 *
 * ## 只有四个字段进签名
 *
 * 官方先把 `loginCode/payWay/rechargeMoney/telPhone` 拼起来算签名，**再**由基类
 * `t0()` 往同一个 map 里补 `projectId/accountId/userId/telephone/phoneSystem/version`。
 * 也就是说这些认证字段随请求体发出去，但**不参与**签名。
 * 顺序错了服务端只回 170，所以这里刻意分开两件事：`signInput` 与请求体分开构造。
 */
object RechargeSigner {

    /** 官方 native 里的 sk 常量（含末尾等号，不是笔误） */
    const val SK = "sign-kailu="

    /**
     * 计算签名。
     *
     * 与官方 `ek.e0.e` 完全一致：key 升序、只拼 `key+value`、null 值当空串、
     * 末尾追加 [SK]，最后取 32 位小写 hex。
     */
    fun sign(params: Map<String, String?>): String {
        val raw = StringBuilder()
        for (key in params.keys.sorted()) {
            raw.append(key)
            params[key]?.let { raw.append(it) }
        }
        raw.append(SK)
        return md5Hex(raw.toString().trim())
    }

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * 充值金额的规范化与校验。
 *
 * 金额只来自服务端下发的充值套餐，正常不会非法；这里仍然全量校验一遍，
 * 因为**一旦把 "0" 或 "abc" 发出去，服务端会把整段校验顺序走歪**
 * （实测 `abc` 直接回 `-2 网络出错了`，用户看到的是一句毫无信息量的报错）。
 */
object RechargeAmounts {

    /** 单笔上限（元）。只是拦手滑多打几个 0，真正的风控在服务端。 */
    private val MAX = BigDecimal("10000")

    /** 最小 0.01 元：0 元订单在服务端被判「无效金额」 */
    private val MIN = BigDecimal("0.01")

    /**
     * 规范化金额：去掉空白、去掉多余的 0，最多两位小数。
     *
     * `"10.0"` → `"10"`，`"010.50"` → `"10.5"`，非法输入返回 null。
     * 返回值就是 `rechargeMoney` 要发的字符串，**同时也是签名输入的一部分**，
     * 所以必须是稳定形态，不能依赖用户的输入原文。
     */
    fun normalize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // 只认真实小数写法：拒绝 1e3 / 0x10 / +5 / " 1 0 "
        if (!Regex("^\\d{1,7}(\\.\\d{1,2})?$").matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value < MIN || value > MAX) return null
        val stripped = value.stripTrailingZeros()
        // BigDecimal 的 stripTrailingZeros 会把 10 变成 1E+1，toPlainString 才是给人看的
        return stripped.toPlainString()
    }

    fun toBigDecimal(raw: String?): BigDecimal? = normalize(raw)?.toBigDecimalOrNull()
}

/** 支付通道：只列出本项目能够真正走完的通道 */
enum class RechargeChannel(val payWay: String, val label: String) {
    /** 支付宝 App / 支付宝 H5，走 PayTask */
    ALIPAY("2", "支付宝"),

    /** 聚合收银台，服务端直接给一个 H5 地址 */
    JUHE("26", "聚合收银台"),

    /** 校园卡充值页，服务端给一个 H5 地址 */
    CAMPUS("36", "校园卡"),
    ;

    companion object {
        /** 服务端 `payWay` 列表 → 本项目能落地的通道，保持枚举声明顺序 */
        fun fromPayWays(payWays: List<String>?): List<RechargeChannel> {
            if (payWays.isNullOrEmpty()) return emptyList()
            val enabled = payWays.map { it.trim() }.toSet()
            return entries.filter { it.payWay in enabled }
        }

        fun ofPayWay(payWay: String): RechargeChannel? =
            entries.firstOrNull { it.payWay == payWay }
    }
}

/**
 * 下单结果。
 *
 * 只有 [Alipay] 需要把 orderInfo 交给支付宝 SDK；另外两种通道直接开浏览器。
 * 服务端 `errorCode != 0` 时 [message] 就是服务端原话，界面直接展示——
 * 这类文案（余额不足 / 未签约 / 渠道关闭）只有服务端说得准。
 */
sealed interface RechargeOrder {
    data class Alipay(val orderInfo: String) : RechargeOrder
    data class Browser(val url: String) : RechargeOrder
}

/** 支付宝 SDK 回传的 resultStatus，见支付宝文档 */
enum class AlipayResult(val code: String, val label: String) {
    SUCCESS("9000", "支付成功"),
    PROCESSING("8000", "支付处理中"),
    CANCELLED("6001", "已取消"),
    NETWORK_ERROR("6002", "网络异常"),
    FAILED("4000", "支付失败"),
    REPEAT("5000", "重复请求"),
    UNKNOWN("", "支付结果未知");

    companion object {
        fun of(code: String?): AlipayResult =
            entries.firstOrNull { it.code == code?.trim() } ?: UNKNOWN
    }
}
