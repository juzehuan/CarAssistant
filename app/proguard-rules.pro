# =============================================================================
# CarAssistant ProGuard / R8 规则
#
# 版权所有 (C) 2026 CarAssistant Project
# 保留所有权利
#
# 本文件是车机助手应用的代码混淆与压缩配置。
# 启用 R8 后，release 构建会对代码进行混淆、压缩、优化，
# 提高反编译难度，减小 APK 体积。
#
# 如需更强保护，建议在 release 构建后使用第三方加固服务
# （如 360 加固保、腾讯乐固、梆梆安全等）进行二次加壳。
# =============================================================================

# -------------------- 基础优化 --------------------
# 不预校验（Android 上无需 preverify）
-dontpreverify

# 混淆时使用更激进的字典，增加反编译难度
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt

# 混淆后的名称复用，减小体积
-overloadaggressively

# 允许修改包名（进一步混淆）
-allowaccessmodification
-repackageclasses ''

# 合并接口（仅当实现相同时）
-mergeinterfacesaggressively

# 优化次数
-optimizationpasses 5

# 保留注解（运行时反射依赖）
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# -------------------- AndroidX / Support Library --------------------
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

# -------------------- Application 入口 --------------------
# Application 类由系统通过反射实例化，必须保留
-keep class com.carassistant.App { *; }

# -------------------- Activity / Service / Receiver --------------------
# AndroidManifest 中声明的组件由系统通过反射启动，R8 默认会保留
# 这里显式声明，确保安全
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# -------------------- 系统服务子类（反射调用，必须保留）--------------------
# 无障碍服务：系统通过反射实例化并调用生命周期方法
-keep public class * extends android.accessibilityservice.AccessibilityService {
    public <init>();
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    public void onInterrupt();
    public void onServiceConnected();
}
# 通知监听服务：系统通过反射实例化
-keep public class * extends android.service.notification.NotificationListenerService {
    public <init>();
    public void onListenerConnected();
    public void onListenerDisconnected();
    public void onNotificationPosted(android.service.notification.StatusBarNotification);
    public void onNotificationRemoved(android.service.notification.StatusBarNotification);
    public void onActiveNotificationsChanged(android.service.notification.StatusBarNotification[]);
}

# -------------------- View / 自定义控件（XML 反射实例化）--------------------
# 自定义 View 由 LayoutInflator 通过反射实例化
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}
-keep public class * extends android.widget.BaseAdapter
-keep public class * extends android.widget.Adapter

# -------------------- Adapter（RecyclerView 性能依赖）--------------------
-keep public class * extends androidx.recyclerview.widget.RecyclerView$Adapter
-keep public class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder

# -------------------- 回调接口（外部调用）--------------------
# MusicController.Callback 由 UI 层实现，需保留方法名
-keep interface com.carassistant.util.MusicController$Callback { *; }

# -------------------- 序列化 --------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# -------------------- 枚举 --------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# -------------------- 反射 / JNI（如有）--------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# -------------------- 日志（可选：移除 Log 调用）--------------------
# release 构建移除 Log.d / Log.v 调用，避免敏感信息泄露
-assumenosideeffects class android.util.Log {
    public static int v(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
}

# -------------------- 异常处理（避免删除必要 try-catch）--------------------
-dontoptimize

# -------------------- 资源相关 --------------------
# 保留 R 字段（资源 ID 引用）
-keep class com.carassistant.R { *; }
-keep class com.carassistant.R$* { *; }

# 保留 drawable 引用的类成员（部分反射访问）
-keepclassmembers class com.carassistant.** {
    public static final int *;
}
