package io.github.ranxian.muguang.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ranxian.muguang.model.ActiveOrder
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.NearbyDevice
import io.github.ranxian.muguang.model.WasherMode
import io.github.ranxian.muguang.ui.MainScreen
import io.github.ranxian.muguang.ui.MainViewModel
import io.github.ranxian.muguang.ui.ShowerScreen
import io.github.ranxian.muguang.ui.UserScreen
import io.github.ranxian.muguang.ui.WalletScreen
import io.github.ranxian.muguang.ui.WasherScreen
import io.github.ranxian.muguang.data.BoundWasherView
import io.github.ranxian.muguang.ui.design.StylePacks
import io.github.ranxian.muguang.ui.design.StyleId
import io.github.ranxian.muguang.ui.design.StyleState
import io.github.ranxian.muguang.ui.design.statusBarColor
import io.github.ranxian.muguang.ui.theme.LocalThemeMode
import io.github.ranxian.muguang.ui.theme.MuguangTheme
import io.github.ranxian.muguang.ui.theme.ThemeMode
import io.github.ranxian.muguang.utils.PrefsHelper

/**
 * debug 专用预览页：不登录也能看「使用页 / 洗衣机运行页 / 主页 / 钱包 / 我的」的实际配色。
 *
 * 三大主页用假数据（MainViewModel 的公开 state 直接赋值）：
 * 构造函数只读本地 prefs、不碰网络，断网截图也不会弹错误提示。
 * 正式包不含本 Activity（见 src/debug 的 manifest 合并）。
 *
 *   adb shell am start -n io.github.ranxian.muguang/.debug.PreviewActivity \
 *       --es screen home --ez dark false
 *
 * 风格由 shared_prefs/muguang_style.xml 决定，与正式入口完全一致。
 * ⚠️ 假数据写进本机 prefs（projectId=PREVIEW 等），只存在于模拟器的 debug 包里。
 */
class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsHelper.init(this)
        StyleState.load(this)
        // 模拟器已有真实登录会话：假数据 seeding 只允许发生在未登录时，
        // 否则会把真实 projectId / 余额 / 绑定寝室全部覆盖掉
        val loggedIn = PrefsHelper.isLoggedIn
        if (!loggedIn) seedFakeAccountPrefs()
        // --es style IOS|GPT|... 直接切换风格（写入偏好，与正式入口一致）
        intent.getStringExtra("style")?.let { StyleState.select(this, StyleId.fromKey(it)) }
        // --es screen repair --es tel <手机号>：修复被旧版假数据污染的会话字段
        // （只修 telephone 等可从已知事实恢复的项；projectId 走应用内「切换项目」从服务端恢复）
        if (intent.getStringExtra("screen") == "repair") {
            intent.getStringExtra("tel")?.let { PrefsHelper.telephone = it }
            PrefsHelper.userName = ""
            PrefsHelper.schoolName = ""
            PrefsHelper.boundRoom = ""
            PrefsHelper.campusBalance = ""
            PrefsHelper.walletBalance = ""
            PrefsHelper.lastDeviceName = ""
            PrefsHelper.lastDeviceSnCode = ""
            PrefsHelper.lastDeviceMac = ""
            PrefsHelper.lastDeviceEmoji = ""
            io.github.ranxian.muguang.data.ActiveOrderRepository.update { it.clear() }
            finish()
            return
        }
        val screen = intent.getStringExtra("screen") ?: "shower"
        val dark = intent.getBooleanExtra("dark", false)
        // 正式入口由 MainActivity 负责状态栏；预览页没有那段逻辑，
        // 不补的话状态栏会留着主题里的默认蓝色，截图会误判成「配色没换干净」。
        // 与 MainActivity 一致：预览截图里也不该出现系统三键
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val pack = StylePacks.of(StyleState.current, dark)
        window.statusBarColor = statusBarColor(pack).toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !pack.palette.isDark
        }
        setContent {
            val mode = remember { mutableStateOf(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            CompositionLocalProvider(LocalThemeMode provides mode) {
io.github.ranxian.muguang.ui.theme.CircularRevealThemeHost(
                    themeModeState = mode,
                    onThemeChanged = { m -> PrefsHelper.themeMode = m.name },
                ) {
                                MuguangTheme {
                    when (screen) {
                        "washer" -> WasherScreen(
                            device = demoWasher(),
                            mode = WasherMode(typeId = 1, typeName = "标准洗", times = 45),
                            stageText = "脱水",
                            elapsedSec = 23 * 60 + 18,
                            remainingSec = 21 * 60 + 42,
                            onMinimizeClick = {},
                        )
                        "home" -> MainTabsPreview(startTab = 0)
                        "wallet" -> MainTabsPreview(startTab = 1)
                        "user" -> MainTabsPreview(startTab = 2)
                        else -> ShowerScreen(
                            emoji = "🚿",
                            statusText = "正在沐浴中",
                            location = "龙川北苑-3号楼南-3层-320",
                            remaining = "12.50",
                            elapsedSec = 8 * 60 + 12,
                            onStopClick = {},
                        )
                    }
                }
                }
            }
        }
    }

    /** 主页三页签的完整壳（含底部胶囊导航），数据全是假的。 */
    @androidx.compose.runtime.Composable
    private fun MainTabsPreview(startTab: Int) {
        val vm: MainViewModel = viewModel()
        if (!PrefsHelper.isLoggedIn) fillFakeState(vm)
        var tab by remember { mutableIntStateOf(startTab) }
        Box(Modifier.fillMaxSize()) {
            // 必须与正式版一致：MainActivity 里 MainScreen 是满屏、胶囊浮在上面。
            // 之前这里给 MainScreen 让了 92dp，预览截图里抽屉把手的位置就比真机高一大截，
            // 拿它验收「把手会不会被胶囊压住」会得出相反结论。
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> MainScreen(phone = vm.phone, viewModel = vm)
                    1 -> WalletScreen(viewModel = vm)
                    else -> UserScreen(
                        phone = vm.phone,
                        onLogout = {},
                        viewModel = vm,
                        onSettingsVisibilityChanged = {},
                    )
                }
            }
            io.github.ranxian.muguang.ui.FloatingPillNavBar(
                currentTab = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    /**
     * 假账号写进 prefs：MainViewModel / 各页面的字段初始化器直接从 prefs 读，
     * 不经过网络。projectId 是余额等 project-scoped key 的前缀，必须先写。
     */
    private fun seedFakeAccountPrefs() {
        PrefsHelper.projectId = "PREVIEW"
        PrefsHelper.telephone = "13800000000"
        PrefsHelper.userName = "预览用户"
        PrefsHelper.schoolName = "预览大学"
        PrefsHelper.boundRoom = "龙川北苑-3号楼南-3层-320"
        PrefsHelper.campusBalance = "52.30"
        // 上次使用 / 活跃订单走生产层的持久化：initManagers 会从 prefs 恢复并
        // 清空内存列表，直接塞内存值会被冲掉，必须落在生产层
        PrefsHelper.lastDeviceName = "热水器-3F-320"
        PrefsHelper.lastDeviceMac = "AA:BB:CC:DD:EE:02"
        PrefsHelper.lastDeviceSnCode = "SN20241024000002"
        PrefsHelper.lastDeviceEmoji = "🚿"
        PrefsHelper.lastDeviceProjectId = PrefsHelper.projectId
        io.github.ranxian.muguang.data.ActiveOrderRepository.upsert(
            ActiveOrder(
                snCode = "SN20241024000003",
                orderNo = "PREVIEW-0001",
                deviceName = "热水器-3F-318",
                deviceMac = "AA:BB:CC:DD:EE:03",
                deviceEmoji = "🚿",
                preDeduct = 12.5,
                projectId = "PREVIEW",
            )
        )
    }

    private fun fillFakeState(vm: MainViewModel) {
        // ⚠️ 仅在未登录时调用（调用方已守卫）
        vm.phone = "13800000000"
        // 双机绑定形态：一台在跑（倒计时）、一台空闲，
        // 首页「我的洗衣机」卡片的两行布局就照这个验收
        val washerA = demoWasher()
        val washerB = demoWasher().copy(
            deviceName = "洗衣机-3F-05",
            snCode = "SN20241024000002",
            macAddress = "AA:BB:CC:DD:EE:02",
        )
        val now = System.currentTimeMillis()
        vm.previewSetBoundWashers(
            devices = listOf(washerA, washerB),
            views = mapOf(
                washerA.snCode to BoundWasherView(
                    statusLabel = "使用中",
                    stageText = "脱水",
                    remainingSec = 21 * 60 + 42,
                    isActive = true,
                    isOwner = true,
                    known = true,
                ),
                washerB.snCode to BoundWasherView(
                    statusLabel = "他人使用中",
                    stageText = "洗涤",
                    remainingSec = 18 * 60,
                    isActive = true,
                    isOwner = false,
                    known = true,
                    remainingIsEstimate = true,
                    orderPlacedAtMs = now - 6 * 60_000L,
                ),
            ),
            updatedAtMs = mapOf(washerA.snCode to now, washerB.snCode to now)
        )
        // 附近设备列表：名字必须能通过寝室筛选（matchesBoundRoom），否则全被滤掉
        vm.nearbyDevices.addAll(
            listOf(
                NearbyDevice("卫生间热水器-3层-320", "AA:BB:CC:DD:EE:03", -48),
                NearbyDevice("洗手台-3层-320", "AA:BB:CC:DD:EE:04", -63),
                NearbyDevice("洗衣机-3层-320", "AA:BB:CC:DD:EE:05", -71,
                    demoWasher().copy(deviceName = "洗衣机-3层-320", macAddress = "AA:BB:CC:DD:EE:05", snCode = "SN20241024000005")),
            )
        )
    }

    private fun demoWasher() = DeviceInfo(
        deviceId = 1,
        deviceName = "洗衣机-3F-02",
        snCode = "SN20241024000001",
        macAddress = "AA:BB:CC:DD:EE:01",
        withholdMoney = 3.0,
        onlineStatusId = 1,
        bigTypeId = 6,
        bigTypeName = "洗衣机",
    )
}
