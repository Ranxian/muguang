package io.github.ranxian.muguang.api

import com.google.gson.JsonParser
import io.github.ranxian.muguang.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 发布源仓库（GitHub API 的 owner/repo 形式）。
 *
 * **检查更新走 GitHub API**：`api.github.com` 在国内实测能通（约 0.6 秒）。
 */
private const val FEED_REPO = "Ranxian/muguang"

/**
 * Gitee 镜像仓库（同样 owner/repo）。
 *
 * 只用来**加速下载安装包**：GitHub 的 Release 附件在大陆走
 * `objects.githubusercontent.com`，实测只有约 100 KB/s；Gitee 约 2 MB/s。
 * 检查更新本身不经过这里，所以镜像没同步时也能正常提示有新版本。
 */
private const val MIRROR_REPO = "Ranxian/muguang"

/** 发行版里的一个附件（这里主要用 APK） */
data class UpdateAsset(
    val name: String,
    /** 浏览器直链，OkHttp 下载也走这个 */
    val downloadUrl: String,
    val sizeBytes: Long
)

/** 更新源的一条 Release 信息 */
data class UpdateRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val assets: List<UpdateAsset> = emptyList(),
    /** 预发布版本。列表里要滤掉，否则发个测试版会提示所有用户更新 */
    val prerelease: Boolean = false
) {
    /** 发行版里的 APK 附件，取第一个 */
    val apkAsset: UpdateAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", true) }

    /**
     * Gitee 镜像的下载直链。
     *
     * 不用额外发请求——两边的 tag 和附件名一致时直接拼：
     *   https://gitee.com/<MIRROR_REPO>/releases/download/v1.2.0/muguang-v1.2.0.apk
     *
     * ⚠️ 前提是发版时 Gitee 用了**同名 tag、同名附件**。少一个就拼不出来，
     * 下载会自动回落到 [apkAsset] 的 GitHub 地址。
     */
    val mirrorApkUrl: String?
        get() {
            val a = apkAsset ?: return null
            if (tagName.isEmpty()) return null
            return "https://gitee.com/$MIRROR_REPO/releases/download/$tagName/${a.name}"
        }

    /**
     * 实际下载用的直链：**优先 Gitee 镜像**（快），失败再回落 GitHub。
     *
     * 调用方只拿一个 URL，所以这里先给镜像；下载失败时
     * [io.github.ranxian.muguang.utils.ApkUpdater] 会带上 [originApkUrl]
     * 的 GitHub 地址重试。
     */
    val directApkUrl: String?
        get() = (mirrorApkUrl ?: apkAsset?.downloadUrl)?.takeIf { it.isNotBlank() }

    /** GitHub 原链，作为镜像下载失败后的回落 */
    val originApkUrl: String? get() = apkAsset?.downloadUrl
}

/** 更新源的仓库信息 */
data class UpdateRepoInfo(
    val fullName: String,
    val description: String,
    val stars: Int,
    val ownerLogin: String,
    val ownerAvatar: String,
    val htmlUrl: String
)

/**
 * GitHub Release 的 JSON →
 * [UpdateRelease]。抽成顶层 internal 是为了能拿真实响应做单测：
 * 解析错一个字段，用户侧表现是「明明发了新版却收不到」或「点下载 404」，
 * 而这两件事在 CI 里没有别的办法发现。
 */
internal fun parseGithubRelease(json: String): UpdateRelease? =
    try {
        parseReleaseObject(JsonParser.parseString(json).asJsonObject)
    } catch (e: Exception) {
        AppLogger.e("解析 release 失败", e)
        null
    }

private fun parseReleaseObject(o: com.google.gson.JsonObject): UpdateRelease = UpdateRelease(
    tagName = o.get("tag_name")?.asString ?: "",
    name = o.get("name")?.asString ?: "",
    body = o.get("body")?.asString ?: "",
    publishedAt = o.get("published_at")?.asString ?: "",
    htmlUrl = o.get("html_url")?.asString ?: "",
    prerelease = o.get("prerelease")?.asBoolean ?: false,
    assets = o.getAsJsonArray("assets")?.mapNotNull { el ->
        val a = el.asJsonObject
        val url = a.get("browser_download_url")?.asString
        val name = a.get("name")?.asString
        // 没有下载直链的附件直接跳过——没它就没法下载
        if (url.isNullOrEmpty() || name.isNullOrEmpty()) null
        else UpdateAsset(name, url, a.get("size")?.asLong ?: 0L)
    } ?: emptyList()
)

/**
 * 更新源 API 客户端（Release / 仓库信息，协议与 GitHub Release JSON 兼容）。
 *
 * [FEED_REPO] 为空时视为未配置：全部方法返回 null/空，不发起任何网络请求。
 */
object UpdateApi {
    private const val BASE = "https://api.github.com"
    private const val REPO = FEED_REPO

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request =
        Request.Builder()
            .url("$BASE$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Muguang-Android")
            .build()

    /** 更新源是否已配置；未配置时调用方直接走降级文案 */
    val configured: Boolean get() = FEED_REPO.isNotBlank()

    /** 单个 Release（含附件 digest）的 API 地址；未配置时返回空串，调用方不得发起请求 */
    fun releaseByTagUrl(tag: String): String =
        if (configured) "$BASE/repos/$REPO/releases/tags/${java.net.URLEncoder.encode(tag, "UTF-8")}" else ""

    private suspend fun getJson(path: String): String? {
        if (!configured) return null
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request(path)).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLogger.w("更新源 ${resp.code} $path")
                        return@withContext null
                    }
                    resp.body?.string()
                }
            } catch (e: Exception) {
                // 国内直连 api.github.com 偶发失败，属预期情况，仅记录不抛出
                AppLogger.w("更新源请求失败 $path: ${e.message}")
                null
            }
        }
    }

    /** 最新 Release；失败返回 null */
    suspend fun fetchLatestRelease(): UpdateRelease? {
        val json = getJson("/repos/$REPO/releases/latest") ?: return null
        return try {
            parseRelease(JsonParser.parseString(json).asJsonObject)
        } catch (e: Exception) {
            AppLogger.e("解析 release 失败", e)
            null
        }
    }

    /**
     * 全部 Release（最新在前，最多 10 条）；失败返回空列表。
     *
     * **滤掉预发布**：调用方拿 `firstOrNull()` 当"最新版"来比对，
     * 不过滤的话，只要发过一个标记为 pre-release 的测试版，所有人都会被提示更新到它。
     */
    suspend fun fetchReleases(): List<UpdateRelease> {
        val json = getJson("/repos/$REPO/releases?per_page=10") ?: return emptyList()
        return try {
            JsonParser.parseString(json).asJsonArray
                .map { el -> parseRelease(el.asJsonObject) }
                .filterNot { it.prerelease }
        } catch (e: Exception) {
            AppLogger.e("解析 releases 失败", e)
            emptyList()
        }
    }

    private fun parseRelease(o: com.google.gson.JsonObject): UpdateRelease = parseReleaseObject(o)

    /** 仓库信息；失败返回 null */
    suspend fun fetchRepoInfo(): UpdateRepoInfo? {
        val json = getJson("/repos/$REPO") ?: return null
        return try {
            val o = JsonParser.parseString(json).asJsonObject
            val owner = o.getAsJsonObject("owner")
            UpdateRepoInfo(
                fullName = o.get("full_name")?.asString ?: REPO,
                description = o.get("description")?.asString ?: "",
                stars = o.get("stargazers_count")?.asInt ?: 0,
                ownerLogin = owner?.get("login")?.asString ?: "",
                ownerAvatar = owner?.get("avatar_url")?.asString ?: "",
                htmlUrl = o.get("html_url")?.asString ?: "https://github.com/$REPO"
            )
        } catch (e: Exception) {
            AppLogger.e("解析仓库信息失败", e)
            null
        }
    }

    /** 仓库主页（降级跳转用，不依赖网络） */
    const val REPO_URL = "https://github.com/$FEED_REPO"

    /** Gitee 镜像主页 */
    const val MIRROR_URL = "https://gitee.com/$MIRROR_REPO"
}
