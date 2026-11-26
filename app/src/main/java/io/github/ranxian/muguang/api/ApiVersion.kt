package io.github.ranxian.muguang.api

/**
 * 请求体/查询参数里上报的客户端版本号。
 *
 * 官方 6.5.29（`ui/a.java` / `cj/a.java` 常量，也是 APK 的 versionName）在
 * `BaseNetActivity.t0()` 里把这个值作为 `version` 字段发给服务端；服务端目前
 * 不做版本分支（6.5.29 反编译全仓没有按 version 判断的逻辑），但保持与官方
 * 一致可以避免将来按版本灰度时被当成老客户端。
 *
 * ⚠️ 这是**上报值**，不是本 App 的版本号（后者见 `BuildConfig.VERSION_NAME`）。
 * 升级官方基线时只改这一处。
 */
const val CLIENT_VERSION = "6.5.29"
