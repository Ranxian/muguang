package io.github.ranxian.muguang.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.Font
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import io.github.ranxian.muguang.ui.design.CubeSpinner
import io.github.ranxian.muguang.ui.design.RadarProbe
import io.github.ranxian.muguang.ui.design.WashingMachineLoader
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.res.painterResource
import io.github.ranxian.muguang.QrScanActivity
import io.github.ranxian.muguang.R
import io.github.ranxian.muguang.data.BalanceEstimator
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.NearbyDevice
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.utils.BackgroundManager
import io.github.ranxian.muguang.utils.BackgroundState
import io.github.ranxian.muguang.utils.PrefsHelper
import io.github.ranxian.muguang.utils.ScanPermission
import io.github.ranxian.muguang.utils.MoneyFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MainScreen(phone: String, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    // 是否已获得扫描权限。做成状态：用户授权后要立刻反映到界面上
    var hasScanPerm by remember { mutableStateOf(ScanPermission.granted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val ok = perms.entries.all { it.value }
        hasScanPerm = ok
        if (ok) viewModel.startScan() else viewModel.toastMessage = ScanPermission.deniedMessage()
    }

    // 扫描前才要权限：已授权直接扫，没授权才弹系统对话框。
    // 这样 Android 12+ 的用户全程只会看到「附近的设备」，不会看到定位
    fun scanWithPermission() {
        if (ScanPermission.granted(context)) viewModel.startScan()
        else permissionLauncher.launch(ScanPermission.required)
    }

    // 扫码绑定设备
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val sn = result.data?.getStringExtra(QrScanActivity.EXTRA_SN_CODE)
            if (!sn.isNullOrEmpty()) {
                viewModel.scanBind(sn)
            } else {
                viewModel.toastMessage = "未识别到有效二维码"
            }
        } else {
            // 扫码页自己弹的说明（没相机权限、相机起不来）挂在 error extra 上。
            // 以前这里只看 RESULT_OK，这些消息全被丢掉了——用户看到的就是
            // 点了扫码、黑屏一闪、回到主页，什么提示都没有。
            result.data?.getStringExtra(QrScanActivity.EXTRA_ERROR)
                ?.takeIf { it.isNotEmpty() }
                ?.let { viewModel.toastMessage = it }
        }
    }

    // 绑定洗衣机的状态轮询：使用中 5 秒一次（剩余时间要准），空闲 15 秒一次。
    // snCode 变化（换绑定/解绑）时 LaunchedEffect 自动取消旧循环。
    LaunchedEffect(viewModel.boundWasher?.snCode) {
        val sn = viewModel.boundWasher?.snCode ?: return@LaunchedEffect
        while (true) {
            viewModel.refreshBoundWasher()
            delay(if (viewModel.boundWasherView?.isActive == true) 5_000L else 15_000L)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWallet()
        viewModel.loadBills()
        // 余额和学校名是账号级的，跟哪个页面无关。以前只在「我的」页面拉，
        // 结果先进主页/钱包页的时候 `campusBalance` 还是 null——界面就当拿不到
        // 真实余额，继续显示「手动填写」和「并非真实余额」。三个入口都拉一次。
        viewModel.loadCampusBalance()
        viewModel.loadSchoolName()
        // 未支付账单也要在这里拉一次：开阀失败弹窗的「立即补扣」靠它。
        // 只在钱包页拉的话，冷启动直接开阀失败时列表是空的，
        // 弹窗会退化成只有「知道了」——正好把这个功能的主要场景漏掉
        viewModel.loadUnpaidBills()
        viewModel.loadAvailableProjects()
        viewModel.initManagers(context)
        // ⚠️ 不再进入首页就自动扫描：扫描改由「探测雷达」点按显式触发
        // （用户要求 + 平台对无操作 BLE 扫描的限制）。刷新按钮仍保留手动触发。
        visible = true
    }

    LaunchedEffect(viewModel.projectRevision) {
        if (viewModel.projectRevision > 0) scanWithPermission()
    }

    var pullRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.pullRefresh()
            scanWithPermission()
            // 1秒后隐藏顶部指示器
            scope.launch {
                kotlinx.coroutines.delay(1000)
                pullRefreshing = false
            }
        }
    )

    // 按寝室筛选设备：用 remember 缓存，仅当设备列表或绑定寝室变化时才重新过滤
    val filteredDevices = remember(
        viewModel.nearbyDevices.toList(),
        // ⚠️ 用 ViewModel 里的状态，不是 PrefsHelper.boundRoom。
        // 读 pref 不会触发重组，取消绑定后列表不会立刻更新（要等下一次扫描）。
        viewModel.boundRoom
    ) {
        viewModel.nearbyDevices.filter {
            val n = it.deviceInfo?.deviceName ?: it.name
            viewModel.matchesBoundRoom(n)
        }
    }

    // 余额：接口拿得到就用真实的，拿不到才回退估算。含日期解析，开销较大，
    // 所以放在 LazyColumn 之外并用 remember 缓存，避免滚动时 item 反复组合/销毁
    // 导致重新解析日期而掉帧。
    // 账单没到位时返回 null：那时候减数为 0，算出来的就是用户填的初始余额，
    // 直接显示出来会先亮一个错数字再跳变。宁可先显示「—」——
    // 但真实余额不用等账单，它是现成的数字。
    val displayBalance = remember(
        viewModel.billList, viewModel.billsLoaded, viewModel.campusBalance,
        viewModel.walletBalance,
        PrefsHelper.manualBalance, PrefsHelper.manualBalanceTime
    ) {
        // 真实余额（一卡通或钱包）不用等账单，是现成的数字；
        // 只有要走估算时才必须等账单到位，否则减数为 0 会先亮一个错数字。
        if (viewModel.campusBalance != null || viewModel.walletBalance != null || viewModel.billsLoaded)
            BalanceEstimator.estimate(viewModel.billList)
        else null
    }

    // ── 新一版主页（P1）：照片背景 + Rocksteady 眉题 + 行书 hero + 玻璃余额卡 + 推拉抽屉 ──
    // 正文 LXGW WenKai（子集化打包），标题行书 Zhi Mang Xing；照片背景走默认首页背景，
    // 用户套用自定义背景时退回透明（沿用 BackgroundManager 通道）。
    val homeBody = FontFamily(Font(R.font.lxgw_wen_kai_regular))
    val homeHero = FontFamily(Font(R.font.zhi_mang_xing_regular))
    val rocksteadyFamily = FontFamily(Font(R.font.rocksteady_regular))
    val heroInk = Color(0xFFF5F5F7)
    val heroSub = Color(0xB3F5F5F7)
    // 玻璃卡里的字：白 72% 的卡面在照片上最暗也只到 ~#7A7A7A、最亮到纯白，
    // 白字压上去在雪山上只有 1.4:1，所以卡面改「白霜玻璃 + 深墨」——
    // 两个极端下深墨都有 ≥9:1，和底部抽屉的白霜材质也统一。
    val cardInk = Color(0xFF1D1D1F)
    val cardInkSub = Color(0xFF4A4A4F)
    val useCustomBg = BackgroundState.config(BackgroundManager.SCOPE_HOME).enabled
    val washerStatusLine = when {
        viewModel.activeWasherSession != null -> "运行中 · ${viewModel.washerStageText.ifBlank { "进行中" }}"
        viewModel.boundWasher != null -> viewModel.boundWasherView?.statusLabel ?: "状态未知"
        else -> "未绑定"
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                // 抽屉靠 translationY 收起，而 graphicsLayer 不会裁剪：
                // 没有这层裁剪，收起后把手以下的抽屉内容会溢到容器外（导航条那一条），
                // 看上去就是「抽屉没拉开也能看到内部」
                .clipToBounds()
                .background(if (useCustomBg) Color.Transparent else Color.Black)
        ) {
            if (!useCustomBg) {
                Image(
                    painter = painterResource(R.drawable.bg_home_road),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0xC2000000),
                                0.30f to Color(0x57000000),
                                0.55f to Color(0x24000000),
                                1f to Color(0x8C000000)
                            )
                        )
                )
            }
            if (viewModel.isShowering) {
                val washer = viewModel.activeWasherSession
                if (washer != null) {
                    WasherScreen(
                        device = washer.device,
                        mode = washer.mode,
                        stageText = viewModel.washerStageText,
                        elapsedSec = viewModel.showerElapsedSec,
                        remainingSec = viewModel.washerRemainingSec,
                        onMinimizeClick = { viewModel.minimizeShower() }
                    )
                } else {
                    ShowerScreen(
                        emoji = viewModel.selectedDevice?.typeEmoji ?: "🚿",
                        statusText = viewModel.selectedDevice?.statusText ?: "正在沐浴中",
                        location = viewModel.selectedDevice?.locationOnly ?: "",
                        remaining = viewModel.showerRemaining,
                        elapsedSec = viewModel.showerElapsedSec,
                        autoDisConSec = viewModel.autoDisConSec,
                        isStopping = viewModel.isStopping,
                        onStopClick = { viewModel.stopShower() },
                        onMinimizeClick = { viewModel.minimizeShower() }
                    )
                }
            } else {
                // 抽屉必须是滚动内容之外的兄弟节点：塞进 verticalScroll 的 Column 里时，
                // 它的 height(440.dp) 会被「剩余高度」压缩（实测只剩 355dp），
                // 而收起位移是按 440dp 常量算的 —— 两者对不上，收起后会露出半截内容。
                // 钉在 Box 底部后高度就是确定的 440dp，位移与高度一一对应。
                Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                ) {
                    Spacer(Modifier.height(8.dp))
                    // ── 品牌行：Rocksteady 眉题 + 扫码 / 刷新 ──
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "沐光 · MUGUANG",
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 2.5.sp,
                            fontFamily = rocksteadyFamily,
                            color = heroInk
                        )
                        RoundNavBtn(
                            contentDescription = "扫码绑定设备",
                            iconRes = R.drawable.ic_qr_code_scanner,
                            tint = heroInk,
                        ) {
                            scanLauncher.launch(Intent(context, QrScanActivity::class.java))
                        }
                        // 钮的可点区域 48dp、玻璃圆面仍是 40dp，间距用 2dp 补回原来的 10dp 视觉间隔
                        Spacer(Modifier.width(2.dp))
                        RoundNavBtn(
                            contentDescription = "刷新设备列表",
                            iconVector = Icons.Default.Refresh,
                            tint = heroInk,
                        ) {
                            viewModel.pullRefresh()
                            scanWithPermission()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // ── 行书 hero ──
                    Text(
                        "校园热水，",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontSize = 42.sp,
                        lineHeight = 50.sp,
                        fontFamily = homeHero,
                        color = heroInk
                    )
                    Text(
                        "一切尽在掌握。",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontSize = 42.sp,
                        lineHeight = 50.sp,
                        fontFamily = homeHero,
                        color = heroInk
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${viewModel.currentProjectName} · 已连接",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        fontSize = 13.sp,
                        fontFamily = homeBody,
                        color = heroSub
                    )
                    Spacer(Modifier.height(18.dp))
                    // ── 玻璃余额卡 ──
                    Box(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "我的资产 · 趣智校园实时余额",
                                    fontSize = 10.sp,
                                    fontFamily = homeBody,
                                    color = cardInkSub
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                displayBalance?.let { "¥%.2f".format(it) } ?: "¥ —",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cardInk
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("洗衣机 · $washerStatusLine", fontSize = 11.sp, fontFamily = homeBody, color = cardInkSub)
                                Text("进行中订单 ${viewModel.activeOrders.size}", fontSize = 11.sp, fontFamily = homeBody, color = cardInkSub)
                            }
                        }
                    }
                    // 扫码绑定入口统一收敛到右上角扫码钮（按钮已移除，避免重复入口）
                    // ── 寝室筛选状态 ──
                    if (viewModel.hasBoundRoom) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.72f))
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🏠 已筛选：${DeviceInfo.roomKey(viewModel.boundRoom) ?: viewModel.boundRoom}",
                                color = cardInk, fontSize = 12.sp, fontFamily = homeBody,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(4.dp))
                            // 触控下限 48dp：字仍是 12sp，靠外层让可点高度够
                            Box(
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 8.dp)
                                    .clickable {
                                        viewModel.applyBoundRoom("")
                                        viewModel.toastMessage = "已取消寝室筛选"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "取消筛选",
                                    fontSize = 12.sp, fontFamily = homeBody, color = cardInkSub
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(120.dp))
                }

                // ── 推拉抽屉：附近设备（真实数据 + 搜索过滤） ──
                P1HomeDrawer(
                    viewModel = viewModel,
                    devices = filteredDevices,
                    hasScanPerm = hasScanPerm,
                    balanceText = displayBalance?.let { "¥%.2f".format(it) } ?: "¥ —",
                    onScanStart = { scanWithPermission() },
                    onScanQr = { scanLauncher.launch(Intent(context, QrScanActivity::class.java)) },
                    onOpenDevice = { device -> viewModel.fetchDeviceInfo(device.mac) },
                    onOpenProject = {
                        viewModel.loadAvailableProjects()
                        showProjectPicker = true
                    },
                    lastDeviceLine = viewModel.lastDeviceName.takeIf { it.isNotEmpty() },
                )
                }
            }
        }
    }

    if (viewModel.showDeviceDetail) {
        val isActive = viewModel.selectedDevice?.snCode?.let { viewModel.isDeviceActive(it) } ?: false
        val selected = viewModel.selectedDevice
        DeviceDetailDialog(
            selected,
            isActive,
            viewModel.isOwner,
            isBoundWasher = selected?.snCode?.let { viewModel.isBoundWasher(it) } ?: false,
            onDismiss = { viewModel.showDeviceDetail = false },
            onConfirm = { viewModel.startShower(phone) },
            onBindWasher = { viewModel.selectedDevice?.let { viewModel.bindWasher(it) } },
            onUnbindWasher = { viewModel.unbindWasher() }
        )
    }

    viewModel.washerModePickerDevice?.let { device ->
        WasherModeDialog(
            device = device,
            modes = viewModel.washerModes,
            loading = viewModel.isLoadingWasherModes || viewModel.isStartingShower,
            busyText = if (viewModel.isStartingShower) "正在启动洗衣机…" else "正在读取可用模式…",
            onStart = { mode -> viewModel.startWasherMode(mode) },
            onDismiss = { viewModel.dismissWasherModes() }
        )
    }

    if (showProjectPicker) {
        ProjectPickerDialog(
            projects = viewModel.availableProjects,
            currentProjectId = viewModel.currentProjectId,
            loading = viewModel.projectsLoading,
            switching = viewModel.projectSwitching,
            onRetry = { viewModel.loadAvailableProjects() },
            onSelect = { project ->
                viewModel.switchProject(project)
                showProjectPicker = false
            },
            onDismiss = { showProjectPicker = false }
        )
    }

    // ── 开阀失败 ──
    //
    // 以前 `showerError` 是**只写不读**的：失败时用户只看到「正在开启热水器…」消失，
    // 然后什么都不发生，完全不知道是失败了还是没点上。
    viewModel.showerError?.let { err ->
        // ⚠️ **只有服务端明确拒绝**才可能和欠费有关。网络异常、设备被占用、结果未知
        // 都跟账单无关——把它们也引导到「补扣」，用户会因为一次 WiFi 掉线去白扣钱
        val bills = if (err.kind == ShowerErrorKind.SERVER_REJECTED) viewModel.deductibleBills
                    else emptyList()

        // 金额是服务端给的**字符串**，解析不出来时**不能悄悄按 0 算**：
        // 这张弹窗存在的唯一目的就是「会动钱，再停一下」，数字失真就失去意义了
        val amounts = bills.map { it.consumeMoney?.toDoubleOrNull() }
        val total = amounts.sumOf { it ?: 0.0 }
        val amountKnown = amounts.all { it != null }

        // key 带上 err：错误换了一条时确认态要跟着复位，不然「确认扣款」会
        // 叠在一条新错误上
        var confirming by remember(err) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.clearShowerError() },
            title = {
                Text(
                    when {
                        err.kind == ShowerErrorKind.WASHER_REJECTED ||
                            viewModel.washerModePickerDevice != null -> "洗衣机启动失败"
                        bills.isEmpty() -> "开阀失败"
                        else -> "开阀失败 · 有未扣账单"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(err.message, color = AppColors.TextPrimary, fontSize = 14.sp)
                    if (bills.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("检测到 ${bills.size} 笔没扣成功的账单：",
                            color = AppColors.Warning, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        bills.forEach { bill ->
                            Text(
                                "· ¥${bill.consumeMoney ?: "?"}   ${bill.consumeDate?.take(16) ?: ""}",
                                color = AppColors.TextSecondary, fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("账户有欠费时服务端会拒绝开阀",
                            color = AppColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                if (bills.isNotEmpty()) {
                    Button(
                        onClick = { confirming = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning)
                    ) { Text("立即补扣") }
                } else {
                    TextButton(onClick = { viewModel.clearShowerError() }) { Text("知道了") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearShowerError() }) { Text("关闭") }
            }
        )

        // 扣钱前的二次确认（和钱包页单笔代扣共用同一个组件，文案不会各写一份）
        if (confirming) {
            DeductConfirmDialog(
                amount = if (amountKnown) total else null,
                detail = "共 ${bills.size} 笔",
                onConfirm = {
                    confirming = false
                    viewModel.clearShowerError()
                    viewModel.deductAllUnpaid { s ->
                        viewModel.toastMessage = when {
                            // null = 一笔都没发出去（没有可补的，或被另一个代扣挡住）
                            s == null -> "没有可补扣的账单，或正在处理另一笔代扣"
                            s.unknown > 0 && s.ok == 0 && s.failed == 0 ->
                                "补扣结果未知，请下拉刷新确认后再操作"
                            s.unknown > 0 ->
                                "成功 ${s.ok} 笔、失败 ${s.failed} 笔、${s.unknown} 笔结果未知"
                            s.failed == 0 -> "补扣成功，再试一次开阀"
                            s.ok == 0 -> s.firstFailReason ?: "补扣失败，请到「钱包」页重试"
                            else -> "成功 ${s.ok} 笔、失败 ${s.failed} 笔"
                        }
                    }
                },
                onDismiss = { confirming = false }
            )
        }
    }

    // 开始使用中的加载提示（开阀确认需要几秒）
    if (viewModel.isStartingShower && viewModel.washerModePickerDevice == null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(
                if (viewModel.washerModePickerDevice != null) "正在启动洗衣机..." else "正在开启热水器...",
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = AppColors.Accent)
                    Spacer(Modifier.width(12.dp))
                    // 蓝牙表那条链路要好几步（连接设备 → 读状态帧 → 下费率 → 写费率包），
                    // 比 4G 表慢得多；给具体进度，别让用户以为卡死了
                    Text(
                        viewModel.bleProgress ?: "正在确认设备是否开启，请稍候",
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // 云端开阀回了「设备不在线」——问用户要不要改用手机蓝牙。
    //
    // 为什么必须问用户：服务端**不提供**任何能区分「云端表 / 蓝牙表」的字段
    // （`smallTypeId` 两校的 4G 表也都是 1；`isBle` 压根不返回），
    // 参考项目也没做这个判断。只有站在设备旁的人才知道答案。
    viewModel.bleRetryDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissBleRetry() },
            title = {
                Text(
                    "要用蓝牙方式开启吗？",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "「${dev.displayName}」无法通过云端开启。\n\n" +
                        "如果这台设备需要手机蓝牙直连（开启时必须待在设备旁边），" +
                        "可以改用蓝牙方式。\n\n" +
                        "不确定的话：用官方「趣智校园」试一下——" +
                        "需要站到设备旁才能开的就是这一类。",
                    color = AppColors.TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmBleRetry() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("用蓝牙开启") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBleRetry() }) { Text("取消") }
            }
        )
    }

    // 自动关停确认弹窗
    if (viewModel.showAutoCloseDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("⚠️ 热水器已自动关闭", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚿 ", fontSize = 16.sp)
                        Text(viewModel.autoCloseDeviceName, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    val sec = viewModel.autoCloseElapsed
                    val t = if (sec / 60 > 0) "${sec / 60}分${sec % 60}秒" else "${sec}秒"
                    Text("⏱ 已用 $t", color = AppColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    if (viewModel.autoCloseLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                            Spacer(Modifier.width(6.dp))
                            Text("结算中...", color = AppColors.TextSecondary)
                        }
                    } else {
                        // 金额可能是 null（结算没拿到）——那是「还不知道」，
                        // 不能显示成 ¥0.00，否则用户以为没花钱
                        Text(
                            viewModel.autoCloseConsumed?.let { "本次消费：${MoneyFormat.withSymbol(it)}" }
                                ?: "消费金额稍后可在账单中查看",
                            fontWeight = FontWeight.Bold, color = AppColors.Accent
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmAutoClose() },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("确 认")
                }
            },
            dismissButton = {}
        )
    }
}

// ─────────────────────────────────────────────────────────────
// P1 主页私有组件
// ─────────────────────────────────────────────────────────────

/**
 * 圆形玻璃图标钮（品牌行右侧：扫码 / 刷新）。
 *
 * 可点区域 48dp（触控下限），玻璃圆面仍是 40dp——视觉尺寸不变，只把热区撑到规范值。
 */
@Composable
private fun RoundNavBtn(
    contentDescription: String,
    iconRes: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painterResource(iconRes), contentDescription,
                    tint = tint, modifier = Modifier.size(18.dp)
                )
            } else if (iconVector != null) {
                Icon(
                    iconVector, contentDescription,
                    tint = tint, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 推拉抽屉：附近设备（真实数据 + 搜索过滤 + 洗衣机状态行）。
 * 收起 = 只露把手；展开 = 560dp 光感毛玻璃面板。拖拽把手或点按均可。
 */
@Composable
private fun P1HomeDrawer(
    viewModel: MainViewModel,
    devices: List<NearbyDevice>,
    hasScanPerm: Boolean,
    balanceText: String,
    onScanStart: () -> Unit,
    onScanQr: () -> Unit,
    onOpenDevice: (NearbyDevice) -> Unit,
    onOpenProject: () -> Unit,
    lastDeviceLine: String?,
) {
    val homeBody = FontFamily(Font(R.font.lxgw_wen_kai_regular))
    // 收起深度 = 抽屉高度 − 把手高度。
    // ⚠️ 用已知的 dp 常量直接换算，不再靠 onSizeChanged 实测：实测值在首帧
    // 约束未稳定时会偏小，导致「收起」只移了一半，抽屉内容露在导航栏后面。
    val drawerH = 440.dp
    val handleH = 52.dp
    // 收起后的把手必须浮在底部导航胶囊之上，否则根本抓不住：
    // 胶囊是 fillMaxWidth + navigationBarsPadding()（MainActivity 已隐藏系统三键，实测为 0）
    // + padding(bottom = 22.dp)，内部 42dp 滑块加 8dp 内边距 = 50dp 高，
    // 也就是它占住屏幕底部 22…72dp 这一段 —— 正好压在 0…52dp 的把手上，
    // 连 38×5dp 的抓握条都被遮住了。抬 84dp（胶囊顶端 72 + 12 间隙）后
    // 把手落在 84…136dp，胶囊完整露在下方。
    val navLift = 84.dp
    val collapsedOffset = with(LocalDensity.current) { (drawerH - handleH).toPx() }
    var offsetPx by remember(collapsedOffset) { mutableFloatStateOf(collapsedOffset) }
    // sheet 是白 80% 叠在照片 + 32% 黑遮罩上，最浅会到 #E8E8EA 附近。
    // 次级文字按这个最浅底色验收：原来的 #6E6E73 只有 4.14:1 且实测更浅时跌到 3.x，
    // 换成 #4A4A4F 后是 7.20:1（纯白上 8.81:1），视觉层级不变。
    val sheetInk = Color(0xFF1D1D1F)          // 主文字 13.75:1
    val sheetInkSecondary = Color(0xFF4A4A4F) // 次级文字 7.20:1
    val sheetChevron = Color(0xFF7C7C82)      // › 非文字图形 3.39:1（≥3:1）
    val sheetLink = Color(0xFF0066CC)         // 链接文字 4.55:1（原 #0071E3 仅 3.84:1）
    val sheetIcon = Color(0xFF0071E3)         // 图标 3.84:1（图形 ≥3:1）
    // 收起深度 = sheet 实测高度 − 把手高度：收起只露把手，内部内容不可见
    val scope = rememberCoroutineScope()
    var dragged = false
    // 抽屉从「收」跨到「开」时 +1：驱动雷达自动转三圈并触发一次真实扫描
    var openCount by remember { mutableStateOf(0) }
    var wasOpen by remember { mutableStateOf(false) }

    fun animateTo(target: Float) {
        scope.launch {
            val anim = Animatable(offsetPx)
            anim.animateTo(
                target,
                tween(420, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                block = { offsetPx = this.value }
            )
        }
    }

    val isOpen = offsetPx < collapsedOffset / 2
    LaunchedEffect(isOpen, collapsedOffset) {
        if (collapsedOffset <= 0f) return@LaunchedEffect
        if (isOpen && !wasOpen) openCount++
        wasOpen = isOpen
    }

    Box(Modifier.fillMaxSize()) {
        if (isOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { animateTo(collapsedOffset) }
            )
        }
        // 给底部胶囊让位的裁切窗口：sheet 只能出现在 u ≥ 84dp 的区域。
        // 只把面板整体上浮还不够——面板下沿低于让位线时，那一截仍会露出内部行；
        // 裁掉之后收起态就只剩把手上沿，正好停在胶囊上方 12dp。
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = navLift)
                .clipToBounds()
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(drawerH)
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = offsetPx }
                // 四角同半径：底部不再贴边，下方要露出胶囊，做成悬浮面板才不会像被切掉
                .clip(RoundedCornerShape(26.dp))
                // 必须是实色：半透明白（试过 0.80 / 0.96）会透出主页的余额卡，
                // 拉开抽屉时「¥52.30」「已筛选…」和自己的行叠在一起，两套字互相干扰。
                // 玻璃感由身后 32% 的黑遮罩提供，面板本身按 iOS 的实心 sheet 处理
                .background(Color.White)
                // 内容离面板底沿留 16dp，最后一行不贴边
                .padding(bottom = 16.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragged = true
                        offsetPx = (offsetPx + delta).coerceIn(0f, collapsedOffset)
                    },
                    onDragStarted = { dragged = false },
                    onDragStopped = {
                        // 整面可拖：上拉开 / 下拉近；松手按过半判定归位
                        if (dragged) animateTo(if (offsetPx < collapsedOffset / 2) 0f else collapsedOffset)
                    }
                )
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(handleH)
                    .clickable { if (!dragged) animateTo(if (isOpen) collapsedOffset else 0f) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFC7C7CC))
                )
            }
            // 把手留在滚动区外：拖拽手势始终从把手起手，内容区只负责滚动
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 探测行：雷达 + 「探测」标题左对齐一行；展开抽屉自动转三圈并触发扫描
                if (collapsedOffset - offsetPx > 120f) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadarProbe(
                            scanning = viewModel.isScanning,
                            onProbeStart = onScanStart,
                            size = 22.dp,
                            autoSpinKey = openCount,
                            // 右边这行标题已经写了状态，雷达底下再挂一行会重复
                            showCaption = false,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (viewModel.isScanning) "探测中…" else "探测附近设备",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = homeBody,
                            color = sheetInk
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        // 触控下限 48dp：文字仍是 12sp，只把可点高度撑够
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onOpenProject)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前项目", fontSize = 12.sp, fontFamily = homeBody, color = sheetInkSecondary)
                    Text(viewModel.currentProjectName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sheetInk)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("趣智校园余额", fontSize = 12.sp, fontFamily = homeBody, color = sheetInkSecondary)
                    Text(balanceText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = sheetInk)
                }
                // 「我的设备」meta 行：与「当前项目」「趣智校园余额」同构，
                // 点按 → 已绑定开管理弹窗，未绑定走扫码绑定
                val washerBound = viewModel.boundWasher
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            val bound = viewModel.boundWasher
                            if (bound != null) {
                                viewModel.selectedDevice = bound
                                viewModel.showDeviceDetail = true
                            } else {
                                onScanQr()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 用户提供的洗衣机图标（Shoh2008 loader 改绘）：有进行中订单才转
                    WashingMachineLoader(
                        running = viewModel.activeWasherSession != null,
                        size = 34.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("我的设备", fontSize = 12.sp, fontFamily = homeBody, color = sheetInkSecondary, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            viewModel.activeWasherSession != null -> "使用中 · ${viewModel.washerStageText.ifBlank { "运行中" }}"
                            washerBound != null -> washerBound.deviceName
                            else -> "未绑定 · 点按绑定"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = homeBody,
                        color = if (washerBound == null) sheetLink else sheetInk
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .padding(horizontal = 22.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x14000000))
                )
                if (!hasScanPerm) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onScanStart)
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍", fontSize = 15.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("开启扫描，发现附近设备", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = sheetInk)
                            Text("授予「附近的设备」权限即可开始", fontSize = 11.sp, color = sheetInkSecondary)
                        }
                    }
                }
                if (viewModel.isScanning) {
                    Row(
                        Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CubeSpinner(size = 15.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("扫描中…", fontSize = 12.sp, fontFamily = homeBody, color = sheetInkSecondary)
                    }
                }
                if (hasScanPerm && devices.isEmpty() && !viewModel.isScanning) {
                    Text(
                        if (viewModel.hasBoundRoom) "未找到寝室内的热水器" else "未发现热水器",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        fontSize = 12.sp, color = sheetInkSecondary
                    )
                }
                devices.forEach { device ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDevice(device) }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFE5F0FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            DeviceGlyph(device.typeEmoji, emojiSize = 14.sp, iconSize = 18.dp, tint = sheetIcon)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = sheetInk)
                            Text(device.signalText, fontSize = 10.sp, color = sheetInkSecondary)
                        }
                        Text("›", fontSize = 15.sp, color = sheetChevron)
                    }
                }
                if (lastDeviceLine != null && !viewModel.isScanning) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("↻", fontSize = 14.sp, color = sheetIcon)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("上次使用", fontSize = 12.sp, color = sheetInkSecondary)
                            Text(lastDeviceLine, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = sheetInk)
                        }
                    }
                }
                // 底部呼吸位：84dp 的胶囊让位已经由外层裁切窗口处理，
                // 这里再垫一大截会在可滚动后变成一屏空白
                Spacer(Modifier.height(12.dp))
            }
        }
        }
    }
}
