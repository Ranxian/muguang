package io.github.ranxian.muguang.model

import com.google.gson.annotations.SerializedName

data class BaseResponse<T>(
    val success: Boolean,
    val data: T?,
    @SerializedName("errorCode") val errorCode: Int = 0,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    val msg: String? = null
) {
    val displayMessage: String?
        get() = errorMessage ?: msg
}

data class LoginData(
    val userId: Long,
    val loginCode: String,
    val userAccount: UserAccount
)

data class UserAccount(
    val accountId: Long,
    val name: String,
    val projectId: Long,
    val accountRealMoney: Double
)

data class WalletData(
    /**
     * 官方 `MyWalletResponse.accountMoney` 是主键、`accountRealMoney` 是别名；
     * 实测本校下发的是别名侧，但换学校/换版本后主键侧也可能出现，两边都接。
     */
    @SerializedName(value = "accountRealMoney", alternate = ["accountMoney"])
    val accountRealMoney: Double,
    val accountGivenMoney: Double,
    val money: String
)

/**
 * 账号信息（`GET /account/info`）。
 *
 * 拿**姓名和学号**最干净的接口——比 `/account/card/getBindCardInfo`
 * 少四十多个用不上的字段。
 *
 * ⚠️ 字段一律可空：学校没填的项（年级 / 班级 / 院系）服务端返回 null，
 * 而 Gson 反序列化绕过 Kotlin 非空约定，声明成非空会在取用时 NPE。
 */
data class AccountInfo(
    /** 姓名。学校没同步时可能是 null —— 这就是「姓名显示未设置」的根因 */
    val name: String? = null,
    /** 学号 */
    val idCardNumber: String? = null,
    val telephone: String? = null,
    val genderName: String? = null,
    /** 校园卡绑定状态：-1 = 未绑定 */
    val cardStatus: Int? = null,
    val cardStatusName: String? = null,
    val gradeName: String? = null,
    val className: String? = null
)

/**
 * 一卡通信息（`GET /settlement/campus/userInfo`）。
 *
 * 趣智校园把易校园的接口**代理了**，所以拿余额不需要去破易校园那套
 * HMAC-SHA256 原生签名——用趣智校园自己的 loginCode 就行。
 *
 * ⚠️ 前提是学生**签约过校园卡免密支付**：没签约时服务端不给 amount。
 */
data class CampusUserInfo(
    val studentNumber: String? = null,
    val studentName: String? = null,
    /** 一卡通余额，字符串形式（如 "16.76"） */
    val amount: String? = null,
    /** 1 = 已签约免密支付 */
    val signStatus: Int? = null
)

data class OrderStatus(
    /**
     * 官方 `SnWaterResponse.Data.timeIds` 是主键、`orderNo` 是别名。
     * 只认 `orderNo` 时，按官方主键下发的学校会拿不到订单号，
     * 表现为「明明在用，恢复/关阀却找不到订单」。
     */
    @SerializedName(value = "orderNo", alternate = ["timeIds"])
    val orderNo: String? = null,
    val state: Int? = null,
    val snCode: String? = null,
    val isOwner: Boolean = true,
    /** 同一响应里的另一个订单号字段（官方 `liquidOrderNo`），留作兜底 */
    val liquidOrderNo: String? = null,
    /** 预扣金额（分/厘口径由服务端决定，仅展示用） */
    @SerializedName(value = "perMoney", alternate = ["preDeductMoney"])
    val perMoney: Int? = null
)

data class BillItem(
    val consumeBillDTO: BillDTO
)

data class BillDTO(
    /** 账单**序号**（7 位，如 `9564403`）。⚠️ 和 `orderNo` 不是一回事，别拿来互相比 */
    val orderId: String,
    val consumeDate: String,
    val consumeMoney: String,
    val description: String,
    /**
     * 下单时返回的 `orderNo`（20 位，如 `13200000000000000002`）——**这才是关阀用的那个**。
     *
     * 以前没解析它，`settleAmount` 只能拿 `orderId` 去比 `orderNo`，永远匹配不上，
     * 于是结束通知永远显示「无消费」。字段可空：Gson 反序列化时缺字段就是 null。
     */
    val orderNo: String? = null
) {
    /** 设备名：龙川北苑 3号楼南 320房 */
    val displayDesc: String get() {
        val name = description.substringAfter(":")
        return if (name.isNotEmpty()) DeviceInfo.formatDeviceName(name) else description
    }

    /** 是否饮水机账单 */
    val isDrinkingWater: Boolean get() =
        description.contains("饮水") || description.contains("直饮") || description.contains("冷水")

    val isWasher: Boolean get() {
        val prefix = description.substringBefore(":", description).trim()
        val body = description.substringAfter(":", "").trim()

        val waterDeviceWords = listOf(
            "热水器", "热水表", "热水变", "洗手台", "直饮水",
            "饮水机", "开水机", "开水器", "冷水"
        )
        if (waterDeviceWords.any { prefix.contains(it) }) return false

        if (prefix.contains("洗衣") || prefix.contains("洗烘")) return true

        val name = body.ifEmpty { description }
        return name.startsWith("洗衣机") ||
            name.startsWith("洗烘") ||
            (name.contains("洗衣房") && name.contains("号机"))
    }

    val deviceTypeLabel: String get() {
        val name = description.substringAfter(":")
        return when {
            isDrinkingWater -> "饮水机"
            isWasher -> "洗衣机"
            name.startsWith("洗手台") || description.contains("洗手台", ignoreCase = true) -> "洗手台热水器"
            else -> "卫生间热水器"
        }
    }

    /** 设备类型 emoji：饮水机 🚰 / 洗手台 🪥 / 热水器 🚿 */
    val deviceEmoji: String get() = when {
        isDrinkingWater -> "🚰"
        isWasher -> "🧺"
        description.contains("洗手台", ignoreCase = true) -> "🪥"
        else -> "🚿"
    }
}

/**
 * `GET /account/useCode/new` 的返回。
 *
 * ⚠️ 字段一律可空——**没领过使用码时服务端真的返回 `"useCode": null`**（实测），
 * Gson 又会绕过 Kotlin 的非空约定把 null 塞进来，写成 `String = ""` 只是自欺欺人。
 */
data class UseCodeData(
    val useCode: String? = null,
    val useCodeStatus: Int? = null,
    /** 手机号后三位，跟 `useCode` 的末三位相同（官方主键 `randomCode`） */
    @SerializedName(value = "useCodeRandom", alternate = ["randomCode"])
    val useCodeRandom: String? = null,
    val useCodeStartTime: String? = null,
    /**
     * 今天还能不能「重新领取」使用码：`1` = 能，`0` = 不能。
     * 值为 0 时 [resetAvailabilityWarMark] 会给出原因（实测是「1天只能领取一次使用码」）。
     */
    val resetAvailability: Int? = null,
    val resetAvailabilityWarMark: String? = null
)

/** `GET /project/info/triple`——只取学校名，其余字段用不着 */
data class ProjectInfo(
    val projectId: Int? = null,
    val projectName: String? = null,
    val projectDescript: String? = null,
    val groupId: Int? = null
)

data class ProjectSwitchData(
    val userId: Long? = null,
    val accountId: Long? = null,
    val projectId: Int? = null,
    val projectName: String? = null,
    val loginCode: String? = null,
    val v3LoginCode: String? = null,
    val telephone: String? = null,
    val name: String? = null,
    val userAccount: ProjectSwitchAccount? = null
)

data class ProjectSwitchAccount(
    val projectId: Int? = null,
    val projectName: String? = null,
    val accountId: Long? = null,
    val userId: Long? = null,
    val name: String? = null,
    val telephone: String? = null
)

/**
 * `/order/weixinScorePay/unPay/queryBill` 返回的未支付账单。
 *
 * ⚠️ 它是**扁平结构**，不像 `/order/query/account/bill/list` 那样套一层
 * `consumeBillDTO`——两个接口看着像，模型不通用，别想着复用 [BillItem]。
 *
 * `consumeDate` 是代扣接口唯一定位一张账单的字段，所以它是最重要的一个。
 */
data class UnpaidBill(
    val orderId: String? = null,
    @SerializedName(value = "orderNo", alternate = ["timeIds"])
    val orderNo: String? = null,
    val consumeDate: String? = null,
    /**
     * 官方 `BillInfoBean` 的金额主键是 `upMoney`（别名 `consumeMoneyReal`），
     * 且**没有** `consumeMoney` 字段。只认 `consumeMoney` 会让欠费金额显示为空。
     * Gson 会把数字读进 String，无需改类型。
     */
    @SerializedName(value = "consumeMoney", alternate = ["upMoney", "consumeMoneyReal"])
    val consumeMoney: String? = null,
    val description: String? = null,
    /** 预扣金额（官方 `perMoney`/`preDeductMoney`），部分学校用它展示欠费额 */
    @SerializedName(value = "perMoney", alternate = ["preDeductMoney"])
    val perMoney: String? = null
)

/**
 * `POST /account/useCode/new/generate` 的返回：**换**出来的候选码，不是生效的码。
 *
 * 换出来的码要再调 `set` 才真正生效，3 分钟内不领取就作废。
 */
data class GenerateUseCodeResult(
    val useCode: String? = null,
    /** 本次「换一个」还剩几次。每天 20 次，从 20 开始往下扣 */
    val remainTimes: Int? = null
)

data class BillDetail(
    val orderId: String? = null,
    val consumeDate: String? = null,
    val consumeMoney: Double = 0.0,
    val description: String? = null,
    val deviceSnCode: String? = null,
    val orderNo: String? = null,
    val preDeductMoney: Double = 0.0
)

/**
 * 开阀结果查询 (/order/tcpDevice/query/downRateResult)
 *
 * 用于确认 downRate 开阀是否真正成功，同时可携带 autoDisConTime（自动关停秒数）。
 */
data class DownRateResult(
    /**
     * 订单号。官方 `DownRateMqttBean` 的主键是 `timeIds`（`orderNo` 才是别名），
     * 只认 `orderNo` 时按官方主键下发的学校拿不到订单号 → 关阀/结算匹配失败。
     */
    @SerializedName(value = "orderNo", alternate = ["timeIds"])
    val orderNo: String? = null,
    /** 自动关停时间（秒），如 600 = 10 分钟 */
    val autoDisConTime: Int? = null,
    /** 状态码，0 通常表示开阀成功 */
    val state: Int? = null,
    val result: Int? = null,
    /** 预扣金额（官方主键 `perMoney`，别名 `preDeductMoney`） */
    @SerializedName(value = "preDeductMoney", alternate = ["perMoney"])
    val preDeductMoney: Double? = null,
    val preDeductMoneySend: Double? = null,
    /** 费率 */
    val rate: Double? = null,
    /** 设备序列号（官方主键 `deviceSncode`，别名 `deviceSnCode`） */
    @SerializedName(value = "snCode", alternate = ["deviceSncode", "deviceSnCode"])
    val snCode: String? = null,
    /** 下单时间（官方主键 `consumeDT`，别名 `consumeDate`） */
    @SerializedName(value = "consumeDate", alternate = ["consumeDT"])
    val consumeDate: String? = null
)

/**
 * 关阀结果查询 (/order/tcpDevice/closeOrder/result/query)
 *
 * 用于确认 closeOrder 是否真正执行成功。服务器对成功/失败的字段
 * 命名可能因学校而异，故用 @SerializedName 做多字段容错。
 */
data class CloseOrderResult(
    /** 订单号（官方部分响应同样用 `timeIds`） */
    @SerializedName(value = "orderNo", alternate = ["timeIds"])
    val orderNo: String? = null,
    /** 订单状态：1=使用中，0=已关闭（部分服务器用 state） */
    val state: Int? = null,
    /** 订单状态（部分服务器用 status） */
    val status: Int? = null,
    /** 操作结果码，0 通常表示成功 */
    val result: Int? = null,
    /**
     * 最终消费金额（元，数字形式）。
     * 官方 `PostConsumeData` 主键是 `upMoney`（`consumeMoneyReal` 也是别名侧），
     * 只认 `consumeMoney` 会丢金额。
     */
    @SerializedName(value = "consumeMoney", alternate = ["upMoney", "consumeMoneyReal"])
    val consumeMoney: Double? = null,
    /** 最终消费金额（元，字符串形式，部分学校返回） */
    @SerializedName("consumeMoneyStr") val consumeMoneyStr: String? = null,
    /** 结算时间（官方主键 `fishTime`） */
    @SerializedName(value = "consumeTime", alternate = ["fishTime"])
    val consumeTime: String? = null,
    /** 设备序列号 */
    @SerializedName(value = "deviceSnCode", alternate = ["deviceSncode"])
    val deviceSnCode: String? = null
)
