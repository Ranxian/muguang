package io.github.ranxian.muguang.api

object WasherApiContract {
    const val MODE_LIST = "/device/mode/list"
    const val START = "/order/tcpDevice/downRate/modeOrder"
    const val STATUS = "/order/tcpDevice/washer/status"

    /**
     * 设备视角的「最近一单」：**不分归属**，谁在洗都返回。
     *
     * 官方 6.5.29 用它驱动设备页（`BaseDeviceActivity.S4` → `gj.d.g()` →
     * `OrderByMacResponse`/`OrderLastBean`）；而 `washer/status` 对非本人订单
     * 回 `errorCode 0`（实测 + 官方 `WashingNetActivity.Z4` 的 0 分支即空闲 UI），
     * 拿不到别人的订单。
     */
    const val LAST_ORDER_BY_MAC = "/order/query/lastOrder/byMac"
}
