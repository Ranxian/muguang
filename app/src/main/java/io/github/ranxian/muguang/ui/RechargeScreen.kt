package io.github.ranxian.muguang.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alipay.sdk.app.PayTask
import io.github.ranxian.muguang.data.AlipayResult
import io.github.ranxian.muguang.data.RechargeAmounts
import io.github.ranxian.muguang.data.RechargeChannel
import io.github.ranxian.muguang.model.RechargePackage
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.PressButton
import io.github.ranxian.muguang.ui.design.SurfaceCard
import io.github.ranxian.muguang.ui.design.cardShape
import io.github.ranxian.muguang.ui.design.controlShape
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.ui.theme.inkOnFill
import io.github.ranxian.muguang.utils.AppLogger
import io.github.ranxian.muguang.utils.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 充值页。
 *
 * ## 为什么只有套餐、没有输入框
 *
 * 官方 `activity_recharge.xml` 里根本没有金额输入控件——金额完全由
 * `settlement/recharge/package/list` 下发。自己加一个输入框看着更自由，
 * 实际上会让服务端那套「活动 / 赠送 / 限额」全部失效，还会把非法金额
 * 变成一句看不懂的报错。所以这里和官方保持一致：只能选套餐。
 *
 * ## 钱的边界
 *
 * 本页做的事只有三件：把服务端下发的选项摆出来、发一次下单请求、
 * 把 orderInfo 交给支付宝。**余额永远从服务端重新拉**，不在这里加减。
 */
@Composable
fun RechargeScreen(
    mainViewModel: MainViewModel?,
    onBack: () -> Unit
) {
    val vm: RechargeViewModel = viewModel()
    val state = vm.state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var balanceRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    // 防误触：页面刚出现的那一下触摸可能被系统重放到新页面上——实测在模拟器上
    // 出现过「点『去充值』→ 新页面同一坐标就是确认按钮 → 直接下了一单」。
    // 按钮外观不变，只是页面稳定 600ms 之后才接受点击。
    // 这一下如果真发出去，用户会白白多出一笔待支付订单，所以宁可迟钝一点。
    var submitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        submitArmed = true
    }

    // 下单结果一律回到这里处理：同一个入口，不会出现「有的通道走 UI、有的走回调」
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is RechargeViewModel.Event.LaunchAlipay -> {
                    val activity = context.findActivity()
                    if (activity == null) {
                        vm.onAlipayLaunchFailed("当前页面无法拉起支付宝，请退出充值页重进后再试")
                    } else {
                        // PayTask 是阻塞调用（内部会起自己的 Activity 并等待），必须离开主线程
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val raw = PayTask(activity).payV2(event.orderInfo, true)
                                AlipayResult.of(raw?.get("resultStatus") as? String)
                            }.getOrElse { e ->
                                AppLogger.w("拉起支付宝失败：${e.message}")
                                AlipayResult.UNKNOWN
                            }
                        }
                        vm.onAlipayResult(result)
                    }
                }

                is RechargeViewModel.Event.OpenCashier -> {
                    val opened = runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                    }.isSuccess
                    if (!opened) vm.onAlipayLaunchFailed("无法打开收银台页面")
                }

                RechargeViewModel.Event.Paid -> {
                    // 唯一被允许的「刷新余额」路径：重新问服务端要
                    mainViewModel?.refreshWallet()
                    mainViewModel?.loadCampusBalance()
                }
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回我的页面",
                    tint = AppColors.TextPrimary
                )
            }
            Text("账户充值", style = Design.type.title, color = Design.palette.ink)
        }
        Spacer(Modifier.height(12.dp))

        WalletBalanceCard(
            balance = mainViewModel?.walletBalance,
            refreshing = balanceRefreshing,
            onRefresh = {
                if (!balanceRefreshing) {
                    balanceRefreshing = true
                    mainViewModel?.refreshWallet()
                    mainViewModel?.loadCampusBalance()
                    scope.launch {
                        delay(1200)
                        balanceRefreshing = false
                    }
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        when {
            state.loadError != null -> RechargeError(
                message = state.loadError!!,
                onRetry = { vm.load() }
            )

            state.loading && state.packages.isEmpty() && state.channels.isEmpty() -> RechargeLoading()

            else -> {
                if (state.channels.isEmpty()) {
                    RechargeEmpty(
                        title = "本校暂未开通在线充值",
                        detail = "服务端没有为这个项目返回可用的支付方式。可以到官方趣智校园 App 里看看是否已开通。"
                    )
                } else if (state.packages.isEmpty()) {
                    RechargeEmpty(
                        title = "暂时没有可选金额",
                        detail = "服务端没有下发有效的充值套餐，请稍后再试。"
                    )
                } else {
                    SectionTitle("选择充值金额")
                    Spacer(Modifier.height(10.dp))
                    AmountGrid(
                        packages = state.packages,
                        selectedTypeId = state.selectedTypeId,
                        onSelect = vm::selectPackage
                    )

                    Spacer(Modifier.height(22.dp))
                    SectionTitle("支付方式")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.channels.forEach { channel ->
                            ChannelRow(
                                channel = channel,
                                selected = channel == state.selectedChannel,
                                enabled = !state.placing && !state.paying,
                                onClick = { vm.selectChannel(channel) }
                            )
                        }
                    }
                }

                state.noSaveMessage?.let { tip ->
                    Spacer(Modifier.height(16.dp))
                    HintCard(tip)
                }

                state.notice?.let { text ->
                    Spacer(Modifier.height(16.dp))
                    NoticeCard(text, state.noticeIsError, onDismiss = vm::dismissNotice)
                }

                Spacer(Modifier.height(22.dp))
                PressButton(
                    onClick = { if (submitArmed) vm.submit() },
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = controlShape(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Design.palette.accent,
                        contentColor = inkOnFill(Design.palette.accent, Design.palette.accentInk),
                        disabledContainerColor = Design.palette.surfaceSunken,
                        disabledContentColor = Design.palette.inkMuted
                    )
                ) {
                    Text(
                        when {
                            state.placing -> "正在下单…"
                            state.paying -> "等待支付结果…"
                            state.amount != null -> "确认充值 ¥${state.amount}"
                            else -> "请选择充值金额"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "支付由支付宝 / 校方收银台完成，沐光不保存你的支付凭证；" +
                        "余额以趣智校园服务端为准，到账后点上方「刷新余额」即可看到。",
                    style = Design.type.caption,
                    color = Design.palette.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(120.dp)) // 让底部悬浮导航栏压不到内容
    }
}

/** 与其它二级页面一致：状态栏留白 */
@Composable
private fun WalletBalanceCard(
    balance: Double?,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("钱包余额 (元)", style = Design.type.body, color = Design.palette.inkMuted)
            TextButton(
                onClick = onRefresh,
                enabled = !refreshing,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Design.palette.accent
                    )
                } else {
                    Text("刷新余额", fontSize = 13.sp, color = Design.palette.ink)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            balance?.let { "¥ ${MoneyFormat.format(it)}" } ?: "¥ —",
            style = Design.type.number,
            color = Design.palette.ink
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "由趣智校园服务端实时返回，充值到账后以服务端为准",
            style = Design.type.caption,
            color = Design.palette.inkMuted
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = Design.palette.ink
    )
}

@Composable
private fun AmountGrid(
    packages: List<RechargePackage>,
    selectedTypeId: Long?,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        packages.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { pkg ->
                    AmountTile(
                        pkg = pkg,
                        selected = pkg.typeId == selectedTypeId,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(pkg.typeId) }
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AmountTile(
    pkg: RechargePackage,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = cardShape()
    val amount = RechargeAmounts.normalize(pkg.amount)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Design.palette.accent.copy(alpha = 0.12f) else AppColors.Card)
            .border(
                BorderStroke(
                    width = if (selected) 1.6.dp else 0.8.dp,
                    color = if (selected) Design.palette.accent else Design.palette.hairline
                ),
                shape
            )
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (amount != null) "¥$amount" else "—",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            // 20sp 粗体属大字，3:1 即可；强调色在浅色风格最差 3.9:1，够用
            color = if (selected) Design.palette.accent else Design.palette.ink
        )
        if (pkg.givenMoney > 0.0) {
            Spacer(Modifier.height(2.dp))
            Text(
                "赠 ¥${MoneyFormat.format(pkg.givenMoney)}",
                fontSize = 11.sp,
                color = Design.palette.inkMuted
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: RechargeChannel,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = cardShape()
    val detail = when (channel) {
        RechargeChannel.ALIPAY -> "拉起支付宝完成支付"
        RechargeChannel.JUHE -> "聚合收银台，支持微信 / 支付宝 / 银联"
        RechargeChannel.CAMPUS -> "跳转学校的一卡通充值页"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .border(
                BorderStroke(
                    width = if (selected) 1.6.dp else 0.8.dp,
                    color = if (selected) Design.palette.accent else Design.palette.hairline
                ),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                channel.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Design.palette.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 12.sp, color = Design.palette.inkMuted)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) Design.palette.accent else Color.Transparent)
                .border(
                    BorderStroke(
                        1.4.dp,
                        if (selected) Design.palette.accent else Design.palette.hairline
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(inkOnFill(Design.palette.accent, Design.palette.accentInk))
                )
            }
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(Design.palette.accent.copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("提示", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Design.palette.accent)
        Spacer(Modifier.size(10.dp))
        Text(text, fontSize = 13.sp, color = Design.palette.inkMuted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NoticeCard(text: String, isError: Boolean, onDismiss: () -> Unit) {
    val tint = if (isError) Design.palette.danger else Design.palette.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape())
            .background(tint.copy(alpha = 0.10f))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            fontSize = 13.sp,
            color = Design.palette.ink,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("知道了", fontSize = 12.sp, color = Design.palette.inkMuted)
        }
    }
}

@Composable
private fun RechargeLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                strokeWidth = 2.dp,
                color = Design.palette.accent
            )
            Spacer(Modifier.height(12.dp))
            Text("正在读取充值信息…", fontSize = 13.sp, color = Design.palette.inkMuted)
        }
    }
}

@Composable
private fun RechargeError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("充值信息没拿到", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Design.palette.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = Design.palette.inkMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        PressButton(
            onClick = onRetry,
            shape = controlShape(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Design.palette.accent,
                contentColor = inkOnFill(Design.palette.accent, Design.palette.accentInk)
            )
        ) { Text("重试", fontSize = 14.sp) }
    }
}

@Composable
private fun RechargeEmpty(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Design.palette.ink)
        Spacer(Modifier.height(8.dp))
        Text(detail, fontSize = 13.sp, color = Design.palette.inkMuted, textAlign = TextAlign.Center)
    }
}

/**
 * 从 Compose 的 Context 里找 Activity。
 *
 * `PayTask` 的构造函数要的是 Activity 而不是 Context——传 Application 会
 * 在拉起收银台时直接抛异常。Compose 里的 `LocalContext` 在 Activity 宿主下
 * 通常就是 Activity 本身，但被主题 / 弹窗包过一层时会变成 `ContextThemeWrapper`，
 * 所以逐层拆。
 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
