# CarAssistant · 车机助手

> 面向 Android 车机（红旗车机为主）的精简系统工具箱，只保留清理优化、应用管理、文件管理、性能监控、自启管理、设备信息六大核心功能。现代化 Material Design 风格，注重大屏车机的扫视可读性与品牌设计语言。

## 功能模块

| 模块 | 说明 |
| --- | --- |
| 主界面 | 首页 / 应用 / 清理 / 文件 四大 Fragment + 底部导航 |
| 清理优化 | 缓存扫描 + 残留文件清理 + 内存加速（Root / 普通模式） |
| 应用管理 | 应用列表、APK 安装、应用详情、批量卸载 |
| 文件管理 | 存储浏览、U 盘自动识别 |
| 性能监控 | CPU / 内存 / 温度悬浮窗实时监控 |
| 自启管理 | 应用开机自启动配置 |
| 设备信息 | 硬件 / 系统 / 屏幕信息一览 |
| 权限引导 | 首次启动集中授权（运行时权限 + 特殊权限） |
| 设置 | 内存清理白名单、版本号 |
| U 盘监听 | 插拔事件自动刷新文件列表 |

## 技术栈

- **语言**：Java 1.8
- **平台**：Android 8.0 (API 26) ~ Android 14 (API 34)
- **UI**：AndroidX AppCompat + Material Design 1.11.0 + ConstraintLayout + RecyclerView + ViewPager2 + CardView
- **架构**：单 Activity 多 Fragment + 多功能 Activity + 前台服务
- **构建**：Gradle 8.13 + Android Gradle Plugin
- **加壳**：R8 / ProGuard 代码混淆 + 资源压缩（release 构建默认启用）

## 目录结构

```
CarAssistant/
├── app/
│   ├── build.gradle              # 模块构建配置（启用 R8 混淆）
│   ├── proguard-rules.pro        # ProGuard / R8 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 组件声明、权限申请
│       ├── java/com/carassistant/
│       │   ├── App.java          # Application 入口（崩溃日志）
│       │   ├── MainActivity.java # 主界面
│       │   ├── SettingsActivity.java
│       │   ├── adapter/          # RecyclerView 适配器
│       │   ├── receiver/         # BootReceiver / UsbReceiver
│       │   ├── service/          # 性能监控悬浮窗服务
│       │   ├── ui/               # Fragment 与各功能 Activity
│       │   └── util/             # 工具类（清理、文件、权限、内存、设备等）
│       └── res/
│           ├── drawable/         # 自定义背景、图标
│           ├── layout/           # 布局文件
│           ├── values/           # 颜色、尺寸、字符串、主题
│           ├── values-sw600dp/   # 大屏适配
│           └── xml/              # FileProvider 配置
├── build.gradle                  # 工程构建配置
├── settings.gradle
├── gradle/                       # Gradle Wrapper
├── gradlew / gradlew.bat
└── .gitignore
```

## 核心权限

| 权限 | 用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 性能监控悬浮窗 |
| `PACKAGE_USAGE_STATS` | 扫描应用缓存大小 |
| `MANAGE_EXTERNAL_STORAGE` | 全盘文件管理（Android 11+） |
| `REQUEST_INSTALL_PACKAGES` | APK 安装 |
| `REQUEST_DELETE_PACKAGES` | 应用卸载 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启调度 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型声明 |

## 构建方法

### 环境要求

- JDK 17+（推荐使用 Android Studio 自带 JBR）
- Android SDK 34
- Android Studio

### 命令行构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（启用 R8 混淆 + 资源压缩）
./gradlew assembleRelease
```

构建产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release-unsigned.apk`（需自行签名）

### 签名

Release APK 默认未签名，发布前需配置签名 keystore。在 `app/build.gradle` 中添加：

```gradle
signingConfigs {
    release {
        storeFile file(RELEASE_STORE_FILE)
        storePassword RELEASE_STORE_PASSWORD
        keyAlias RELEASE_KEY_ALIAS
        keyPassword RELEASE_KEY_PASSWORD
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release
        // ... 已有的 minifyEnabled / shrinkResources 配置
    }
}
```

并在 `gradle.properties`（不入库）中填入凭据。

## 安全与版权

- **代码混淆**：release 构建启用 R8，进行代码压缩、混淆、优化，提高反编译难度
- **日志裁剪**：release 自动移除 `Log.v` / `Log.d` 调用，避免敏感信息泄露
- **版权声明**：全部 Java 源文件头部均含版权声明

```
Copyright (C) 2026 CarAssistant Project. All rights reserved.
版权所有 (C) 2026 车机助手项目
未经著作权人书面许可，不得以任何形式复制、修改、分发、出售或逆向工程。
```

如需更强保护，建议 release 构建后使用第三方加固服务（360 加固保、腾讯乐固、梆梆安全等）进行二次加壳。

许可证详见 [LICENSE](LICENSE)。

## 兼容性

- 最低支持：Android 8.0 (API 26)
- 目标版本：Android 14 (API 34)
- 屏幕适配：支持 `sw600dp` / `sw720dp` / `sw840dp` / `sw1080dp` 多档车机大屏
- 旋转适配：`configChanges` 自行处理方向变化，避免 Activity 重建

## 致谢

- [AndroidX](https://developer.android.com/jetpack/androidx)
- [Material Components for Android](https://github.com/material-components/material-components-android)
