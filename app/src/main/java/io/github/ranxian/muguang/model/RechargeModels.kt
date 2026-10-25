package io.github.ranxian.muguang.model

/**
 * 充值通道配置（`GET settlement/recharge/pay/mode`）。
 *
 * `payWay` 是通道编号的字符串列表，官方用 `2=支付宝 / 23=招行 / 24=一卡通 /
 * 26=聚合 / 33=翼支付 / 36=校园卡 / 6=云闪付`。每个学校开通的子集不同，
 * 界面只能按这里下发的来，不能写死。
 */
data class RechargePayModes(
    val projectId: Long = 0,
    val noSaveMessage: String? = null,
    val payWay: List<String>? = null,
)

/**
 * 充值套餐（`GET settlement/recharge/package/list`）。
 *
 * 官方页面**没有自定义金额输入框**，金额完全由这个列表决定，
 * 所以本项目的充值页也照此设计：用户只能选套餐，不能自己填数。
 *
 * `amount` 在服务端是字符串，但实测返回的是 JSON 数字（`10.0`），
 * Gson 对 String 字段读数字 token 是兼容的；万一某个学校返回 null，
 * 界面按「该套餐不可用」跳过，不会拿 null 去下单。
 */
data class RechargePackage(
    val typeId: Long = 0,
    val projectId: Long = 0,
    val amount: String? = null,
    val givenMoney: Double = 0.0,
    val activityId: Long? = null,
)
