package io.github.ranxian.muguang.data

import com.google.gson.Gson
import io.github.ranxian.muguang.model.DeviceInfo

/**
 * 「我的热水器」的持久化。
 *
 * 与洗衣机不同，热水器**不需要手动绑定**：绑定寝室之后，扫描结果里同寝室的那台
 * 淋浴热水器会被自动认出来并记在这里（一间寝室一台，后检测到的替换旧的）。
 * 记完整 `DeviceInfo` 而不是只记 snCode，理由和 `BoundWasherStore` 相同：
 * 轮询状态、打开详情都用得到这些字段，重新查一次设备信息既慢又可能失败。
 *
 * project-scoped（[io.github.ranxian.muguang.utils.PrefsHelper.boundHeaterJson]），
 * 不同学校的热水器不是同一台，切换项目后必须跟着项目走。
 */
object BoundHeaterStore {

    private val gson = Gson()

    /** 解析失败、空串、或缺少关键字段（snCode 为空）时返回 null——宁可当没绑，也不要半条记录。 */
    fun parse(json: String?): DeviceInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, DeviceInfo::class.java)
                ?.takeIf { it.snCode.isNotBlank() && it.deviceName.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun toJson(device: DeviceInfo?): String =
        if (device == null) "" else gson.toJson(device)

    /**
     * 这台热水器还在不在当前绑定的寝室里。
     *
     * `boundRoom` 为空表示用户没绑寝室——此时不设限（手动绑定的热水器照常保留）。
     * 判定复用 [DeviceInfo.inSameRoom] 这一处规则，不另写一套。
     */
    fun stillInRoom(boundRoom: String, device: DeviceInfo): Boolean =
        boundRoom.isBlank() || DeviceInfo.inSameRoom(boundRoom, device.deviceName)
}
