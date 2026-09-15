# CarAssistant 项目长期记忆

## 分支策略（重要：两个版本长期并存）
用户明确要求**全功能版和精简版都保留，不做合并**。两条分支并行维护：
- `main` = **全功能版**（含音乐/歌词/按键映射/侧边栏/控制面板等），终端提交 `0cb092b`，与 `origin/main` 一致，保持干净不动。
- `slim-core` = **精简版**（只保留六大核心功能），是当前开发主线，已推远端并设上游跟踪。
- ⚠️ 注意 `main` 的名字不代表“最新版”，它反而是功能更全的旧版本；改代码前先确认自己在哪条分支上。
- 分支间规模差异参考：`git diff --shortstat main slim-core` → 196 files changed, +682 / -23781。
- 永久锚点标签（已推远端）：`v1-full` → `0cb092b`（全功能版）、`v1-slim` → `3067d48`（精简版）。

## 网络：GitHub 必须走本机代理（重要）
- 本机**直连 github.com:443 被墙**（`Test-NetConnection github.com -Port 443` 返回 False，curl 直连报 `Failed to connect ... after 21064 ms`）。
- 本机代理 `127.0.0.1:7890` 可用（系统代理已开启，Clash 之类）。git 未配置代理时会随机报 `CONNECT tunnel failed, response 502` / `Empty reply from server`，看着像仓库问题、实际是网络。
- **推拉命令模板**：`git -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push origin <branch>`
- 如嫌麻烦可在仓库里持久化（代理关闭时会失效，需手动删）：`git config --local http.proxy http://127.0.0.1:7890`。

## ⚠️ 签名：本地构建**无法覆盖**车机上的已装应用（重大坑）
- 车机上装的旧版 `com.carassistant` 是**公司 release 签名**：
  `CN=Remobie Check, O=Chongzhu, C=TH`（如 `C:\Users\Administrator\Downloads\app-release.apk`，2026-09-03）。
- 本机 `assembleDebug` 产出的是 **Android Debug 签名**（`CN=Android Debug`）。两者签名不匹配，
  Android 会拒绝安装，报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，**旧应用原样保留**。
  症状表现：改完功能装上后"什么都没变"（旧图标、新按钮找不到）——不是代码问题，是根本没装上。
- 项目根目录**没有** `keystore.properties` / `*.jks`，所以本地也无法产出与旧版同签名的 release 包。
- 验证签名的命令（build-tools 37.0.0 在用）：
  ```powershell
  & "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs <apk> | Select-String "certificate DN"
  ```
- **当前可行路径**：先 `adb uninstall com.carassistant`（或车机上手动卸载）→ 再装 debug 包。
  代价是会清掉白名单、开机自启列表等本地配置。
- **长期路径**：找用户要 Remobie/Chongzhu 的 release keystore，放进项目根目录并写 `keystore.properties`
  （`storeFile` / `storePassword` / `keyAlias` / `keyPassword`），之后 `assembleRelease` 才能原地升级。
  配套脚本 `harden_resign.bat` 负责加固后的 zipalign + 重签名。
- `versionCode` 固定为 `20260802`、`versionName 1.0.0`，**从没随构建更新过**；按注释约定应改成构建日期 YYYYMMDD。

## 项目概况
- Android 车机（一汽红旗）工具 App，包名 `com.carassistant`。
- 目标机型 Android 8/9（API 26-28）；`minSdk 26` / `targetSdk 34` / `compileSdk 34`。
- 功能集随分支不同：`slim-core` 为六大核心（清理优化、应用管理、文件管理、性能监控、自启管理、设备信息）；`main` 为完整功能集。
- 应用显示名：`应用管理`（原「车机助手」，仅 `slim-core` 分支改过）。

## 关键约定 / 坑
- **分支命名不能带 `/`**：本环境 git 无法创建 `feature/xxx` 这类分支（引用写不进 `.git/refs/heads/`，静默失败）。用 `slim-core` 这类无斜杠名字。
- **构建**：`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`，`ANDROID_HOME=C:\Users\Administrator\AppData\Local\Android\Sdk`，命令 `./gradlew assembleDebug`。**不要加 `--offline`**（拿不到 AGP 插件）。
- 遇到 `mergeDebugJavaResource` 报「拒绝访问」是构建缓存文件锁，先 `gradlew --stop` 再重编。
- 本环境 Bash 缺 coreutils（ls/grep/sed/wc/head 都不可用），git 命令可用；资源搜索用 Grep/Glob 工具。
- PowerShell 工具的 stdout 不回显，需要写文件再读；`Remove-Item` 被 safe-delete 拦截，用 `[System.IO.File]::Delete()`。
- `git commit -F -` 走管道会把中文变成 `?`，用 `-m` 或写成 UTF-8 文件。

## Android 存储枚举（已实机验证有效，可复用）
Android 8/9 上 U 盘挂在 `/storage/XXXX-XXXX`，不能用路径含 `usb`/`udisk` 来判断。有效做法：
1. 三级枚举：`StorageManager.getStorageVolumes()` → 反射 `getVolumeList()` → 扫 `/storage`、`/mnt/media_rw`、`/mnt/usb_storage`、`/mnt/udisk`。
2. 路径归一化：`/mnt/media_rw/<seg>` ↔ `/storage/<seg>`（前者普通应用无权访问）。
3. U 盘判定：`/proc/mounts` 块设备 major —— 8/65-71/128-135 = SCSI（U 盘）；179/259 = MMC（SD 卡）。
4. 挂载校验：`Environment.getExternalStorageState(File)` + `/proc/mounts` 独立挂载点检查，避免拔盘后残留空目录被误判。
5. 不要用 `total <= 0` 静默过滤卷，StatFs 失败时应显示「容量未知」。
- 调试：`Log.d(TAG="StorageUtil")` 打印每个卷来源（api/hidden/scan）与 usb 判定，`adb logcat -s StorageUtil:D`。

## 自适应图标规则备忘
- 108dp 画布，中心 72dp 可见，安全直径约 90dp（**半径 45dp 内最不会被遮罩裁切**）。
- 放大图形用 `<group pivotX/pivotY/scaleX/scaleY>` 包一层，不要改 `pathData`。

## 图标方案（PNG 位图，用户提供的设计稿）
- **唯一设计源**：`tools/gen-icons/source/ic_launcher_source.png`（192×192 RGBA，蓝色圆角方块+白色车形）。
  改图标就替换这个文件，然后跑 `python tools/gen-icons/gen_icons.py`。
- 密度基准是**传统图标标准 48dp**：mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192。
  输出 `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` + `ic_launcher_round.png`（圆形版按遮罩裁切）。
- 矢量自适应图标（`mipmap-anydpi-v26/*.xml`、`drawable/ic_launcher_foreground.xml`）已删除。
  `tools/gen-icons/ic_launcher_foreground.xml` 仅作历史留档 + 脚本兜底。
- ⚠️ 设计源只有 192px，**不要上采样**（会发虚）。需要更大尺寸就找用户要 512/1024 的设计稿。
- ⚠️ `.gitignore` 有全局 `*.png`（排截图用）。新增任何位图资源都要在末尾补
  `!路径/*.png` 例外，否则会被静默忽略、进不了提交。
- ⚠️ 车机 launcher 会缓存图标，改完必须**卸载重装**才会刷新。

## 构建：Gradle 缓存锁（journal-1）
- 症状：启动阶段报 `FileNotFoundException: .gradle\caches\journal-1\journal-1.lock (拒绝访问)`，构建直接失败。
- `gradlew --stop` **不一定有效**。有效解法是删掉整个 journal 缓存目录（Gradle 会自动重建，安全）：
  ```powershell
  & .\gradlew.bat --stop
  [System.IO.Directory]::Delete("$env:USERPROFILE\.gradle\caches\journal-1", $true)
  ```
- 编译前建议固定先跑一次 `gradlew --stop`，能省一轮失败。

## UI 入口可见性（踩过两次，务必按此自查）
- 主题是 `Theme.MaterialComponents.Light.NoActionBar` → **ActionBar / 选项菜单永远不会显示**。
  `onCreateOptionsMenu` + `res/menu/*.xml` 这套写法在本项目里等于死代码（已删 `main_menu.xml`）。
  新增页面入口必须写在布局里（首页头部图标之类），不能靠菜单。
- **入口和按钮都要放首屏内**。车机是横屏，竖向空间比手机小得多；2560 高的模拟器上首屏约到 y=1500，
  放在 y=2200 的底部卡片用户滚不到（反馈过「看不到按钮」）。
- 自查套路（不依赖截图）：`adb shell uiautomator dump` 导出 XML 后 grep 节点是否存在 + `bounds` 是否在首屏，
  再用 `adb shell input tap <cx> <cy>` 点击验证；确认进程/服务状态用
  `adb shell pidof com.carassistant` 与 `adb shell dumpsys activity services | grep MonitorService`。

## 发布产物
- `dist/CarAssistant-slim-YYYYMMDD.apk` 是给车机手动安装的包，来源即 `app/build/outputs/apk/debug/app-debug.apk` 的拷贝；
  `dist/` 已被 `.gitignore` 覆盖，不进版本库。
- 核实包内容（无需安装）：`aapt2 dump resources <apk> | grep "id/xxx"`、`aapt2 dump badging <apk> | grep application-label`。
  badging 输出里的中文在 GBK 控制台会显示成乱码（如 `应用管理` → `搴旂敤绠＄悊`），属编码显示问题，不是包坏了。

## 内嵌第三方 App：方控映射（com.hzsoft.keymapx）
- 上游来源 `C:\Users\Administrator\Downloads\appp`（逆向+优化工程，产物 `方控映射_1.1_优化版.apk`，
  入口 `com.hzsoft.sidebar.KeyMapSettingsActivity`，签名密钥 `_tools\keymap-release.jks`，密码 `keymap123456`）。
- 集成方式是**原样内嵌**：`app/src/main/assets/keymap.apk` 是逐字节副本
  （SHA256 `5a60e6ff…37f702`、235982 字节），代码侧只做「已装则开、未装则装」。
- ⚠️ `.gitignore` 有全局 `*.apk`。**任何放进 assets 的包都必须补 `!app/src/main/assets/*.apk` 例外**，
  否则会被静默忽略：既不进提交也不进 APK，且不报错。
- 装 APK 一律走 `FileUtil.installApk(ctx, file)`，不要另写 intent —— 那条路（FileProvider +
  `ACTION_VIEW` + chooser 兜底）已经在文件管理页跑通。
- 想换成别的内嵌应用：替换 assets 文件 + 改 `KeyMapLauncher` 里的 PKG / MAIN_ACTIVITY / 卡片文案即可。
- 上游只有 smali 没有 Java 源码，所以「按源码合并进工程」这条路实际等于重写，不要轻易答应。

## 模拟器验证：先加载 skill
- 本项目做任何「装到模拟器上看功能是否生效」的活，**动手前先加载 `android-emulator-verify` skill**。
  它带 `scripts/ui.py`（dump + 解析 + `ui.tap_text` 按文本点击），以及常见的坑
  （adb 只认 Windows 路径、Bash 缺 coreutils、时序陷阱、release 包不能 run-as）。
- 本机雷电常被**多个会话同时占用**：症状是 app 被 `pm suspend/disable-user` 冻掉、`am start` 抢不到前台、
  `uiautomator dump` 报 `UiAutomationService … already registered!`。处置见该 skill 的 2.7 节。
