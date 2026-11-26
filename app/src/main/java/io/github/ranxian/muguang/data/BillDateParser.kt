package io.github.ranxian.muguang.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object BillDateParser {
    /**
     * 服务端墙钟的时区。
     *
     * `consumeDate` 之类的字段是**服务器本地时间**（学校都在国内，服务器按 +08 打点），
     * 而结算对账要拿它和本机 `startedAt` 求差。按设备默认时区解析时，
     * 手机在海外/时区被改成 UTC 后整段会偏移 8 小时——对账窗口永远落空。
     * 与 `WasherModels.parseServerTime` 保持同一个口径。
     */
    fun defaultZoneId(): ZoneId = ZoneId.of("Asia/Shanghai")

    private val formatters = listOf(
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS").withResolverStyle(ResolverStyle.STRICT)
    )

    fun parseLocalDateTime(value: String): LocalDateTime? =
        formatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDateTime.parse(value, formatter) }.getOrNull()
        }

    fun toEpochMillis(value: String, zoneId: ZoneId = defaultZoneId()): Long =
        parseLocalDateTime(value)?.atZone(zoneId)?.toInstant()?.toEpochMilli() ?: 0L
}
