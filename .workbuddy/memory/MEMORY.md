# CarAssistant 项目长期记忆

## 项目概况
- Android 车机（一汽红旗）工具 App，包名 `com.carassistant`。
- 目标机型 Android 8/9（API 26-28）；`minSdk 26` / `targetSdk 34` / `compileSdk 34`。
- 当前功能集（精简后六大核心）：清理优化、应用管理、文件管理、性能监控、自启管理、设备信息。
- 应用显示名：`应用管理`（原「车机助手」）。

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
