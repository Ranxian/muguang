package io.github.ranxian.muguang.data

import com.google.gson.Gson
import io.github.ranxian.muguang.utils.PrefsHelper

/**
 * 蓝牙表「同一时刻只能有一台在用水」的守卫。
 *
 * ## 为什么必须拦
 *
 * 蓝牙表的阀是**手机**开的，关阀也得由手机连上去写指令。整个 App 只有一条
 * `BleController`，`connect()` 的第一步就是 `disconnect()` 掉上一条连接。
 *
 * 于是用户在 A 表用水、点「返回主页」（`minimizeShower` 保留订单与连接）、
 * 再从附近设备开 B 表时：
 * 1. `startBleShower` 复用同一个 controller，`connect(B)` 把 A 的连接掐了
 * 2. `bleSession` / `bleSessionJson` 被 B 覆盖
 * 3. A 的阀还开着，但已经没有任何会话/连接能把它关上 —— 水一直流、一直计费
 *
 * 这不是理论问题：`connect()` 里的 `disconnect()` 是写死的。
 *
 * ## 判据
 *
 * 「另一台」= 持久化会话的 snCode 与目标不同，且该设备的本地订单还没结束
 * （`ShowerController.isRunning` 或仍有开阀时间戳）。只看会话存在不够——
 * 历史版本可能在异常路径留下过期会话，保守起见加订单状态这一层。
 *
 * 纯函数部分抽出来是为了单测（不依赖 Android）。
 */
object BleSessionGuard {

    /**
     * @param sessionJson [PrefsHelper.bleSessionJson] 的内容
     * @param targetSn 即将开阀的设备
     * @param isRunning 该 snCode 是否仍有未结束的本地订单
     * @return 冲突的会话；没有冲突返回 null
     */
    fun otherActiveSession(
        sessionJson: String,
        targetSn: String,
        isRunning: (String) -> Boolean,
        startedAt: (String) -> Long = { 0L }
    ): BleShowerController.Session? {
        if (sessionJson.isBlank()) return null
        val session = runCatching {
            Gson().fromJson(sessionJson, BleShowerController.Session::class.java)
        }.getOrNull() ?: return null
        val sn = session.snCode
        if (sn.isBlank() || sn == targetSn) return null
        val active = runCatching { isRunning(sn) }.getOrDefault(false) || startedAt(sn) > 0L
        return session.takeIf { active }
    }

    /** 生产路径：直接读 Prefs 与订单仓库 */
    fun otherActiveSession(targetSn: String): BleShowerController.Session? =
        otherActiveSession(
            sessionJson = PrefsHelper.bleSessionJson,
            targetSn = targetSn,
            isRunning = ShowerController::isRunning,
            startedAt = ShowerController::startedAt
        )

    /** 面向用户的提示文案（两台设备都点名，用户才知道该去关哪台） */
    fun conflictMessage(session: BleShowerController.Session): String {
        val name = PrefsHelper.lastDeviceName
            .takeIf { it.isNotBlank() && session.snCode == PrefsHelper.lastDeviceSnCode }
            ?: "另一台蓝牙设备"
        return "${name}正在使用中。蓝牙表同一时间只能控制一台，请先关掉它再开这台。"
    }
}
