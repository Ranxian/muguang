package io.github.ranxian.muguang.data

import com.google.gson.Gson
import io.github.ranxian.muguang.model.DeviceInfo

/**
 * 已绑定洗衣机列表的序列化 / 反序列化（纯函数，方便单测）。
 *
 * 1.0 只能绑一台，存的是 `boundWasherJson` 里**单个** DeviceInfo 的 JSON。
 * 双机时代改存 `boundWashersJson` 里的 **JSON 数组**；旧键保留只读，用于升级迁移——
 * 老用户装新版后第一件事就是把那台旧绑定读进列表，不能让他重新扫一遍码。
 *
 * 上限 [MAX_BINDINGS] 是产品决定（寝室里通常一台滚筒一台波轮，两台够用；
 * 再多首页卡片也摆不下），列表本身不做裁剪，写入前的检查在 ViewModel。
 */
object BoundWasherStore {

    const val MAX_BINDINGS = 2

    private val gson = Gson()

    /**
     * @param arrayJson 新格式的 JSON 数组（空串 = 没写过）
     * @param legacySingleJson 1.0 旧格式的单个 DeviceInfo JSON
     * @return 绑定列表；新格式有内容时旧格式直接忽略（save 时会清掉旧键，不会复活）
     */
    fun parse(arrayJson: String, legacySingleJson: String): List<DeviceInfo> {
        val fromArray = runCatching {
            gson.fromJson(arrayJson, Array<DeviceInfo>::class.java).orEmpty().toList()
        }.getOrNull().orEmpty()
        if (fromArray.isNotEmpty()) return fromArray

        val legacy = runCatching {
            gson.fromJson(legacySingleJson, DeviceInfo::class.java)
        }.getOrNull()
        return legacy
            ?.takeIf { it.isLaundry && it.snCode.isNotBlank() }
            ?.let { listOf(it) }
            ?: emptyList()
    }

    fun toJson(devices: List<DeviceInfo>): String = gson.toJson(devices)
}
