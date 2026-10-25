package io.github.ranxian.muguang.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.github.ranxian.muguang.model.AccountInfo
import io.github.ranxian.muguang.model.BaseResponse
import io.github.ranxian.muguang.model.BillDetail
import io.github.ranxian.muguang.model.BillItem
import io.github.ranxian.muguang.model.BleDownRateData
import io.github.ranxian.muguang.model.BleUploadData
import io.github.ranxian.muguang.model.CampusUserInfo
import io.github.ranxian.muguang.model.CloseOrderResult
import io.github.ranxian.muguang.model.DownRateResult
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.GenerateUseCodeResult
import io.github.ranxian.muguang.model.LoginData
import io.github.ranxian.muguang.model.OrderLastBean
import io.github.ranxian.muguang.model.OrderStatus
import io.github.ranxian.muguang.model.ProjectInfo
import io.github.ranxian.muguang.model.ProjectSwitchData
import io.github.ranxian.muguang.model.RechargePackage
import io.github.ranxian.muguang.model.RechargePayModes
import io.github.ranxian.muguang.model.UnpaidBill
import io.github.ranxian.muguang.model.UseCodeData
import io.github.ranxian.muguang.model.WalletData
import io.github.ranxian.muguang.model.WasherMode
import io.github.ranxian.muguang.model.WasherStartData
import io.github.ranxian.muguang.model.WasherStatus
import io.github.ranxian.muguang.data.RechargeChannel
import io.github.ranxian.muguang.data.RechargeOrder
import io.github.ranxian.muguang.data.RechargeSigner
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val gson = Gson()

private suspend fun Call<ResponseBody>.awaitString(): String {
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { this.cancel() }
        this.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        cont.resume(body.string())
                    } else {
                        cont.resumeWithException(Exception("Empty response body"))
                    }
                } else {
                    cont.resumeWithException(Exception("HTTP ${response.code()}: ${response.message()}"))
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                cont.resumeWithException(t)
            }
        })
    }
}

private fun <T> parse(json: String, dataClass: Class<T>): BaseResponse<T> {
    val obj = JsonParser().parse(json).asJsonObject
    val success = obj.get("success")?.asBoolean ?: false
    val errorCode = obj.get("errorCode")?.asInt ?: 0
    val errorMessage = obj.get("errorMessage")?.let { if (it.isJsonNull) null else it.asString }
    val msg = obj.get("msg")?.let { if (it.isJsonNull) null else it.asString }
    val dataElement = obj.get("data")
    val data: T? = if (dataElement != null && !dataElement.isJsonNull) {
        gson.fromJson(dataElement, dataClass)
    } else null
    return BaseResponse(success, data, errorCode, errorMessage, msg)
}

private fun <T> parseList(json: String, elementClass: Class<T>): BaseResponse<List<T>> {
    val obj = JsonParser().parse(json).asJsonObject
    val success = obj.get("success")?.asBoolean ?: false
    val errorCode = obj.get("errorCode")?.asInt ?: 0
    val errorMessage = obj.get("errorMessage")?.let { if (it.isJsonNull) null else it.asString }
    val msg = obj.get("msg")?.let { if (it.isJsonNull) null else it.asString }
    val dataElement = obj.get("data")
    val data: List<T>? = if (dataElement != null && !dataElement.isJsonNull && dataElement.isJsonArray) {
        val result = mutableListOf<T>()
        for (item in dataElement.asJsonArray) {
            result.add(gson.fromJson(item, elementClass))
        }
        result
    } else null
    return BaseResponse(success, data, errorCode, errorMessage, msg)
}

suspend fun QzxyService.loginSafe(
    telephone: String,
    password: String,
    phoneSystem: String = "android",
    type: Int = 0,
    version: String = "6.5.28"
): BaseResponse<LoginData> = parse(login(telephone, password, phoneSystem, type, version).awaitString(), LoginData::class.java)

suspend fun QzxyService.getWalletSafe(): BaseResponse<WalletData> =
    parse(getWallet().awaitString(), WalletData::class.java)

suspend fun QzxyService.getDeviceInfoSafe(mac: String): BaseResponse<DeviceInfo> =
    parse(getDeviceInfo(mac).awaitString(), DeviceInfo::class.java)

suspend fun QzxyService.downRateSafe(
    xfModel: Int = 0,
    snCode: String,
    auth: Map<String, String>
): BaseResponse<DownRateResult> = parse(downRate(xfModel, snCode, auth).awaitString(), DownRateResult::class.java)

suspend fun QzxyService.closeOrderSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(closeOrder(snCode, orderNo, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.downRateResultSafe(
    snCode: String,
    auth: Map<String, String>
): BaseResponse<DownRateResult> = parse(downRateResult(snCode, auth).awaitString(), DownRateResult::class.java)

suspend fun QzxyService.closeOrderResultSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<CloseOrderResult> = parse(closeOrderResult(snCode, orderNo, auth).awaitString(), CloseOrderResult::class.java)

suspend fun QzxyService.consumeOrderResultSafe(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): BaseResponse<CloseOrderResult> = parse(consumeOrderResult(snCode, orderNo, auth).awaitString(), CloseOrderResult::class.java)

/**
 * 同上，但要**原始响应体**。
 *
 * ## 为什么留这条口子
 *
 * 这个接口的响应结构以前一直没抓到过（`API-qzxy.md` 里只有汇总表和 Retrofit 签名，
 * 没有响应样例），当时的解析模型是拿隔壁接口套的、纯猜。
 * 09-18 真机跑通后已经拿到真实结构（见下方 `ConsumeOrderResult`），
 * 但**解析仍然走原始串**，理由有两条：
 *
 * 1. **字段名可能因学校而异**。目前只有一个学校（某职业技术大学）的样本，
 *    而服务端在别的接口上确实有过按学校改字段名的先例（见 `CloseOrderResult` 的注释）。
 *    按名字找金额比死认一个字段名耐操。
 * 2. **出问题时日志里得有官方原话**。用户报「结算金额不对」，让他导出日志，
 *    原始响应直接摆在那儿，不用再让他去抓包。
 *
 * `parse()` 会把 JSON 吃干抹净只吐 `BaseResponse<T>`，原始串在那一层就丢了，
 * 所以要拿原话就必须从这儿绕过去。
 */
suspend fun QzxyService.consumeOrderResultRaw(
    snCode: String,
    orderNo: String,
    auth: Map<String, String>
): String = consumeOrderResult(snCode, orderNo, auth).awaitString()

suspend fun QzxyService.queryUsingSafe(
    xfModel: Int = 0,
    snCode: String,
    auth: Map<String, String>
): BaseResponse<OrderStatus> = parse(queryUsing(xfModel, snCode, auth).awaitString(), OrderStatus::class.java)

suspend fun QzxyService.getBillListSafe(
    month: String,
    billRequestType: Int = 2
): BaseResponse<List<BillItem>> = parseList(getBillList(month, billRequestType).awaitString(), BillItem::class.java)

suspend fun QzxyService.getBillDetailSafe(
    orderId: String,
    consumeDate: String
): BaseResponse<BillDetail> = parse(getBillDetail(orderId, consumeDate).awaitString(), BillDetail::class.java)

suspend fun QzxyService.updateUseCodeStatusSafe(
    status: Int,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(updateUseCodeStatus(status, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.getUseCodeSafe(): BaseResponse<UseCodeData> =
    parse(getUseCode().awaitString(), UseCodeData::class.java)

suspend fun QzxyService.generateUseCodeSafe(
    auth: Map<String, String>
): BaseResponse<GenerateUseCodeResult> =
    parse(generateUseCode(auth).awaitString(), GenerateUseCodeResult::class.java)

suspend fun QzxyService.setUseCodeSafe(
    useCode: String,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(setUseCode(useCode, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.getVerificationCodeSafe(
    telephone: String,
    secret: String,
    typeId: Int = 3
): BaseResponse<Unit> =
    parse(getVerificationCode(telephone, secret, typeId).awaitString(), Unit::class.java)

suspend fun QzxyService.registerAndLoginSafe(telephone: String, smsCode: String): BaseResponse<LoginData> =
    parse(registerAndLogin(telephone, smsCode).awaitString(), LoginData::class.java)

// ── 账号信息 / 一卡通 ──

suspend fun QzxyService.getAccountInfoSafe(): BaseResponse<AccountInfo> =
    parse(getAccountInfo().awaitString(), AccountInfo::class.java)

/** 字段是 [getAccountInfoSafe] 的超集，复用同一个模型即可（Gson 只映射存在的字段） */
suspend fun QzxyService.getBindCardInfoSafe(): BaseResponse<AccountInfo> =
    parse(getBindCardInfo().awaitString(), AccountInfo::class.java)

suspend fun QzxyService.forgetPasswordSafe(
    password: String,
    code: String,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(forgetPassword(password, code, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.getProjectInfoSafe(): BaseResponse<ProjectInfo> =
    parse(getProjectInfo().awaitString(), ProjectInfo::class.java)

suspend fun QzxyService.getProjectListSafe(): BaseResponse<List<ProjectInfo>> =
    parseList(getProjectList().awaitString(), ProjectInfo::class.java)

suspend fun QzxyService.switchProjectSafe(
    projectId: String
): BaseResponse<ProjectSwitchData> {
    return parse(
        switchProject(projectId).awaitString(),
        ProjectSwitchData::class.java
    )
}

suspend fun QzxyService.getWasherModesSafe(
    smallTypeId: Int
): BaseResponse<List<WasherMode>> =
    parseList(getWasherModes(smallTypeId).awaitString(), WasherMode::class.java)

suspend fun QzxyService.startWasherSafe(
    snCode: String,
    modeTypeId: Int,
    auth: Map<String, String>,
    isHaier: Boolean = false
): BaseResponse<WasherStartData> {
    val fields = linkedMapOf(
        "snCode" to snCode,
        "xfModel" to modeTypeId.toString(),
        "isHaier" to if (isHaier) "1" else "0"
    )
    return parse(
        startWasher(auth + fields).awaitString(),
        WasherStartData::class.java
    )
}

suspend fun QzxyService.queryWasherStatusSafe(
    snCode: String,
    auth: Map<String, String>,
    isHaier: Boolean = false
): BaseResponse<WasherStatus> {
    val fields = linkedMapOf(
        "snCode" to snCode,
        "isHaier" to if (isHaier) "1" else "0"
    )
    return parse(
        queryWasherStatus(auth + fields).awaitString(),
        WasherStatus::class.java
    )
}

/**
 * 设备视角的最近一单：**不分归属**，谁在洗都回。
 *
 * `washer/status` 只覆盖「本机视角」——有的学校对别人的订单直接回 `errorCode 0`
 * （= 空闲），绑定卡片就会在他人在用时显示「空闲」。这个接口用 mac 查设备侧订单，
 * 拿来补这个洞。
 *
 * 参数按官方新版 API 分支：`macAddress` + `isHaier`（`isHaier = devTypeStatus == 7`）。
 */
suspend fun QzxyService.getLastOrderByMacSafe(
    macAddress: String,
    isHaier: Boolean = false
): BaseResponse<OrderLastBean> = parse(
    getLastOrderByMac(macAddress, if (isHaier) 1 else 0).awaitString(),
    OrderLastBean::class.java
)

// ── 残留账单的手动代扣 ──

suspend fun QzxyService.queryUnpaidBillsSafe(auth: Map<String, String>): BaseResponse<List<UnpaidBill>> =
    parseList(queryUnpaidBills(auth).awaitString(), UnpaidBill::class.java)

/**
 * 手动请求代扣。
 *
 * body 是 **JSON**（本项目唯一一个），字段固定就那几个，直接拼更省事——
 * 但**值必须转义**，所以走 Gson 而不是字符串模板。
 *
 * ⚠️ `phoneSystem` 传 `WeChat` 是**照抄抓包**的。这个接口是官方 App 里那个 H5
 * 页面调的，和 Android 原生请求（`phoneSystem=android`）不是一条路。
 * 服务端万一校验这个字段，传 android 就会被拒；照抄成功过的那份最稳。
 */
suspend fun QzxyService.requestDeductSafe(
    consumeDate: String,
    auth: Map<String, String>,
    deductType: Int = DEDUCT_TYPE
): BaseResponse<Unit> {
    val body = Gson().toJson(
        mapOf(
            "deductType" to deductType,
            "consumeDate" to consumeDate,
            "projectId" to (auth["projectId"] ?: ""),
            "accountId" to (auth["accountId"] ?: ""),
            "loginCode" to (auth["loginCode"] ?: ""),
            "userId" to (auth["userId"] ?: ""),
            "phoneSystem" to "WeChat"
        )
    )
    return parse(requestDeduct(body.toRequestBody(JSON_MEDIA_TYPE)).awaitString(), Unit::class.java)
}

/**
 * 代扣类型。**只有一个样本，不保证是常量**——万一服务端按扣款渠道变，
 * 会返回错误码而不是扣错钱，改这一处即可。
 * 抓包实测 `{"deductType":7,...}` → `{"success":true}`。
 */
const val DEDUCT_TYPE = 7

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

suspend fun QzxyService.getCampusUserInfoSafe(): BaseResponse<CampusUserInfo> =
    parse(getCampusUserInfo().awaitString(), CampusUserInfo::class.java)

suspend fun QzxyService.updatePhoneSafe(
    newTelephone: String,
    code: String,
    auth: Map<String, String>
): BaseResponse<Unit> =
    parse(updatePhone(newTelephone, code, auth).awaitString(), Unit::class.java)

suspend fun QzxyService.updatePasswordSafe(
    oldPassword: String,
    newPassword: String,
    auth: Map<String, String>
): BaseResponse<Unit> =
    parse(updatePassword(oldPassword, newPassword, auth).awaitString(), Unit::class.java)

// ── 蓝牙水表通道 ──
//
// 走这三个接口的设备**没有 4G**，手机是网关：服务端只下发费率包（downData），
// 阀由手机通过 BLE 写进设备。详见 docs/蓝牙表适配调研.md

suspend fun QzxyService.bluetoothRateOrderSafe(
    deviceId: String,
    macAddress: String,
    macType: String,
    bigTypeId: String,
    smallTypeId: String,
    protocolType: String,
    randomNumber: String,
    xfModel: String,
    signature: String,
    auth: Map<String, String>
): BaseResponse<BleDownRateData> = parse(
    bluetoothRateOrder(
        deviceId = deviceId,
        macAddress = macAddress,
        macType = macType,
        bigTypeId = bigTypeId,
        smallTypeId = smallTypeId,
        protocolType = protocolType,
        randomNumber = randomNumber,
        xfModel = xfModel,
        signature = signature,
        auth = auth
    ).awaitString(),
    BleDownRateData::class.java
)

suspend fun QzxyService.bluetoothUploadDataSafe(
    protocolType: String,
    randomNumber: String,
    xfData: String,
    signature: String,
    auth: Map<String, String>
): BaseResponse<BleUploadData> = parse(
    bluetoothUploadData(
        protocolType = protocolType,
        randomNumber = randomNumber,
        xfData = xfData,
        signature = signature,
        auth = auth
    ).awaitString(),
    BleUploadData::class.java
)

suspend fun QzxyService.bluetoothFailOrderSafe(
    consumeDate: String,
    signature: String,
    auth: Map<String, String>
): BaseResponse<Unit> = parse(
    bluetoothFailOrder(consumeDate, signature, auth).awaitString(),
    Unit::class.java
)

// ── 充值 ──

suspend fun QzxyService.getRechargePayModesSafe(): BaseResponse<RechargePayModes> =
    parse(getRechargePayModes().awaitString(), RechargePayModes::class.java)

suspend fun QzxyService.getRechargePackagesSafe(): BaseResponse<List<RechargePackage>> =
    parseList(getRechargePackages().awaitString(), RechargePackage::class.java)

/**
 * 下单。
 *
 * 官方 `RechargeActivity` 里这两份 map 是分开构造的，这里照搬：
 *
 * 1. **签名输入**只有 `loginCode / payWay / rechargeMoney / telPhone`（`ek.e0.e`）
 * 2. **请求体**只有 `payWay / rechargeMoney / signature`，认证字段由基类 `t0()` 补
 *
 * 本项目没有那个基类（GET 的认证参数由拦截器补，POST 不会），所以认证字段
 * 在这一层显式拼进 body——少了它们服务端直接回 `8 未获取到用户信息`。
 *
 * 响应 data 三种形态：支付宝是**裸字符串**（orderInfo），聚合是
 * `{cashierUrl, tranNo}`，校园卡是 `{campusRechargeUrl}`，所以走原始 JSON 解析。
 */
suspend fun QzxyService.placeRechargeOrderSafe(
    channel: RechargeChannel,
    amount: String,
    telPhone: String,
    loginCode: String,
    auth: Map<String, String>
): BaseResponse<RechargeOrder> {
    val signature = RechargeSigner.sign(
        mapOf(
            "loginCode" to loginCode,
            "payWay" to channel.payWay,
            "rechargeMoney" to amount,
            "telPhone" to telPhone
        )
    )
    val body = linkedMapOf(
        "payWay" to channel.payWay,
        "rechargeMoney" to amount,
        "signature" to signature
    )
    // 认证字段：官方 t0() 注入的那一组，一个都不能少
    body["projectId"] = auth["projectId"].orEmpty()
    body["accountId"] = auth["accountId"].orEmpty()
    body["userId"] = auth["userId"].orEmpty()
    body["telephone"] = auth["telephone"] ?: telPhone
    body["telPhone"] = telPhone
    body["loginCode"] = loginCode
    body["phoneSystem"] = "android"
    body["version"] = "6.5.28"

    val json = when (channel) {
        RechargeChannel.ALIPAY -> alipayRecharge(body).awaitString()
        RechargeChannel.JUHE -> juheRecharge(body).awaitString()
        RechargeChannel.CAMPUS -> campusRecharge(body).awaitString()
    }
    return parseRechargeOrder(json)
}

/**
 * 解析下单响应。
 *
 * `data` 可能是字符串（支付宝 orderInfo）、对象（收银台/校园卡 URL），
 * 也可能直接是 null（失败）。这里不按通道硬编码字段名，而是**先按形态判断、再逐个候选键找 URL**：
 * 只有一个学校的样本，字段名换过的先例在这个 API 上已经出现过。
 */
private fun parseRechargeOrder(json: String): BaseResponse<RechargeOrder> {
    val obj = JsonParser().parse(json).asJsonObject
    val success = obj.get("success")?.asBoolean ?: false
    val errorCode = obj.get("errorCode")?.asInt ?: 0
    val errorMessage = obj.get("errorMessage")?.let { if (it.isJsonNull) null else it.asString }
    val msg = obj.get("msg")?.let { if (it.isJsonNull) null else it.asString }
    val dataElement = obj.get("data")

    val order: RechargeOrder? = when {
        dataElement == null || dataElement.isJsonNull -> null
        dataElement.isJsonPrimitive -> {
            val value = dataElement.asString.trim()
            when {
                value.isEmpty() -> null
                // orderInfo 是一大串签名后的 key=value，绝不以 http 开头；
                // 真有学校直接把收银台 URL 塞进字符串里，那就当 URL 处理
                value.startsWith("http", ignoreCase = true) -> RechargeOrder.Browser(value)
                else -> RechargeOrder.Alipay(value)
            }
        }
        dataElement.isJsonObject -> {
            val data = dataElement.asJsonObject
            val url = listOf("cashierUrl", "campusRechargeUrl", "payUrl", "url", "jumpUrl", "h5Url")
                .firstNotNullOfOrNull { key ->
                    data.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                }
            when {
                url != null -> RechargeOrder.Browser(url)
                // 对象里塞 orderInfo 的情况也兜一下
                data.get("orderInfo")?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true ->
                    RechargeOrder.Alipay(data.get("orderInfo").asString)
                else -> null
            }
        }
        else -> null
    }
    return BaseResponse(success, order, errorCode, errorMessage, msg)
}

