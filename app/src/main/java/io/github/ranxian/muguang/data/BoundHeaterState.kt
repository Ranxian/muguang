package io.github.ranxian.muguang.data

/**
 * 已检测到的热水器在界面上的投影。
 *
 * ## 数据源只有一个：`/order/tcpDevice/query/rateOrder/using`
 *
 * 洗衣机要融合「本机视角 + 设备视角」两个接口才能还原状态与剩余时间；热水器没有
 * `lastOrder/byMac` 这一路，但 `queryUsing` 的返回已经够用（见 API 文档 4.5）：
 *
 * | 响应 | 含义 |
 * |---|---|
 * | `errorCode == 307` 或 `data.orderNo != null` | 有单在用 |
 * | `success == true` 且没有 orderNo | 确实空闲 |
 * | 请求本身失败 | **不知道**——绝不能当成空闲 |
 *
 * `data.isOwner` 区分「我的单」和「别人的单」。**别人的单不猜剩余时间**：
 * 服务端没给剩余秒（`expireTimes` 实测为 null），编一个倒计时等于替服务端担保。
 *
 * 所以这里的状态是一组**布尔事实**（有人用 / 是不是我的单），不是一个数。
 */
data class BoundHeaterView(
    /** 空闲 / 使用中 · 他人订单 / 使用中 · 我的订单 / 读取中… / 未能获取状态 */
    val statusLabel: String,
    val isActive: Boolean,
    val isOwner: Boolean,
    /** 是否真的拿到了服务端答复（false 时 [statusLabel] 是「读取中/未能获取」） */
    val known: Boolean,
)

object BoundHeaterState {

    /** 首次进入、还没查过：显示「读取中…」，不能显示「空闲」。 */
    val reading = BoundHeaterView(statusLabel = "读取中…", isActive = false, isOwner = false, known = false)

    /** 查询失败（网络/解析）：**不知道**，不能断言空闲。 */
    val failed = BoundHeaterView(statusLabel = "未能获取状态", isActive = false, isOwner = false, known = false)

    /**
     * @param active 服务端说这台设备上有没有进行中的订单
     * @param isOwner 那单是不是本人下的；`null` = 服务端没给（307 但 data 为空），
     *   此时只说「使用中」，不认领也不甩锅
     */
    fun fromQuery(active: Boolean, isOwner: Boolean?): BoundHeaterView = when {
        !active -> BoundHeaterView(statusLabel = "空闲", isActive = false, isOwner = false, known = true)
        isOwner == false -> BoundHeaterView(
            statusLabel = "使用中 · 他人订单", isActive = true, isOwner = false, known = true
        )
        isOwner == null -> BoundHeaterView(
            statusLabel = "使用中", isActive = true, isOwner = false, known = true
        )
        else -> BoundHeaterView(
            statusLabel = "使用中 · 我的订单", isActive = true, isOwner = true, known = true
        )
    }
}
