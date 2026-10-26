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
 * **检查更新首选 GitHub**；不可达时自动兜底到 Gitee（见 [UpdateApi.fetchReleases]）。
 * 2026-10 国内网络实测：api.github.com 会整段连接重置，只靠 GitHub 的话
 * 检查更新永远静默失败——用户收不到任何新版本提示。
 */
private const val FEED_REPO = "Ranxian/muguang"

/**
 * Gitee 镜像仓库（同样 owner/repo）。
 *
 * 两个用途：
 * 1. **加速下载安装包**：GitHub 附件在大陆走 `objects.githubusercontent.com`，
 *    实测约 100 KB/s；Gitee 约 2 MB/s。
 * 2. **检查更新的兜底源**：GitHub API 连不上时改读 Gitee 发行版列表比版本号。
 *    Gitee 与 GitHub 的 tag / 附件名保持一致（发版流程保证），
 *    所以拿到哪个源的信息都能拼出可用的下载直链。
 */
private const val MIRROR_REPO = "dyed-string/muguang"

/** Gitee OpenAPI v5 根地址（国内可达性好于 api.github.com） */
private const val GITEE_API = "https://gitee.com/api/v5"

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
     *   https://gitee.com/<MIRROR_REPO>/releases/download/v1.1.0/muguang-1.1.0-arm64-v8a.apk
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
 * Gitee 发行版 JSON → [UpdateRelease]。
 *
 * 与 GitHub 的字段差异（实测 v5 响应）：
 * - 没有 `html_url` / `published_at`：页面地址用 tag 拼，时间取 `created_at`
 * - 附件同样有 `browser_download_url`，`size` 可能缺失（当 0 处理，仅展示用）
 *
 * 抽成顶层 internal 便于单测：解析错字段的用户侧表现是「发了新版却收不到提示」。
 */
internal fun parseGiteeRelease(json: String): UpdateRelease? =
    try {
        parseGiteeReleaseObject(JsonParser.parseString(json).asJsonObject)
    } catch (e: Exception) {
        AppLogger.e("解析 Gitee release 失败", e)
        null
    }

private fun parseGiteeReleaseObject(o: com.google.gson.JsonObject): UpdateRelease {
    val tag = o.get("tag_name")?.asString ?: ""
    return UpdateRelease(
        tagName = tag,
        name = o.get("name")?.asString ?: "",
        body = o.get("body")?.asString ?: "",
        publishedAt = o.get("created_at")?.asString ?: "",
        htmlUrl = o.get("html_url")?.asString
            ?: if (tag.isNotBlank()) "https://gitee.com/$MIRROR_REPO/releases/tag/$tag" else "",
        prerelease = o.get("prerelease")?.asBoolean ?: false,
        assets = o.getAsJsonArray("assets")?.mapNotNull { el ->
            val a = el.asJsonObject
            val url = a.get("browser_download_url")?.asString
            val name = a.get("name")?.asString
            if (url.isNullOrEmpty() || name.isNullOrEmpty()) null
            else UpdateAsset(name, url, a.get("size")?.asLong ?: 0L)
        } ?: emptyList()
    )
}

/**
 * 从列表里挑最新版本（**与列表顺序无关**）。
 *
 * ⚠️ Gitee 的 releases 接口按**旧 → 新**返回（实测新建 v1.1.1 后列表仍是
 * v1.0, v1.1, v1.1.1 的顺序），GitHub 才是新 → 旧。兜底路径不能拿
 * `firstOrNull()` 当最新——那会把最老的当成最新，永远提示不了更新。
 * 这里用版本号比较选最大，两个源的顺序差异都不影响。
 */
internal fun pickLatestRelease(releases: List<UpdateRelease>): UpdateRelease? {
    var best: UpdateRelease? = null
    for (r in releases) {
        if (r.prerelease || r.tagName.isBlank()) continue
        if (best == null || io.github.ranxian.muguang.utils.ApkUpdater.isNewer(r.tagName, best.tagName)) {
            best = r
        }
    }
    return best
}

/** 按版本号新 → 旧排序（调用方习惯拿 firstOrNull() 当最新） */
internal fun sortNewestFirst(releases: List<UpdateRelease>): List<UpdateRelease> =
    releases.sortedWith { a, b ->
        when {
            io.github.ranxian.muguang.utils.ApkUpdater.isNewer(a.tagName, b.tagName) -> -1
            io.github.ranxian.muguang.utils.ApkUpdater.isNewer(b.tagName, a.tagName) -> 1
            else -> 0
        }
    }

/**
 * 更新源 API 客户端（Release / 仓库信息）。
 *
 * 双源：GitHub 为主（[FEED_REPO]），Gitee 镜像为兜底（[MIRROR_REPO]）。
 * [FEED_REPO] 为空时视为未配置：全部方法返回 null/空，不发起任何网络请求。
 */
object UpdateApi {
    private const val BASE = "https://api.github.com"
    private const val REPO = FEED_REPO
    private const val GITEE_REPO = MIRROR_REPO

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

    /** Gitee API 请求；失败返回 null（与 [getJson] 同一套失败语义：静默降级） */
    private suspend fun getGiteeJson(path: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$GITEE_API$path")
                    .header("User-Agent", "Muguang-Android")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLogger.w("Gitee 更新源 ${resp.code} $path")
                        return@withContext null
                    }
                    resp.body?.string()
                }
            } catch (e: Exception) {
                AppLogger.w("Gitee 更新源请求失败 $path: ${e.message}")
                null
            }
        }

    /** 最新 Release；GitHub 失败时兜底 Gitee，两者都失败返回 null */
    suspend fun fetchLatestRelease(): UpdateRelease? {
        try {
            val json = getJson("/repos/$REPO/releases/latest")
            if (json != null) parseRelease(JsonParser.parseString(json).asJsonObject)?.let { return it }
        } catch (e: Exception) {
            AppLogger.e("解析 release 失败", e)
        }
        val giteeJson = getGiteeJson("/repos/$GITEE_REPO/releases/latest")
        if (giteeJson != null) {
            try {
                parseGiteeRelease(giteeJson)?.let { return it }
            } catch (e: Exception) {
                AppLogger.e("解析 Gitee release 失败", e)
            }
        }
        // /releases/latest 不可用（或解析失败）时回落到列表里挑最大版本
        return pickLatestRelease(fetchGiteeReleases())
    }

    /**
     * 全部 Release（最新在前，最多 10 条）；失败返回空列表。
     *
     * **滤掉预发布**：调用方拿 `firstOrNull()` 当"最新版"来比对，
     * 不过滤的话，只要发过一个标记为 pre-release 的测试版，所有人都会被提示更新到它。
     *
     * 先查 GitHub；GitHub 不可达（国内会整段连接重置）时改查 Gitee 镜像仓库。
     * 两个源的 tag 与附件名保持一致，所以无论拿到哪边的数据，下载直链都拼得出来。
     */
    suspend fun fetchReleases(): List<UpdateRelease> {
        val github = try {
            val json = getJson("/repos/$REPO/releases?per_page=10")
            json?.let {
                JsonParser.parseString(it).asJsonArray
                    .map { el -> parseRelease(el.asJsonObject) }
                    .filterNot { it.prerelease }
            }.orEmpty()
        } catch (e: Exception) {
            AppLogger.e("解析 releases 失败", e)
            emptyList()
        }
        if (github.isNotEmpty()) return github
        // Gitee 列表是旧→新，必须自己排一遍再交给「拿第一当最新」的调用方
        return sortNewestFirst(fetchGiteeReleases())
    }

    /** Gitee 镜像仓库的发行版列表（最新在前，滤掉预发布）；失败返回空列表 */
    private suspend fun fetchGiteeReleases(): List<UpdateRelease> {
        val json = getGiteeJson("/repos/$GITEE_REPO/releases?per_page=10&page=1") ?: return emptyList()
        return try {
            JsonParser.parseString(json).asJsonArray
                .mapNotNull { el -> parseGiteeRelease(el.toString()) }
                .filterNot { it.prerelease }
        } catch (e: Exception) {
            AppLogger.e("解析 Gitee releases 失败", e)
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
