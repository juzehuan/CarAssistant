# CarAssistant 构建与发布说明

本说明记录车机助手（CarAssistant）Release 包的构建、加壳（混淆）、签名、加固及版本管理流程，方便复现出包。

---

## 1. 环境要求

| 项目 | 说明 |
| --- | --- |
| JDK | Android Studio 自带 JRE（JBR）即可，或 JDK 17 |
| Android SDK | 通过 `local.properties` 的 `sdk.dir` 指定 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 项目自带 Wrapper（`gradlew.bat`） |

> Windows 上若未配置 `JAVA_HOME`，构建/签名时需先设置：
> ```bat
> set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
> set "PATH=%JAVA_HOME%\bin;%PATH%"
> ```

---

## 2. 版本号方案（对齐规则）

位于 `app/build.gradle` 的 `defaultConfig`：

```gradle
versionCode 20260802      // 构建日期 YYYYMMDD，单调递增，保证升级兼容
versionName "1.0.0"       // 语义化版本 x.y.z
```

- **versionCode**：采用构建日期 `YYYYMMDD`，保证永远单调递增，避免升级时因版本回退被系统拒绝。
- **versionName**：语义化版本 `x.y.z`，与 versionCode 通过本约定保持一致（如 `1.0.0` ↔ `20260802`）。
- 每次发版只需更新这两个值；versionCode 必须比上一版更大。

---

## 3. 签名配置

签名信息集中在根目录 `keystore.properties`（**勿提交到版本库**）：

```properties
storeFile=carassistant-release.jks
storePassword=********
keyPassword=********
keyAlias=carassistant-release
```

`app/build.gradle` 的 `signingConfigs.release` 自动读取该文件；`buildTypes.release` 与 `bundle.release` 均使用此签名。

- Keystore 文件：`carassistant-release.jks`（位于项目根目录）
- 签名证书：`CN=CarAssistant, OU=Development, O=CarAssistant Project, L=Shenzhen, ST=Guangdong, C=CN`

---

## 4. 加壳（代码混淆 / 压缩）

`app/build.gradle` 的 `buildTypes.release` 已开启：

```gradle
minifyEnabled true        // R8 混淆 + 压缩 + 优化
shrinkResources true      // 资源压缩
proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
```

混淆规则在 `app/proguard-rules.pro`，要点：

- 自定义字典混淆（`dictionary.txt`），`-repackageclasses ''` 重排包名，提高反编译难度；
- 保留 `Application`、四大组件、无障碍服务、通知监听服务等反射实例化类；
- 移除 `Log.d/v` 调用，避免敏感信息泄露；
- 保留 `R` 类与 `MusicController.Callback` 等接口。

> 注：此为「轻量加壳」。如需更强保护，见第 6 节「二次加固」。

---

## 5. 构建命令

在项目根目录执行（PowerShell / CMD 均可，需先配置 `JAVA_HOME`）：

```bat
# 生成已混淆 + 签名的 APK
gradlew.bat assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk

# 生成已混淆 + 签名的 AAB（Android App Bundle，用于上架应用商店）
gradlew.bat bundleRelease
# 产物: app/build/outputs/bundle/release/app-release.aab
```

校验签名：

```bat
apksigner.bat verify --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## 6. 二次加固后再签名

轻量加壳不足以抵御专业逆向时，可在出包后用第三方加固服务（腾讯乐固、360 加固保、梆梆安全等）进行「二次加壳」，加固后 APK 的签名会失效，需用本项目的 release keystore **重新签名**。

脚本 `harden_resign.bat` 封装了「对齐 + 重签名」环节：

```bat
# 1) 先用加固工具（如乐固 CLI）对 app-release.apk 加固，得到加固 APK
# 2) 用本脚本重签名
harden_resign.bat <加固后的APK> [输出APK]
# 例：
harden_resign.bat app-release_legu.apk app-release-final.apk
```

脚本会自动：读取 `keystore.properties` → `zipalign` 对齐 → `apksigner` 用 release keystore 重签名 → 校验签名。

---

## 7. 产物清单

| 产物 | 路径 | 说明 |
| --- | --- | --- |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | 已混淆 + 签名 |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` | 已混淆 + 签名，用于上架 |
| 混淆映射 | `app/build/outputs/mapping/release/mapping.txt` | 反混淆堆栈用，发版后妥善保存 |

---

## 8. 注意事项

- `keystore.properties` 与 `carassistant-release.jks` 含敏感信息，**切勿提交到 Git**。
- 发版后请备份并妥善保存 `mapping.txt`，用于线上崩溃日志反混淆。
- 加固会改变 APK 结构，加固后**必须重新签名**且需用同一 release keystore，否则无法覆盖安装 / 上架。
