package io.github.ranxian.muguang.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ranxian.muguang.BuildConfig
import io.github.ranxian.muguang.ui.design.AmbientGlow
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.QuietAction
import io.github.ranxian.muguang.ui.design.GlowAlignment
import io.github.ranxian.muguang.ui.design.cardShape
import io.github.ranxian.muguang.ui.design.controlShape

/**
 * 把一个输入框接入系统自动填充。两件事缺一不可：
 * 1. 把控件在屏幕上的位置告诉 AutofillNode（系统要靠它把高亮框画对位置）
 * 2. 控件获得焦点时主动向系统发一次请求，密码管理器才会弹出「填充/保存」条
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.loginAutofill(node: AutofillNode, autofill: Autofill?): Modifier =
    this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .onFocusChanged { if (it.isFocused) autofill?.requestAutofillForNode(node) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onLoginSuccess: (String) -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    // 入场只做「轻微上浮 + 放大」，幅度很小：登录页是每天要看一次的门，
    // 大动画会变成等待。
    val scale by animateFloatAsState(if (entered) 1f else 0.97f, tween(320))
    val lift by animateFloatAsState(if (entered) 0f else 14f, tween(320))

    val p = Design.palette

    // ── 系统自动填充（保存/回填账号密码）──
    // 本项目用的 Compose 1.6 还没有官方那套 `Modifier.semantics { contentType = ... }`
    // （那个 API 到 Compose 1.8 才有），所以这里用官方暴露的底层三件套自己接：
    //   AutofillNode（描述这个输入框是什么）+ AutofillTree（注册给系统）+ LocalAutofill（触发请求）
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val phoneAutofill = remember {
        AutofillNode(listOf(AutofillType.Username)) { viewModel.phone = it }
    }
    val passwordAutofill = remember {
        AutofillNode(listOf(AutofillType.Password)) { viewModel.password = it }
    }
    val smsAutofill = remember {
        AutofillNode(listOf(AutofillType.SmsOtpCode)) { viewModel.smsCode = it }
    }

    // 注册到系统 Autofill 树；离开页面时摘掉，避免切来切去堆积无用节点
    DisposableEffect(Unit) {
        val nodes = listOf(phoneAutofill, passwordAutofill, smsAutofill)
        nodes.forEach { autofillTree += it }
        onDispose { nodes.forEach { autofillTree.children.remove(it.id) } }
    }

    LaunchedEffect(viewModel.loginResult) {
        val result = viewModel.loginResult
        if (result != null && result.isSuccess) {
            onLoginSuccess(viewModel.phone)
            viewModel.resetResult()
        }
    }

    LaunchedEffect(Unit) { entered = true }

    Box(Modifier.fillMaxSize().background(p.canvas)) {
        // 沉浸光感：顶部一团漫射光，缓慢漂移，作为整页唯一的装饰
        AmbientGlow(
            tokens = Design.motion.glow,
            modifier = Modifier.fillMaxWidth().height(420.dp).align(Alignment.TopCenter),
            alignment = GlowAlignment.Top,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .offset(y = lift.dp)
                .verticalScroll(rememberScrollState())
                // 底部要让开导航栏，否则最后一行版本号会被手势条压住
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // 用 inkMuted 而不是 inkSubtle：inkSubtle 是给「分隔线旁的极弱提示」准备的，
            // 当 11sp 小字压在画布上只有 1.98:1（默认风格），远不够 4.5:1；
            // 换成 inkMuted 后是 4.69:1，其余风格 5.4~10.1:1。
            Text("沐光 · MUGUANG", style = Design.type.label, color = p.inkMuted)
            Spacer(Modifier.height(10.dp))
            Text("登录，开始用水。", style = Design.type.hero, color = p.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "校园热水与洗衣，一个入口管好。",
                style = Design.type.body,
                color = p.inkMuted,
            )

            Spacer(Modifier.height(26.dp))

            // 登录卡片：不再用蓝色渐变包白卡，整页只有一层纸面
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape())
                    .background(p.surface)
                    .border(1.dp, p.hairline, cardShape())
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text("欢迎回来", style = Design.type.title, color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text("请使用校园账号登录", style = Design.type.caption, color = p.inkMuted)
                Spacer(Modifier.height(14.dp))

                // 密码/验证码切换
                TextButton(
                    onClick = { viewModel.switchMode() },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(
                        text = if (viewModel.isSmsMode) "改用密码登录" else "改用验证码登录",
                        style = Design.type.caption.copy(
                            fontWeight = FontWeight.SemiBold,
                            // GPT 风格的强调色接近纯黑，纯文字链接会和普通标签分不清，
                            // 加下划线是唯一不引入新颜色的"可点"提示
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                        color = p.ink,
                    )
                }
                // 当前模式的一句说明。放这儿是因为用户最容易卡住的两个点都在这：
                // 没注册的人不知道能不能直接用（能，会自动注册），
                // 没设过密码的人不知道密码该填什么（填不了，用验证码）
                Text(
                    if (viewModel.isSmsMode) "未注册的手机号将自动创建账号"
                    else "未设置密码？请使用手机号验证码登录",
                    style = Design.type.caption,
                    color = p.inkMuted,
                    modifier = Modifier.padding(bottom = 14.dp),
                )

                OutlinedTextField(
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label = { Text("手机号") },
                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = p.inkMuted) },
                    trailingIcon = {
                        if (viewModel.phone.isNotEmpty()) {
                            TextButton(onClick = { viewModel.phone = "" }) {
                                Text("✕", color = p.inkMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().loginAutofill(phoneAutofill, autofill),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = controlShape(),
                    colors = loginFieldColors()
                )
                Spacer(Modifier.height(12.dp))

                if (viewModel.isSmsMode) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = viewModel.smsCode,
                            onValueChange = { viewModel.smsCode = it },
                            label = { Text("验证码") },
                            modifier = Modifier.weight(1f).loginAutofill(smsAutofill, autofill),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = controlShape(),
                            colors = loginFieldColors()
                        )
                        Spacer(Modifier.width(10.dp))
                        // 固定宽度：文案在「发送」↔「60s」之间变化时按钮宽度不变，
                        // 否则会把左边 weight(1f) 的输入框挤窄/挤宽，看着像抖了一下
                        Button(
                            onClick = { viewModel.sendSmsCode() },
                            enabled = !viewModel.isSendingCode && viewModel.countdown == 0,
                            shape = controlShape(),
                            modifier = Modifier.width(84.dp).height(56.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = p.accent,
                                contentColor = p.accentInk,
                                disabledContainerColor = p.surfaceSunken,
                                disabledContentColor = p.inkSubtle,
                            )
                        ) {
                            Text(
                                if (viewModel.countdown > 0) "${viewModel.countdown}s" else "发送",
                                fontSize = 13.sp, maxLines = 1,
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = { Text("密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = p.inkMuted) },
                        trailingIcon = {
                            if (viewModel.password.isNotEmpty()) {
                                TextButton(onClick = { viewModel.password = "" }) {
                                    Text("✕", color = p.inkMuted)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().loginAutofill(passwordAutofill, autofill),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = controlShape(),
                        colors = loginFieldColors()
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.login() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !viewModel.isLoading,
                    shape = controlShape(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.accent,
                        contentColor = p.accentInk,
                        disabledContainerColor = p.surfaceSunken,
                        disabledContentColor = p.inkSubtle,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp), color = p.accentInk, strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "登录",
                            style = Design.type.body.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = p.accentInk,
                        )
                    }
                }

                viewModel.errorMessage?.let { msg ->
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(controlShape())
                            .background(p.danger.copy(alpha = if (p.isDark) 0.16f else 0.08f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            msg,
                            style = Design.type.caption,
                            color = p.danger,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 同上：版本号是 12sp 小字，inkSubtle 在浅色底上读不清，改 inkMuted
                // 版本号必须读 BuildConfig：写死会在发版后与更新检查里的版本对不上
                Text(
                    "沐光 v${BuildConfig.VERSION_NAME} · 非官方校园客户端",
                    style = Design.type.caption,
                    color = p.inkMuted,
                )
                Spacer(Modifier.width(10.dp))
                QuietAction(
                    text = if (showStyles) "收起风格" else "界面风格",
                    onClick = { showStyles = !showStyles },
                )
            }
            if (showStyles) {
                Spacer(Modifier.height(12.dp))
                StylePickerCard()
            }
        }
    }
}

/**
 * 登录页输入框配色。
 *
 * 卡片底色现在跟着 surface 令牌走（不再固定白底），所以这里可以安全地用主题色，
 * 不会出现深色模式下"浅色字压白卡"的问题。强调色只给聚焦态。
 */
@Composable
private fun loginFieldColors(): TextFieldColors {
    val p = Design.palette
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.hairline,
        focusedLabelColor = p.inkMuted,
        // 未聚焦时的浮动标签就是用户判断「这格填什么」的唯一提示，12sp 小字；
        // inkSubtle 在浅色底上只有 2:1 左右，和页脚那两处一起换成 inkMuted
        unfocusedLabelColor = p.inkMuted,
        focusedTextColor = p.ink,
        unfocusedTextColor = p.ink,
        cursorColor = p.accent,
        focusedContainerColor = p.surface,
        unfocusedContainerColor = p.surface,
    )
}
