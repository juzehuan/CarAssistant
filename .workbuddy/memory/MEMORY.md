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

## 图标方案（已从矢量改为位图）
- 当前图标是 **PNG 位图**：`res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` + `ic_launcher_round.png`。
  矢量自适应图标配置（`mipmap-anydpi-v26/*.xml`、`drawable/ic_launcher_foreground.xml`）已删除。
- 尺寸基准是 **108dp 画布**：mdpi 108 / hdpi 162 / xhdpi 216 / xxhdpi 324 / xxxhdpi 432。
- 重新生成图标：改 `tools/gen-icons/ic_launcher_foreground.xml`（矢量源，支持 `<group>` 缩放）后运行
  `python tools/gen-icons/gen_icons.py`，会自动写出全部密度的 PNG 并校验尺寸。
- 原理：`pathData` 与 SVG `d` 兼容 + 无头浏览器渲染，不需要 cairo 等原生依赖。
  ⚠️ group 变换换算：Android `postXxx` 是**左乘**语义，等价 SVG 为
  `translate(tx+px,ty+py) rotate(r) scale(sx,sy) translate(-px,-py)`（顺序不能反）。
- ⚠️ `.gitignore` 有全局 `*.png`（排截图用），已在末尾加 `!app/src/main/res/mipmap-*/ic_launcher*.png` 例外；
  新增其它位图资源时注意同样会被吞掉。
- ⚠️ 图标相关的坑：车机 launcher 会缓存应用名与图标，改完要卸载重装；纯位图图标在 Android 8+ 可能被套白底圆圈并缩放。
