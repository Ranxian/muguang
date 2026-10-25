package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.api.NetworkModule
import io.github.ranxian.muguang.api.getLastOrderByMacSafe
import io.github.ranxian.muguang.api.getWasherModesSafe
import io.github.ranxian.muguang.api.queryWasherStatusSafe
import io.github.ranxian.muguang.api.startWasherSafe
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.OrderLastBean
import io.github.ranxian.muguang.model.WasherMode
import io.github.ranxian.muguang.model.WasherSession
import io.github.ranxian.muguang.model.WasherStatus
import io.github.ranxian.muguang.utils.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface WasherStartOutcome {
    data class Started(
        val session: WasherSession,
        val status: WasherStatus?
    ) : WasherStartOutcome

    data class Resumed(
        val session: WasherSession,
        val status: WasherStatus?
    ) : WasherStartOutcome

    data class InUseByOthers(val message: String) : WasherStartOutcome

    data class Failed(
        val message: String,
        val errorCode: Int? = null
    ) : WasherStartOutcome
}

object WasherController {


    suspend fun loadModes(device: DeviceInfo) =
        NetworkModule.apiService.getWasherModesSafe(
            smallTypeId = device.smallTypeId
                ?: throw IllegalArgumentException("设备缺少 smallTypeId，无法读取洗衣模式")
        )


    suspend fun start(
        device: DeviceInfo,
        mode: WasherMode
    ): WasherStartOutcome {
        if (!device.isLaundry) {
            return WasherStartOutcome.Failed("该设备不是已支持的洗衣机")
        }
        if (device.snCode.isBlank()) {
            return WasherStartOutcome.Failed("设备缺少 SN 码，无法启动")
        }
        if (mode.typeId <= 0) {
            return WasherStartOutcome.Failed("所选模式缺少有效编号")
        }

        val resp = try {
            NetworkModule.apiService.startWasherSafe(
                snCode = device.snCode,
                modeTypeId = mode.typeId,
                auth = NetworkModule.authFields(),
                isHaier = device.devTypeStatus == 7
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("启动洗衣机失败", e)
            return WasherStartOutcome.Failed("网络异常，未能启动设备")
        }

        if (resp.success && resp.errorCode == 0) {
            val orderId = resp.data?.timeIds.orEmpty()
            val status = pollInitialStatus(device)
            val resolvedOrder = orderId.ifBlank { status?.orderKey.orEmpty() }
            if (resolvedOrder.isBlank()) {
                AppLogger.w("洗衣机启动成功，但暂未拿到 timeIds：${device.snCode}")
            }
            return WasherStartOutcome.Started(
                session = WasherSession(
                    device = device,
                    mode = mode,
                    orderId = resolvedOrder,
                    preDeductMilliYuan = resp.data?.perMoney ?: mode.money
                ),
                status = status
            )
        }
        if (resp.errorCode == 307 && resp.data?.isOwner != false) {
            val status = pollInitialStatus(device)
            val orderId = resp.data?.timeIds.orEmpty().ifBlank { status?.orderKey.orEmpty() }
            return WasherStartOutcome.Resumed(
                session = WasherSession(
                    device = device,
                    mode = mode,
                    orderId = orderId,
                    preDeductMilliYuan = resp.data?.perMoney ?: mode.money
                ),
                status = status
            )
        }
        if (resp.errorCode == 307 || resp.data?.isOwner == false) {
            return WasherStartOutcome.InUseByOthers("该设备正在被他人使用")
        }
        return WasherStartOutcome.Failed(
            resp.displayMessage ?: "启动失败",
            resp.errorCode
        )
    }

    /**
     * 查询洗衣机状态。
     *
     * 官方语义（`BaseDeviceActivity.Z4` / `WashingNetActivity.Z4`，6.5.29 反编译确认）：
     * - `errorCode == 0` → **空闲**，`data` 通常为 null（这不是失败）
     * - `errorCode == 307` → 有进行中订单，`data` 携带订单详情；官方在 `isOwner=false`
     *   时同样读 `time`/`timeIds`（他人订单也显示倒计时），所以这里不按归属丢数据
     * - 剩余秒数字段正式名是 `expireTimes`，`time` 只是别名（已补进模型）
     *
     * 返回 null 仅表示**网络/解析失败**；「设备空闲」返回全零标记对象，
     * 让调用方用 `looksActive` 区分两种情况。
     */
    suspend fun status(device: DeviceInfo): WasherStatus? {
        if (device.snCode.isBlank()) return null
        val resp = try {
            NetworkModule.apiService.queryWasherStatusSafe(
                snCode = device.snCode,
                auth = NetworkModule.authFields(),
                isHaier = device.devTypeStatus == 7
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("查询洗衣机状态失败", e)
            return null
        }

        // 有 data 就交出去（他人订单也带剩余时间）；errorCode 0 且无 data = 明确空闲
        val data = resp.data
        if (data != null) return data
        if (resp.success && resp.errorCode == 0) return WasherStatus(isOwner = true)
        return null
    }


    /**
     * 设备视角的最近一单（`order/query/lastOrder/byMac`）。
     *
     * 用途：`washer/status` 是**本机视角**，有的学校对他人的订单直接回 `errorCode 0`
     * （等同空闲），绑定卡片就会把「别人正在洗」显示成「空闲」。这个接口用 mac
     * 查设备侧订单，不看归属。
     *
     * 返回 null 表示「没有订单」或「查询失败」——两者对上层都是「这条渠道没有信息」，
     * 是否空闲由 [status] 决定。
     */
    suspend fun lastOrder(device: DeviceInfo): OrderLastBean? {
        val mac = device.macAddress.takeIf { it.isNotBlank() }
            ?: device.realMac?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            val resp = NetworkModule.apiService.getLastOrderByMacSafe(
                macAddress = mac,
                isHaier = device.devTypeStatus == 7
            )
            if (resp.success || resp.errorCode == 0) resp.data else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("查询设备最近订单失败", e)
            null
        }
    }

    private suspend fun pollInitialStatus(device: DeviceInfo): WasherStatus? {
        repeat(4) { attempt ->
            if (attempt > 0) delay(500)
            status(device)?.let { return it }
        }
        return null
    }
}
