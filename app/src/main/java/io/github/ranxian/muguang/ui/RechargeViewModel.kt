package io.github.ranxian.muguang.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ranxian.muguang.api.NetworkModule
import io.github.ranxian.muguang.api.getRechargePackagesSafe
import io.github.ranxian.muguang.api.getRechargePayModesSafe
import io.github.ranxian.muguang.api.placeRechargeOrderSafe
import io.github.ranxian.muguang.data.AlipayResult
import io.github.ranxian.muguang.data.RechargeAmounts
import io.github.ranxian.muguang.data.RechargeChannel
import io.github.ranxian.muguang.data.RechargeOrder
import io.github.ranxian.muguang.model.RechargePackage
import io.github.ranxian.muguang.utils.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * 充值页的状态与下单流程。
 *
 * ## 什么在本地、什么在服务端
 *
 * 金额选项来自 `recharge/package/list`，支付通道来自 `recharge/pay/mode`，
 * 下单结果（orderInfo / 收银台地址）来自下单接口——**三样都不许本地造**。
 * 本类只做三件事：选、下单、把结果翻译成人话。
 *
 * 余额也**不在这里改**：支付成功后只发一个 [Event.Paid] 让调用方去重新拉
 * `/account/wallet`。本地加减一个数字既骗不过服务端，也会让用户以为充上了。
 */
class RechargeViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        /** 读取配置失败的原话；null 表示没失败 */
        val loadError: String? = null,
        val packages: List<RechargePackage> = emptyList(),
        val channels: List<RechargeChannel> = emptyList(),
        val selectedTypeId: Long? = null,
        val selectedChannel: RechargeChannel? = null,
        /** 下单请求在途 */
        val placing: Boolean = false,
        /** 支付宝收银台已拉起，等结果 */
        val paying: Boolean = false,
        val notice: String? = null,
        val noticeIsError: Boolean = false,
        /** 服务端下发的提示（例如「本校充值不保存余额」），有就展示 */
        val noSaveMessage: String? = null,
    ) {
        val selectedPackage: RechargePackage?
            get() = packages.firstOrNull { it.typeId == selectedTypeId }

        /** 选中的金额（规范化后），没有可选套餐时为 null */
        val amount: String?
            get() = selectedPackage?.amount?.let { RechargeAmounts.normalize(it) }

        val canSubmit: Boolean
            get() = !loading && !placing && !paying && amount != null && selectedChannel != null
    }

    /** 只发一次的事件：拉起支付宝 / 打开收银台 / 支付成功（该刷余额了） */
    sealed interface Event {
        data class LaunchAlipay(val orderInfo: String) : Event
        data class OpenCashier(val url: String) : Event
        object Paid : Event
    }

    var state by mutableStateOf(UiState())
        private set

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events: SharedFlow<Event> = _events

    /**
     * 拉配置。
     *
     * 两个 GET 串行发：并发没有收益（都很快），串行反而能在第一个失败时
     * 直接给出准确原因，不会出现「一个成功一个失败、界面该显示什么」的歧义。
     */
    fun load() {
        if (state.loading) return
        val auth = NetworkModule.authFields()
        if (auth["loginCode"].isNullOrBlank()) {
            state = state.copy(
                loading = false,
                loadError = "登录状态已失效，请返回重新登录"
            )
            return
        }
        state = state.copy(
            loading = true,
            loadError = null,
            notice = null,
            noticeIsError = false,
            placing = false,
            paying = false
        )
        viewModelScope.launch {
            try {
                val modesResp = NetworkModule.apiService.getRechargePayModesSafe()
                val modes = modesResp.data
                if (modes == null) {
                    state = state.copy(
                        loading = false,
                        loadError = modesResp.displayMessage?.takeIf { it.isNotBlank() }
                            ?: "服务端未返回支付方式"
                    )
                    return@launch
                }
                val channels = RechargeChannel.fromPayWays(modes.payWay)
                val pkgResp = NetworkModule.apiService.getRechargePackagesSafe()
                val packages = pkgResp.data
                if (packages == null) {
                    state = state.copy(
                        loading = false,
                        loadError = pkgResp.displayMessage?.takeIf { it.isNotBlank() }
                            ?: "服务端未返回充值套餐"
                    )
                    return@launch
                }
                // 金额拿不到或不合规的套餐直接不显示——点下去只会换来一句
                // 服务端看不懂的「网络出错了」
                val usable = packages.filter { RechargeAmounts.normalize(it.amount) != null }
                val keepChannel = state.selectedChannel?.takeIf { it in channels }
                state = state.copy(
                    loading = false,
                    loadError = null,
                    channels = channels,
                    packages = usable,
                    noSaveMessage = modes.noSaveMessage?.takeIf { it.isNotBlank() },
                    selectedTypeId = usable.firstOrNull { it.typeId == state.selectedTypeId }?.typeId
                        ?: usable.firstOrNull()?.typeId,
                    selectedChannel = keepChannel ?: channels.firstOrNull()
                )
            } catch (e: Exception) {
                AppLogger.w("读取充值配置失败：${e.message}")
                state = state.copy(
                    loading = false,
                    loadError = "读取充值信息失败：${e.message ?: "网络异常"}"
                )
            }
        }
    }

    fun selectPackage(typeId: Long) {
        if (state.selectedTypeId == typeId) return
        state = state.copy(selectedTypeId = typeId, notice = null, noticeIsError = false)
    }

    fun selectChannel(channel: RechargeChannel) {
        if (state.selectedChannel == channel) return
        state = state.copy(selectedChannel = channel, notice = null, noticeIsError = false)
    }

    fun dismissNotice() {
        state = state.copy(notice = null, noticeIsError = false)
    }

    /** 下单。所有校验都在真正发请求之前做完 */
    fun submit() {
        val s = state
        if (!s.canSubmit) return
        val channel = s.selectedChannel ?: return
        val amount = s.amount ?: return

        val auth = NetworkModule.authFields()
        val loginCode = auth["loginCode"].orEmpty()
        val telPhone = auth["telPhone"] ?: auth["telephone"].orEmpty()
        if (loginCode.isBlank() || telPhone.isBlank()) {
            state = s.copy(notice = "登录信息不完整，请重新登录后再试", noticeIsError = true)
            return
        }

        state = s.copy(placing = true, notice = null, noticeIsError = false)
        viewModelScope.launch {
            try {
                val resp = NetworkModule.apiService.placeRechargeOrderSafe(
                    channel = channel,
                    amount = amount,
                    telPhone = telPhone,
                    loginCode = loginCode,
                    auth = auth
                )
                val order = resp.data
                if (resp.errorCode != 0 || order == null) {
                    // 服务端原话最准（余额上限 / 渠道关闭 / 未签约都是它说了算）
                    val reason = resp.displayMessage?.takeIf { it.isNotBlank() }
                        ?: if (resp.errorCode != 0) "下单失败（${resp.errorCode}）"
                        else "服务端未返回支付信息"
                    state = state.copy(placing = false, notice = reason, noticeIsError = true)
                    return@launch
                }
                when (order) {
                    is RechargeOrder.Alipay -> {
                        state = state.copy(
                            placing = false,
                            paying = true,
                            notice = "正在拉起支付宝…",
                            noticeIsError = false
                        )
                        _events.emit(Event.LaunchAlipay(order.orderInfo))
                    }
                    is RechargeOrder.Browser -> {
                        state = state.copy(
                            placing = false,
                            notice = "已打开收银台，完成支付后回来点「刷新余额」",
                            noticeIsError = false
                        )
                        _events.emit(Event.OpenCashier(order.url))
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("充值下单失败：${e.message}")
                state = state.copy(
                    placing = false,
                    notice = "下单失败：${e.message ?: "网络异常"}",
                    noticeIsError = true
                )
            }
        }
    }

    /**
     * 支付宝回传结果。
     *
     * 「处理中 / 结果未知 / 网络异常」一律**不报失败**：钱可能已经扣了，
     * 说失败会诱导用户再付一次。这几种只说「去刷新余额确认」。
     */
    fun onAlipayResult(result: AlipayResult) {
        state = state.copy(
            placing = false,
            paying = false,
            notice = describe(result),
            noticeIsError = result == AlipayResult.FAILED
        )
        if (result == AlipayResult.SUCCESS) _events.tryEmit(Event.Paid)
    }

    /** 收银台根本没拉起来（没装支付宝且 H5 也失败） */
    fun onAlipayLaunchFailed(reason: String?) {
        state = state.copy(
            placing = false,
            paying = false,
            notice = reason?.takeIf { it.isNotBlank() } ?: "无法拉起支付宝，请确认已安装支付宝或改用其它通道",
            noticeIsError = true
        )
    }

    private fun describe(result: AlipayResult): String = when (result) {
        AlipayResult.SUCCESS -> "支付成功，正在刷新余额…"
        AlipayResult.PROCESSING -> "支付处理中，稍后点「刷新余额」确认到账"
        AlipayResult.CANCELLED -> "已取消支付"
        AlipayResult.NETWORK_ERROR -> "网络异常，支付结果未知，请点「刷新余额」确认"
        AlipayResult.FAILED -> "支付失败"
        AlipayResult.REPEAT -> "重复的支付请求，请刷新余额确认是否已到账"
        AlipayResult.UNKNOWN -> "支付结果未知，请点「刷新余额」确认"
    }
}
