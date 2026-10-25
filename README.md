# 沐光 (Muguang)

> 沐光 Muguang · Android（Kotlin / Jetpack Compose）· v1.2.0 · MIT

校园热水与洗衣的 Android 客户端。用来启停热水、查看绑定洗衣机的实时状态、看账单与余额。

> 第三方客户端，与设备运营方、学校均无隶属关系。账号、余额、订单全部由服务端裁决，本应用只做展示与转发。

## 功能

- **热水**：扫码或用附近设备列表启动、停止，按剩余金额/时长自动结算
- **洗衣机**：绑定一台机器常驻首页，展示状态与剩余时间；别人的单不给剩余秒时，改显示「对方几点下单 · 已过多久」——只说服务端能给的事实，不猜
- **绑定后常驻**：45 秒轮询，切到别的页面也会更新
- **账单与余额**：消费分类、趋势、按项目隔离
- **桌面小组件**：1×1 / 2×2 / 2×4，直接启停
- **背景装扮**：自定义背景图，自动取主题色作为强调色
- **界面风格**：五套并列的视觉语言，设置页随时切换

## 界面风格

截图略。五套风格结构完全一致，只有色彩、字体、圆角与分隔手法不同：

| 风格 | 特征 |
|---|---|
| **GPT**（默认） | 纯白底、极细描边、超大标题与超大等宽数字 |
| Apple | 系统灰底、白色分组卡、蓝色只给交互 |
| Claude | 暖纸底、衬线标题、赤陶色点缀 |
| Google | 浅灰底、淡蓝色调容器、填充胶囊按钮 |
| 明日方舟 | 深色底、荧光黄、切角边框、中英双语等宽标签 |

实现方式：`ui/design/` 里的 `StylePack`（调色板 / 形状 / 字体 / 面板 / 动效五组令牌），
页面从 `LocalStyle` 取令牌，不写死颜色。新增风格只需要加一个 `StyleId` 和两套取值（浅色 + 深色）。

## 动效：沉浸光感

- 顶部一层缓慢漂移的径向光晕（`AmbientGlow`），只出现在 hero 区域；列表项不开，避免噪声
- 按下反馈用弹簧（damping 0.72–0.9，stiffness 320–600），不用 tween
- 完全不依赖 `RenderEffect` / blur：`minSdk 26` 也能跑，且不需要全屏模糊的额外开销

## 安装

到本仓库的 **Releases** 页面下载最新 APK，在手机上打开安装即可。

- 最低系统版本 **Android 8.0（API 26）**
- 只打 **arm64-v8a**，纯 32 位的老机型装不上
- APK 由本项目自己的签名密钥签名，不是应用商店版本

## 构建

```bash
# JDK 21 + Android SDK 36
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Release 需要 `local.properties` 里的签名四项（`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）；
缺任意一项时 release 包保持未签名，debug 不受影响。

本机（Windows + 受限页面文件）跑 R8 会 OOM，因此 `release` 关闭混淆，仅用于自用验证。

## 测试

121 个单元测试，覆盖洗衣机状态机与剩余时间优先级、结算引擎、账单日期解析、BOM 隔离、时间显示策略等纯逻辑。

## 目录

```
app/src/main/java/io/github/ranxian/muguang/
├── api/       网络、签名、更新检查
├── ble/       蓝牙帧与设备查询
├── data/      仓储、控制器、结算、状态机
├── model/     数据模型
├── service/   前台服务、小组件蓝牙
├── ui/        界面
│   └── design/  设计系统（令牌 / 风格包 / 组件 / 动效）
├── utils/     偏好、日志、加密、通知
└── widget/    桌面小组件
```

## 相关文档

- [PROJECT.md](PROJECT.md)：架构、数据流、状态机与关键取舍
- [docs/API-qzxy.md](docs/API-qzxy.md)：接口契约（字段、签名、坑）
- [SECURITY.md](SECURITY.md)：安全边界与凭据处理

## 许可

MIT，见 [LICENSE](LICENSE)。第三方组件与许可条款见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
