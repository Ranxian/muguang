package io.github.ranxian.muguang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.data.BalanceEstimator
import io.github.ranxian.muguang.data.BillCategory
import io.github.ranxian.muguang.data.billCategory
import io.github.ranxian.muguang.data.forBillCategory
import io.github.ranxian.muguang.model.BillDTO
import io.github.ranxian.muguang.model.BillItem
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.DeviceKind
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.PressButton
import io.github.ranxian.muguang.ui.design.SurfaceCard
import io.github.ranxian.muguang.ui.design.deviceTint
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.ui.theme.contrastRatio
import io.github.ranxian.muguang.ui.theme.inkOnFill
import io.github.ranxian.muguang.utils.MoneyFormat
import io.github.ranxian.muguang.utils.PrefsHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WalletScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.loadBills()
        // 一卡通真实余额只在这里和主页各拉一次（「我的」页面也有）——
        // 漏了这个页面的话，先开钱包页会一直按「估算」渲染
        viewModel.loadCampusBalance()
    }
    var detailBill by remember { mutableStateOf<BillDTO?>(null) }
    var billCategory by rememberSaveable { mutableStateOf(BillCategory.BATH) }
    var showProjectPicker by remember { mutableStateOf(false) }
    // 待确认的代扣：(consumeDate, 金额)。这是动钱的操作，不弹确认框直接扣太莽了
    var pendingDeduct by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 进页面和下拉刷新时都拉一次未支付列表——残留账单是服务端状态，只有它说了算
    LaunchedEffect(Unit) { viewModel.loadUnpaidBills() }
    var showEditDialog by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }

    // 消费趋势开关：进页面时读一次。设置页改完切回来，本页会被重建（AnimatedContent），
    // remember 重新初始化，读到最新值——和通知开关（NotifyCard）同一套模型。
    var trendEnabled by remember { mutableStateOf(PrefsHelper.trendEnabled) }

    var pullRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            pullRefreshing = true
            viewModel.pullRefresh()
            scope.launch {
                kotlinx.coroutines.delay(1000)
                pullRefreshing = false
            }
        }
    )

    // 余额：接口拿得到就用真实的，拿不到才回退「初始余额 − 之后产生的消费」。
    // 算法和主页、桌面小组件共用一份。
    //
    // 账单没到位时给 null，避免先把初始余额亮出来再跳变（见 MainViewModel.billsLoaded）——
    // 但**真实余额不受这个限制**：它是现成的数字，不是减出来的，不用等账单。
    // ⚠️ 必须把钱包余额也算进「有真实余额」——只认一卡通的话，
    // 没接一卡通的学校会被误判成「只能估算」，明明钱包里有确切数字却让用户手填。
    val hasReal = viewModel.campusBalance != null || viewModel.walletBalance != null
    val displayBalance = remember(
        viewModel.billList, viewModel.billsLoaded, viewModel.campusBalance,
        viewModel.walletBalance,
        PrefsHelper.manualBalance, PrefsHelper.manualBalanceTime
    ) {
        if (hasReal || viewModel.billsLoaded) BalanceEstimator.estimate(viewModel.billList) else null
    }

    val projectBills = remember(
        viewModel.billHistory,
        viewModel.billsProjectId,
        viewModel.currentProjectId
    ) {
        if (viewModel.billsProjectId == viewModel.currentProjectId) {
            viewModel.billHistory
        } else {
            emptyList()
        }
    }
    val categoryHistory = remember(projectBills, billCategory) {
        projectBills.forBillCategory(billCategory)
    }
    val categoryPage = remember(categoryHistory) { categoryHistory.take(20) }
    val categoryTotal = remember(categoryPage) {
        categoryPage.sumOf { it.consumeBillDTO.consumeMoney.toDoubleOrNull() ?: 0.0 }
    }
    val bathCount = remember(projectBills) {
        projectBills.count { it.billCategory() == BillCategory.BATH }
    }
    val washerCount = remember(projectBills) {
        projectBills.count { it.billCategory() == BillCategory.WASHER }
    }

    LaunchedEffect(viewModel.currentProjectId, viewModel.currentProjectName) {
        if (viewModel.currentProjectName.contains("洗衣") ||
            viewModel.currentProjectName.contains("洗烘")
        ) {
            billCategory = BillCategory.WASHER
        }
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullState)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 标题按**钱在哪**写：一卡通 → 「一卡通余额」，趣智校园钱包 → 「趣智校园余额」。
            // （卡片内那行「账户余额 (元)」保持不动。）
            Text(
                BalanceEstimator.title(),
                style = Design.type.title, color = Design.palette.ink
            )
            Spacer(Modifier.height(16.dp))

            SurfaceCard(modifier = Modifier.fillMaxWidth(), contentPadding = 24.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("账户余额 (元)", style = Design.type.body, color = Design.palette.inkMuted)
                        // 拿到真实余额后「手动填写」就藏起来：估算值会被真实值覆盖，
                        // 摆在那儿只会让人以为填了能改数字。
                        //
                        // 字色用 ink 而不是强调色：强调色当小字压在卡片上，两套浅色风格
                        // 只有 3.9:1（iOS 蓝 #007AFF 压白卡 / 暖纸赤陶 3.91:1），不够 4.5:1。
                        // 「这里能点」改由下划线提示——和登录页「改用验证码登录」同一套做法，
                        // 不引入新颜色。
                        // 触摸区补到 48dp：原来只有 ~31dp（4dp 内边距 + 一行正文），手指按不准。
                        if (!hasReal) {
                            Text(
                                "手动填写", color = Design.palette.ink, style = Design.type.body,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        // 预填**当前显示的余额**，不是上次填的那个初始值。
                                        // 预扣过几次之后再进来，填上次的旧数字会让人以为没生效，
                                        // 而且从这个数字改起本来也更顺手。
                                        editValue = displayBalance?.let { BalanceEstimator.formatMoney(it) }
                                            ?: PrefsHelper.manualBalance.ifEmpty { "0" }
                                        showEditDialog = true
                                    }
                                    .heightIn(min = 48.dp)
                                    // 文字在 48dp 触摸区里垂直居中，视觉上仍和左边标签同一行
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(displayBalance?.let { "¥ ${BalanceEstimator.formatMoney(it)}" } ?: "¥ —",
                        style = Design.type.number, color = Design.palette.ink)
                    Spacer(Modifier.height(8.dp))
                    // 三个来源要分别说清是哪来的：都写成「校园卡」的话，
                    // 显示趣智校园钱包余额的学校会对不上账。
                    Text(
                        when {
                            viewModel.campusBalance != null -> "来自校园卡账户的实时余额"
                            hasReal -> "来自趣智校园账户的实时余额"
                            else -> "仅通过初始金额和账单进行估算，并非真实余额"
                        },
                        style = Design.type.caption, color = Design.palette.inkMuted
                    )
            }

            Spacer(Modifier.height(24.dp))

            ProjectSwitchCard(
                currentProjectName = viewModel.currentProjectName,
                switching = viewModel.projectSwitching,
                onClick = {
                    viewModel.loadAvailableProjects()
                    showProjectPicker = true
                }
            )

            Spacer(Modifier.height(16.dp))

            BillCategoryTabs(
                selected = billCategory,
                bathCount = bathCount,
                washerCount = washerCount,
                onSelected = { billCategory = it }
            )

            Spacer(Modifier.height(20.dp))

            if (trendEnabled) {
                SpendingTrendCard(
                    bills = categoryHistory,
                    loading = viewModel.isLoadingBills && !viewModel.billsLoaded
                )
                Spacer(Modifier.height(24.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "${billCategory.label}账单",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        "最近 20 次${billCategory.label}消费",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
                Text(
                    "合计 ${MoneyFormat.withSymbol(categoryTotal)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))

            if (viewModel.isLoadingBills && categoryPage.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = AppColors.Accent, strokeWidth = 2.dp)
                }
            } else if (categoryPage.isEmpty()) {
                Text(
                    "暂无${billCategory.label}账单记录",
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ⚠️ 在循环外取一次。unpaidConsumeDates 是计算属性，
                    // 写在 forEach 里的话每渲染一行就重建一次整个 Set
                    val unpaidDates = viewModel.unpaidConsumeDates
                    categoryPage.forEach { bill ->
                        val dto = bill.consumeBillDTO
                        BillCard(
                            bill = bill,
                            unpaid = dto.consumeDate in unpaidDates,
                            deducting = viewModel.deductingConsumeDate == dto.consumeDate,
                            onDeduct = { pendingDeduct = dto.consumeDate to dto.consumeMoney },
                            onClick = { detailBill = dto }
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp)) // 底部留出悬浮导航栏空间
        }

        PullRefreshIndicator(
            refreshing = pullRefreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = AppColors.SolidSurface, // 不透明，避免半透明叠加导致内外不一致
            contentColor = AppColors.Accent
        )
    }

    // 手动余额编辑弹窗
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("手动填写余额", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // 这个弹窗只在拿不到任何真实余额时才出现，写「一卡通」会让人
                    // 以为填的是校园卡——其实就是个本地基准值，说「账户」更准。
                    Text("请输入账户当前余额", color = AppColors.TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("此操作仅用于本地估算，不会修改账户真实余额",
                        color = AppColors.TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { v ->
                            // 最多9位整数 + 2位小数
                            if (v.isEmpty() || v.matches(Regex("\\d{0,9}(\\.\\d{0,2})?"))) editValue = v
                        },
                        label = { Text("余额 (元)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    PrefsHelper.manualBalance = editValue
                    PrefsHelper.manualBalanceTime = System.currentTimeMillis()
                    showEditDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    // 请求代扣确认弹窗。
    // ⚠️ 这是**从一卡通里真扣钱**，必须先让用户看清楚金额再动手 ——
    // 按钮在列表里很小一颗，误触的代价是真金白银。
    pendingDeduct?.let { (date, money) ->
        DeductConfirmDialog(
            amount = money.toDoubleOrNull(),
            detail = date,
            onConfirm = {
                pendingDeduct = null
                viewModel.requestDeduct(date) { r ->
                    viewModel.toastMessage = when (r) {
                        is MainViewModel.DeductResult.Ok -> "代扣成功"
                        is MainViewModel.DeductResult.Failed -> r.reason ?: "代扣失败"
                        // 结果未知**不能说失败**：请求可能已经发出去了，
                        // 说失败就是在鼓励用户再点一次 → 扣两次
                        is MainViewModel.DeductResult.Unknown -> "结果未知，请下拉刷新确认后再操作"
                    }
                }
            },
            onDismiss = { pendingDeduct = null }
        )
    }

    // 账单详情弹窗
    if (detailBill != null) {
        val dto = detailBill!!
        AlertDialog(
            onDismissRequest = { detailBill = null },
            title = { Text("账单详情", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val name = dto.description.substringAfter(":")
                    val dname = if (name.isNotEmpty()) DeviceInfo.formatDeviceName(name) else dto.description
                    WDetailRow("设备名", dname)
                    WDetailRow("设备类型", dto.deviceTypeLabel)
                    WDetailRow("消费金额", "-¥ ${dto.consumeMoney}")
                    WDetailRow("消费时间", dto.consumeDate.take(19))
                    WDetailRow("订单号", dto.orderId)
                }
            },
            confirmButton = { Button(onClick = { detailBill = null }) { Text("确定") } }
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
}

@Composable
private fun BillCategoryTabs(
    selected: BillCategory,
    bathCount: Int,
    washerCount: Int,
    onSelected: (BillCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BillCategory.entries.forEach { category ->
            val active = category == selected
            val count = if (category == BillCategory.BATH) bathCount else washerCount
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    // 填充用 AppColors.Accent：它就是当前风格包的强调色（Theme.kt 里
                    // 从 pack.palette.accent 派生，额外叠加自定义背景提取的主题色），
                    // 和本页其它强调色元素保持一致，不会出现「胶囊和进度圈不是一个蓝」
                    .background(if (active) AppColors.Accent else Color.Transparent)
                    .clickable { onSelected(category) }
                    // 触摸区补到 48dp（原来 ~40dp）；高度用 heightIn 而不是 height，
                    // 字号放大到 1.3 倍以上时胶囊能自己长高，不会把字切掉
                    .heightIn(min = 48.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${category.label}  $count",
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    // 选中态的字走 inkOnFill：风格包配对的 accentInk 已经够用（4.58~4.75:1），
                    // 但 AppColors.Accent 还会被「自定义背景提取的主题色」覆盖，
                    // 那个颜色是什么都可能——由 inkOnFill 现场按亮度挑墨色，永远 >=4.5:1。
                    color = if (active) inkOnFill(AppColors.Accent, Design.palette.accentInk)
                            else AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BillCard(
    bill: BillItem,
    unpaid: Boolean,
    deducting: Boolean,
    onDeduct: () -> Unit,
    onClick: () -> Unit
) {
    val dto = bill.consumeBillDTO
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        border = BorderStroke(0.8.dp, AppColors.Border), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${dto.deviceEmoji} ${dto.displayDesc}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                val tc = when {
                    dto.isDrinkingWater -> deviceTint(DeviceKind.DRINKING)
                    dto.isWasher -> deviceTint(DeviceKind.WASHER)
                    dto.deviceTypeLabel.contains("洗手台") -> deviceTint(DeviceKind.SINK)
                    else -> Design.palette.accent
                }
                // 设备类型是 12sp 正文，色标必须自己够 4.5:1；某些色调（比如 iOS 浅色
                // 下的系统蓝）压在白卡上只有 3.9:1，这种就退回中性墨色，别让字读不清
                Text(dto.deviceTypeLabel, fontSize = 12.sp,
                    color = if (contrastRatio(tc, Design.palette.surface) >= 4.5f) tc
                            else Design.palette.inkMuted)
                Spacer(Modifier.height(2.dp))
                Text(dto.consumeDate.take(16), fontSize = 12.sp, color = AppColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("-¥ ${dto.consumeMoney}", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = spendRed())
                // 只有服务端说这条**没扣成功**时才出现按钮。判断依据是
                // /order/weixinScorePay/unPay/queryBill 的返回，不去猜账单里的字段含义。
                if (unpaid) {
                    Spacer(Modifier.height(6.dp))
                    PressButton(
                        onClick = onDeduct,
                        enabled = !deducting,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        // 30dp 是给 12sp 定的死高度，字号放大后标签会被切；
                        // 改成最小高度，正常字号下仍是 44dp 的可按尺寸
                        modifier = Modifier.heightIn(min = 44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Warning,
                            // 禁用只在「扣款中」这一瞬间出现。原来禁用底是
                            // TextSecondary@0.4（半透明灰）配写死的白字，浅色下白字压浅灰
                            // 只有 1.78:1；换成不透明的沉底 + inkMuted，实测 4.3~6.8:1
                            // （禁用态本身不参加 4.5:1 的要求，但也不能真的看不见）。
                            disabledContainerColor = Design.palette.surfaceSunken,
                            disabledContentColor = Design.palette.inkMuted
                        )
                    ) {
                        // 扣款中把文案也换掉：只靠变灰不够，用户会以为卡住了
                        // 白字压在 warning 上在终端（荧光黄 1.49:1）、极简深色（1.67:1）
                        // 基本读不出来，所以按实测挑字色
                        Text(if (deducting) "扣款中…" else "请求代扣",
                            fontSize = 12.sp, color = inkOnFill(AppColors.Warning, Design.palette.accentInk))
                    }
                }
            }
        }
    }
}

@Composable
private fun WDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
    }
}

/**
 * 支出金额的红。
 *
 * danger 令牌是按「实心填充色」选的（白字压在上面才算可读），它自己当正文颜色时，
 * 浅色风格要掉到 4.5:1 以下：iOS 红 #FF3B30 压在白卡上只有 3.46:1。
 * 这里把红色往 ink 方向拉三成 —— ink 在每套风格里都是与底色对比最高的颜色，
 * 靠近它必然抬高对比度，红色语义还在；深色风格下 ink 是浅色，方向自动反过来。
 * 实测最差 6.33:1（默认浅色），其余 6.3~8.6:1。
 *
 * 选「压深颜色」而不是「把字号抬到 18.66sp 走大字 3:1 豁免」：金额列宽度和行高都不动，
 * 账单列表的观感保持不变。
 */
@Composable
private fun spendRed(): Color = lerp(Design.palette.danger, Design.palette.ink, 0.30f)
