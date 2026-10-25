# PROJECT — 沐光架构与关键取舍

本文只记录**重构后**的结构与决策。协议细节、设备帧格式、历史踩坑见
[docs/API-qzxy.md](docs/API-qzxy.md)。

## 1. 分层

```
ui/            界面与 ViewModel（Compose）
  design/      设计系统：令牌 → 风格包 → 组件 → 动效
data/          仓储、控制器、结算、状态机（可单测，不依赖 Android）
model/         DTO 与纯数据模型
api/           Retrofit / OkHttp / 签名 / 更新检查
ble/           蓝牙帧编解码与设备查询
service/       前台服务、小组件后台蓝牙
widget/        桌面小组件渲染
utils/         偏好、日志、加密、通知
```

依赖方向单向向下：`ui → data → api/model`。`data` 不反向引用 `ui`，
所以剩余时间、结算、账单解析这些东西可以脱离 Android 跑单测（121 个测试都在这层）。

## 2. 设计系统

### 2.1 令牌分三层

1. **基础令牌**（`DesignTokens.kt`）：颜色、形状、字体、面板、动效五组，纯数据类
2. **风格包**（`StylePacks.kt`）：五套具体取值 × 浅色/深色
3. **组件**（`Components.kt`）：`SurfaceCard` / `SectionHeader` / `MetricValue` / `StatusBadge` /
   `PrimaryAction` / `QuietAction` / `Hairline`

页面只读 `Design.palette`、`Design.type` 这类访问器，**不写死颜色**。
`MuguangTheme` 把风格包翻译成 `AppColorSet` 与 Material3 `ColorScheme`，
所以还没重写的旧页面也会自动跟随配色，不会出现两套色板打架。

### 2.2 为什么要五套而不是一套

用户先要选型。把五套做成并列的风格包，选型后不需要删除任何一套——切换开关留在设置页，
以后调整某个页面的层级设计时，可以立刻在多套视觉下检查是否都成立。

### 2.3 形状与描边的取舍

- GPT / Claude / 方舟：1dp 描边，**不用阴影**。阴影在深色底上等于消失，描边不会。
- Apple：0.5dp 极浅投影 + 无描边，模拟系统的分组卡片。
- Google：无描边无阴影，用 `surfaceTonal` 色块分区。
- 方舟：`CutCornerShape(topEnd, bottomStart)`，两个对角切，比四角全切更稳（不会像缺角图片）。

## 3. 沉浸光感

`AmbientGlow` 是一层径向渐变，位置随时间缓慢漂移（7–12 秒一个来回）。

**为什么不用 blur**：

- `Modifier.blur` 在 API < 31 上是空操作，视觉会突然降级；
- 全屏 `RenderEffect` 在低端机上直接吃掉帧率；
- 径向渐变本身没有硬边，不需要再模糊一次。

光晕只给 hero（登录页头部、正在运行的洗衣机卡）。列表项一律不开——
一屏多处漂移会变成噪声，也白白多耗绘制。

按下反馈用弹簧：`dampingRatio 0.72–0.9`、`stiffness 320–600`，
不同风格手感略有差别（方舟最"脆"，Claude 最"软"）。

## 4. 洗衣机状态与剩余时间

服务端对**别人的单**不返回剩余秒数。这一点是逐接口验证过的结论，不是没找到：
`device/mac/list/new`、`washingLiquid/liquidStatusObj`、`SnLaundryResponse`、
`query/rateOrder/using` 全部清点过，BLE/MQTT 只传金额与时间戳，不传用量。

因此剩余时间按六级优先级取，**只有前两级敢叫"剩余"**：

1. `status.time`（服务端剩余秒，超过总时长时夹到总时长）
2. 服务端文案里带「剩/余/还有」的时间
3. 观测到的本单启动时刻（轮询看到订单 key 变化，取两次采样的中点）
4. 排队预测（上一单的结束时刻）
5. `consumeDate` 估算
6. 本地会话已用时长

3–6 一律标记为估算。拿不到 1、2 时，界面不说"剩余"，而说
「对方 21:03 下单 · 已过 17:23」——两个都是服务端事实，猜错的风险归零。

时钟基准用 HTTP `Date` 响应头校准（`NetworkModule.serverNowMs()`），
本机时间与服务器差几分钟时倒计时不会整体偏移。

## 5. 工程身份

- 包名 `io.github.ranxian.muguang`，applicationId 相同
- 偏好文件名 `muguang_prefs` / `muguang_prefs_plain`，通知渠道 `muguang_alert` 等
- 更新源未配置（`UpdateApi.FEED_REPO` 留空），检查更新降级为本地提示
- 换包名意味着**不能覆盖安装旧版**：两者是两个独立应用，数据不共享

## 6. 已知限制

- Release 关闭 R8（本机 OOM），包体偏大
- 只打 arm64-v8a
- 界面只有中文
- R8/JDK 环境要求：JDK 21、AGP 8.13.2、Gradle 9.4.1、compileSdk 36
