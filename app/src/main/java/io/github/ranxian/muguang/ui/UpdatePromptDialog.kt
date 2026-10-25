package io.github.ranxian.muguang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.api.UpdateApi
import io.github.ranxian.muguang.api.UpdateRelease
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.utils.ApkDownloadState
import io.github.ranxian.muguang.utils.ApkInstallResult
import io.github.ranxian.muguang.utils.ApkUpdater

/**
 * 启动后检测到新版本时的提示框。
 *
 * 设计上只做一件事：把「有新版本」和「装它」之间的步骤压到最短。
 * 用户点「立即更新」就地下载、就地安装，不必先翻到设置页再点一遍。
 *
 * 三条状态纪律（和设置页那张卡片一致）：
 * - 进度只在同一状态分支内变化，不来回跳页面
 * - 缺「安装未知应用」权限时给明确出口，不静默失败
 * - 「稍后」只是关掉，下次启动还会提醒；「跳过此版本」才真正闭嘴
 */
@Composable
fun UpdatePromptDialog(
    release: UpdateRelease,
    onDismiss: (skipVersion: Boolean) -> Unit
) {
    val context = LocalContext.current
    val state = ApkUpdater.state
    var installError by remember { mutableStateOf<String?>(null) }

    val asset = release.apkAsset
    val downloadUrl = if (UpdateApi.configured) release.directApkUrl else null

    fun install(file: java.io.File) {
        installError = when (val r = ApkUpdater.installApk(context, file)) {
            is ApkInstallResult.Error -> r.message
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        title = { Text("发现新版本 ${release.tagName}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "当前版本 ${ApkUpdater.currentVersion} · 新版本可直接在应用内安装",
                    color = AppColors.TextSecondary, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))

                when {
                    state is ApkDownloadState.Running -> {
                        val pct = state.percent
                        Text(
                            if (pct >= 0) "正在下载 $pct%" else "正在下载…",
                            color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        if (pct >= 0) {
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = AppColors.Accent, trackColor = AppColors.Border
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = AppColors.Accent, trackColor = AppColors.Border
                            )
                        }
                    }

                    state is ApkDownloadState.Done -> {
                        Text(
                            "更新包已下载完成", color = AppColors.Success,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "点「立即安装」会拉起系统安装器。若提示需要允许「安装未知应用」，" +
                                "开完之后回到这里仍然是这一步，不用重新下载。",
                            color = AppColors.TextSecondary, fontSize = 12.sp
                        )
                    }

                    state is ApkDownloadState.Failed -> {
                        Text(state.message, color = AppColors.Warning, fontSize = 12.sp)
                    }

                    else -> {
                        // 默认形态：只展示更新日志的前几行，太长会顶掉按钮。
                        // Release 正文是 Markdown，弹窗里不渲染，先把标记剥掉再显示，
                        // 否则用户看到的是 `>` 和反引号。
                        val lines = release.body.trim().lineSequence()
                            .map { it.trim().removePrefix(">").trim() }
                            .map { it.replace("**", "").replace("`", "") }
                            .filter { it.isNotEmpty() }
                            .take(8)
                            .toList()
                        if (lines.isNotEmpty()) {
                            Text(
                                lines.joinToString("\n"),
                                color = AppColors.TextSecondary, fontSize = 12.sp
                            )
                        }
                    }
                }

                installError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = AppColors.Warning, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            when {
                state is ApkDownloadState.Running -> TextButton(onClick = { ApkUpdater.cancel() }) {
                    Text("取消下载", color = AppColors.TextSecondary)
                }

                state is ApkDownloadState.Done -> TextButton(onClick = { install(state.file) }) {
                    Text("立即安装", color = AppColors.Success)
                }

                // 走到这里只剩 Idle / Failed 两种（上面已拦掉 Running、Done）
                downloadUrl != null && asset != null -> TextButton(onClick = {
                    installError = null
                    ApkUpdater.start(
                        context = context,
                        url = downloadUrl,
                        fileName = asset.name,
                        expectedSize = asset.sizeBytes,
                        releaseTag = release.tagName,
                        fallbackUrl = release.originApkUrl
                    )
                }) {
                    Text(if (state is ApkDownloadState.Failed) "重新下载" else "立即更新", color = AppColors.Accent)
                }

                else -> TextButton(onClick = { onDismiss(false) }) {
                    Text("稍后", color = AppColors.TextSecondary)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = { onDismiss(true) }) {
                    Text("跳过此版本", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            }
        }
    )
}
