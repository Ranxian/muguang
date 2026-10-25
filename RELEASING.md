# 发版手册

发版全部由 tag 驱动，不需要本地打包上传。

## 一次性准备

### 1. 签名密钥

仓库里**永远不存** `.jks`。CI 从 secrets 里解出来，本地开发走 `local.properties`。

把现有密钥转成 base64（在项目根目录执行，路径按实际改）：

```bash
base64 -w0 ../keys/muguang-release.jks > keystore.b64
```

`keystore.b64` 的内容填进 `KEYSTORE_BASE64`，填完立刻删掉这个文件。

⚠️ 这把密钥一旦用于发布就不能更换——换了签名，老版本无法覆盖安装，只能卸载重装。
密钥本体和密码请离线备份两份。

### 2. GitHub Secrets

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | 上面生成的 base64（单行） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名（当前为 `muguang`） |
| `KEY_PASSWORD` | 密钥密码 |
| `GITEE_TOKEN` | 可选。Gitee 私人令牌，勾 `projects` 权限。不填则只发 GitHub |
| `GITEE_OWNER` | 可选，默认 `Ranxian` |
| `GITEE_REPO` | 可选，默认 `muguang` |

### 3. 仓库与镜像

- GitHub：`https://github.com/Ranxian/muguang`
- Gitee 镜像：`https://gitee.com/Ranxian/muguang`（Gitee 仓库需先在网页上建好，并推一次 `main`）

远端已配好：

```bash
git remote -v
# origin  https://github.com/Ranxian/muguang.git
# gitee   https://gitee.com/Ranxian/muguang.git
```

首次推送：

```bash
git push -u origin main
git push -u gitee main
```

## 每次发版

1. 改 `app/build.gradle.kts` 的 `versionCode`（**必须递增**）和 `versionName`
2. 更新 `CHANGELOG.md`
3. 提交，然后打 tag 推送：

```bash
git commit -am "chore: 发版 1.2.1"
git tag v1.2.1
git push origin main --tags
```

workflow 会依次做：单测 → 构建 release → `apksigner verify` 确认已签名 → 建 GitHub Release
并附上 `muguang-<version>-arm64-v8a.apk` 与 `.sha256` → （配了 token 时）在 Gitee 建同名发行版传同一份附件。

## 检查更新怎么对上的

`api/UpdateApi.kt` 里两个常量决定一切：

- `FEED_REPO` —— 检查更新走 `api.github.com/repos/<FEED_REPO>/releases/latest`
- `MIRROR_REPO` —— 下载直链拼 `https://gitee.com/<MIRROR_REPO>/releases/download/<tag>/<附件名>`

所以**附件名必须与 Gitee 上的完全一致**（workflow 两边用的是同一个文件名），
少一个字符镜像就 404，此时下载会自动回落到 GitHub 的 `browser_download_url`。

几个容易踩的点：

- tag 必须带 `v` 前缀且能被 `v?\d+(\.\d+)*` 解析，否则 `ApkUpdater.isNewer` 判不出高低
- `-rc` / `-beta` 会被 workflow 标成 pre-release；`/releases/latest` 本来就跳过预发布，
  但 tag 名为 `v1.2.0-rc1` 时 `isNewer` 也只比到 `1.2.0`（不把 rc 当更新）
- 版本号显示读 `BuildConfig.VERSION_NAME`，不要在任何界面写死

## 本地出包（不发布）

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

`local.properties` 里放签名四项时本地包也会签名；缺项则产物未签名（仅可调试，不能分发）：

```properties
KEYSTORE_FILE=../keys/muguang-release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=muguang
KEY_PASSWORD=...
```

## 校验发版结果

```bash
# 签名指纹应与上次一致
apksigner verify --print-certs app-release.apk | grep SHA-256

# 装上后打开「我的 → 检查更新」，应当提示已是最新
# 想验证更新链路：把 versionName 临时减一档出包安装，再点检查更新，应提示有新版本并可下载
```
