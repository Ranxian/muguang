package io.github.ranxian.muguang.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实 GitHub Release 响应的形状验解析：字段名对不上、附件挑错、镜像链拼错，
 * 用户侧就是「发了新版收不到」或「点下载 404」。
 */
class UpdateApiParseTest {

    // 取自 GitHub Releases API 的真实结构（节选，字段名原样保留）
    private val releaseJson = """
        {
          "url": "https://api.github.com/repos/Ranxian/muguang/releases/1",
          "html_url": "https://github.com/Ranxian/muguang/releases/tag/v1.2.0",
          "tag_name": "v1.2.0",
          "name": "沐光 v1.2.0",
          "body": "首个公开版本",
          "prerelease": false,
          "published_at": "2026-10-25T08:00:00Z",
          "assets": [
            {
              "name": "muguang-1.2.0-arm64-v8a.apk.sha256",
              "size": 96,
              "browser_download_url": "https://github.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk.sha256"
            },
            {
              "name": "muguang-1.2.0-arm64-v8a.apk",
              "size": 19543058,
              "browser_download_url": "https://github.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `解析基础字段`() {
        val r = parseGithubRelease(releaseJson)!!
        assertEquals("v1.2.0", r.tagName)
        assertEquals("沐光 v1.2.0", r.name)
        assertEquals("首个公开版本", r.body)
        assertEquals("2026-10-25T08:00:00Z", r.publishedAt)
        assertFalse(r.prerelease)
    }

    @Test
    fun `apk 附件从多个附件里挑出来`() {
        val r = parseGithubRelease(releaseJson)!!
        assertEquals(2, r.assets.size)
        // 校验文件排在前面，不能被当成安装包
        assertEquals("muguang-1.2.0-arm64-v8a.apk", r.apkAsset?.name)
        assertEquals(19543058L, r.apkAsset?.sizeBytes)
    }

    @Test
    fun `下载优先镜像、回落原链`() {
        val r = parseGithubRelease(releaseJson)!!
        assertEquals(
            "https://gitee.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk",
            r.directApkUrl
        )
        assertEquals(
            "https://github.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk",
            r.originApkUrl
        )
    }

    @Test
    fun `没有附件时下载链为空`() {
        val r = parseGithubRelease("""{"tag_name":"v1.3.0","assets":[]}""")!!
        assertNull(r.apkAsset)
        assertNull(r.directApkUrl)
        assertNull(r.originApkUrl)
    }

    @Test
    fun `附件缺直链直接跳过`() {
        val r = parseGithubRelease(
            """{"tag_name":"v1.3.0","assets":[{"name":"x.apk","size":1}]}"""
        )!!
        assertTrue(r.assets.isEmpty())
        assertNull(r.apkAsset)
    }

    @Test
    fun `预发布标记要读出来`() {
        val r = parseGithubRelease("""{"tag_name":"v1.3.0-rc1","prerelease":true}""")!!
        assertTrue(r.prerelease)
    }

    @Test
    fun `畸形 JSON 不抛异常`() {
        assertNull(parseGithubRelease("{not json"))
        assertNull(parseGithubRelease("[]"))
    }

    /**
     * 线上真实响应的完整快照：v1.2.0 发布后从
     * `GET /repos/Ranxian/muguang/releases/latest` 原样抓下来的。
     *
     * 上面那份是手写的结构样例，这份带了几十个真实字段
     * （author / uploader / reactions / assets[].digest …）。解析器只要在真响应上
     * 挑错附件或拼错镜像链，用户侧就是「点下载 404」。
     */
    private fun realReleaseJson(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("github-release-v1.2.0.json")) {
            "缺少测试资源 github-release-v1.2.0.json"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `线上真实响应能挑出安装包并拼出镜像链`() {
        val r = parseGithubRelease(realReleaseJson())!!
        assertEquals("v1.2.0", r.tagName)
        assertFalse(r.prerelease)
        assertEquals("muguang-1.2.0-arm64-v8a.apk", r.apkAsset?.name)
        assertEquals(19543058L, r.apkAsset?.sizeBytes)
        assertEquals(
            "https://gitee.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk",
            r.directApkUrl
        )
        assertEquals(
            "https://github.com/Ranxian/muguang/releases/download/v1.2.0/muguang-1.2.0-arm64-v8a.apk",
            r.originApkUrl
        )
    }

    @Test
    fun `刚发布的这一版对 1_2_0 用户不算新`() {
        // 装上 v1.2.0 之后点「检查更新」必须看到「已是最新」；
        // 这条把真实 tag 和包内 versionName 对上了，发版后才不会自己提示自己
        val r = parseGithubRelease(realReleaseJson())!!
        assertFalse(io.github.ranxian.muguang.utils.ApkUpdater.isNewer(r.tagName, "1.2.0"))
    }
}
