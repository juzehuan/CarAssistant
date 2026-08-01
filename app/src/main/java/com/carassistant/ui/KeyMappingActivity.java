/*
 * Copyright (C) 2026 CarAssistant Project. All rights reserved.
 *
 * 版权所有 (C) 2026 车机助手项目
 * 保留所有权利
 *
 * 本源代码受著作权法保护，未经著作权人书面许可，不得以任何形式复制、修改、
 * 分发、出售或逆向工程。违反者将承担法律责任。
 *
 * Source code protected by copyright law. Unauthorized copying, modification,
 * distribution, sale, or reverse engineering without written permission is
 * prohibited and subject to legal action.
 */

package com.carassistant.ui;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carassistant.R;
import com.carassistant.util.KeyActionExecutor;
import com.carassistant.util.KeyMappingUtil;
import com.carassistant.util.PermissionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按键映射设置页
 *
 * 功能：
 * - 列出已配置的按键映射（支持单击/双击/长按/组合键）
 * - 添加映射：先录制按键（支持组合键），再选触发模式，最后选动作
 * - 编辑映射：长按列表项可重新选择动作（保留原按键和触发模式）
 * - 测试映射：点击测试按钮立即执行动作
 * - 启用/禁用映射
 * - 顶部菜单：导入、导出、清空、应用预设模板
 *
 * 支持动作类型：应用启动 / 音量 / 媒体 / 开关 / 亮度 / 系统操作 / 车机助手 /
 *               系统设置子页 / 常用应用 / 高级动作（URL/广播/音量/通知栏等）
 */
public class KeyMappingActivity extends AppCompatActivity {

    private RecyclerView rv;
    private TextView tvEmpty;
    private KeyMappingAdapter adapter;
    private final List<KeyMappingUtil.KeyMapping> mappings = new ArrayList<>();

    /** 导入文件选择器 */
    private final ActivityResultLauncher<String> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImportFile);
    /** 导出文件创建器 */
    private final ActivityResultLauncher<String> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::onExportFile);

    /** 通知权限请求 launcher（Android 13+） */
    private final ActivityResultLauncher<String> notificationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // 权限回调后刷新状态
                refreshPermissionCard();
            });

    /** 权限状态卡视图引用 */
    private View cardPermissions;
    private TextView tvAccessibilityStatus;
    private TextView tvNotificationStatus;
    private View btnAccessibilityGo;
    private View btnNotificationGo;
    private ImageView ivAccessibilityIcon;
    private ImageView ivNotificationIcon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_mapping);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rv = findViewById(R.id.rv_mappings);
        tvEmpty = findViewById(R.id.tv_empty);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new KeyMappingAdapter();
        adapter.setListener(new KeyMappingAdapter.OnMappingActionListener() {
            @Override public void onDelete(KeyMappingUtil.KeyMapping m) { confirmDelete(m); }
            @Override public void onToggleEnabled(KeyMappingUtil.KeyMapping m, boolean on) {
                KeyMappingUtil.setEnabled(KeyMappingActivity.this, m, on);
            }
            @Override public void onTest(KeyMappingUtil.KeyMapping m) {
                // 立即执行该映射的动作进行测试
                KeyActionExecutor.execute(KeyMappingActivity.this, m);
                Toast.makeText(KeyMappingActivity.this, "测试：" + m.actionLabel, Toast.LENGTH_SHORT).show();
            }
            @Override public void onEditAction(KeyMappingUtil.KeyMapping m) {
                startEditAction(m);
            }
        });
        rv.setAdapter(adapter);

        // 添加按钮
        findViewById(R.id.btn_add).setOnClickListener(v -> startAddMapping());

        // 顶部菜单按钮
        findViewById(R.id.btn_menu).setOnClickListener(v -> showOverflowMenu(v));

        // 权限状态卡
        initPermissionCard();

        loadMappings();
    }

    /** 初始化权限状态引导卡 */
    private void initPermissionCard() {
        cardPermissions = findViewById(R.id.card_permissions);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvNotificationStatus = findViewById(R.id.tv_notification_status);
        btnAccessibilityGo = findViewById(R.id.btn_accessibility_go);
        btnNotificationGo = findViewById(R.id.btn_notification_go);
        ivAccessibilityIcon = findViewById(R.id.iv_accessibility_icon);
        ivNotificationIcon = findViewById(R.id.iv_notification_icon);

        // 点击整行也跳转
        View rowAccessibility = findViewById(R.id.row_accessibility);
        View rowNotification = findViewById(R.id.row_notification);

        View.OnClickListener goAccessibility = v -> {
            PermissionUtil.requestAccessibilityPermission(this);
            Toast.makeText(this, R.string.keymap_perm_accessibility_tip,
                    Toast.LENGTH_LONG).show();
        };
        View.OnClickListener goNotification = v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                notificationPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Android 13 以下：通知权限默认开启，跳转到应用详情页
                try {
                    Intent it = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    it.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                } catch (Exception e) {
                    Toast.makeText(this, R.string.keymap_perm_notification_not_required,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        btnAccessibilityGo.setOnClickListener(goAccessibility);
        rowAccessibility.setOnClickListener(goAccessibility);
        btnNotificationGo.setOnClickListener(goNotification);
        rowNotification.setOnClickListener(goNotification);
    }

    /** 刷新权限状态卡：仅在未授权时显示 */
    private void refreshPermissionCard() {
        boolean accOn = PermissionUtil.isAccessibilityEnabled(this);
        boolean notOn = PermissionUtil.hasNotificationPermission(this);

        // 任一权限未授权则显示卡片
        boolean showCard = !accOn || !notOn;
        cardPermissions.setVisibility(showCard ? View.VISIBLE : View.GONE);

        // 无障碍状态
        int okColor = ContextCompat.getColor(this, R.color.success);
        int noColor = ContextCompat.getColor(this, R.color.danger);
        if (accOn) {
            tvAccessibilityStatus.setText(R.string.keymap_perm_granted);
            tvAccessibilityStatus.setTextColor(okColor);
            ivAccessibilityIcon.setColorFilter(okColor);
            btnAccessibilityGo.setVisibility(View.GONE);
        } else {
            tvAccessibilityStatus.setText(R.string.keymap_perm_not_granted);
            tvAccessibilityStatus.setTextColor(noColor);
            ivAccessibilityIcon.setColorFilter(noColor);
            btnAccessibilityGo.setVisibility(View.VISIBLE);
        }

        // 通知状态
        if (notOn) {
            tvNotificationStatus.setText(R.string.keymap_perm_granted);
            tvNotificationStatus.setTextColor(okColor);
            ivNotificationIcon.setColorFilter(okColor);
            btnNotificationGo.setVisibility(View.GONE);
        } else {
            tvNotificationStatus.setText(R.string.keymap_perm_not_granted);
            tvNotificationStatus.setTextColor(noColor);
            ivNotificationIcon.setColorFilter(noColor);
            btnNotificationGo.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 用户从系统设置返回后刷新权限状态
        refreshPermissionCard();
    }

    private void loadMappings() {
        mappings.clear();
        mappings.addAll(KeyMappingUtil.getAllMappings(this));
        adapter.setData(mappings);
        tvEmpty.setVisibility(mappings.isEmpty() ? View.VISIBLE : View.GONE);
        rv.setVisibility(mappings.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 顶部菜单：导入/导出/清空/预设模板 */
    private void showOverflowMenu(View v) {
        PopupMenu pm = new PopupMenu(this, v);
        pm.getMenu().add(0, 1, 0, "导出配置");
        pm.getMenu().add(0, 2, 0, "导入配置");
        pm.getMenu().add(0, 3, 0, "应用预设模板");
        pm.getMenu().add(0, 4, 0, "清空全部");
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                createFileLauncher.launch("key_mapping_backup.json");
                return true;
            } else if (id == 2) {
                openFileLauncher.launch("application/json");
                return true;
            } else if (id == 3) {
                showPresetPicker();
                return true;
            } else if (id == 4) {
                confirmClearAll();
                return true;
            }
            return false;
        });
        pm.show();
    }

    /** 选择并应用预设模板 */
    private void showPresetPicker() {
        List<KeyMappingUtil.Preset> presets = KeyMappingUtil.getBuiltinPresets();
        String[] items = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            KeyMappingUtil.Preset p = presets.get(i);
            items[i] = p.name + "\n  " + p.desc;
        }
        new AlertDialog.Builder(this)
                .setTitle("应用预设模板（会覆盖同按键同触发模式的映射）")
                .setItems(items, (d, w) -> {
                    KeyMappingUtil.Preset p = presets.get(w);
                    confirmApplyPreset(p);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmApplyPreset(KeyMappingUtil.Preset p) {
        ConfirmDialog.show(this,
                R.drawable.ic_feature_keymap, ConfirmDialog.TYPE_WARN,
                "应用预设",
                "确定应用「" + p.name + "」？\n\n"
                        + "说明：此预设包含 " + p.mappings.length + " 条映射，"
                        + "将覆盖同按键同触发模式下的现有映射，其他映射不受影响。",
                "应用",
                () -> {
                    KeyMappingUtil.applyPreset(this, p);
                    loadMappings();
                    Toast.makeText(this, "已应用预设：" + p.name, Toast.LENGTH_SHORT).show();
                });
    }

    // ============ 添加流程 ============

    /** 添加映射：先录制按键 */
    private void startAddMapping() {
        KeyCaptureDialog dialog = new KeyCaptureDialog(this, new KeyCaptureDialog.Callback() {
            @Override public void onSingleKeyCaptured(int keyCode) {
                // 选触发模式
                chooseTrigger(keyCode, 0);
            }
            @Override public void onComboCaptured(int key1, int key2) {
                // 组合键：直接选动作
                chooseActionForCombo(key1, key2);
            }
        });
        dialog.show();
    }

    /** 选择触发模式 */
    private void chooseTrigger(int keyCode, int comboKey) {
        String[] triggers = {"单击", "双击", "长按"};
        new AlertDialog.Builder(this)
                .setTitle("触发模式：" + KeyMappingUtil.getKeyLabel(keyCode))
                .setItems(triggers, (d, w) -> {
                    int trigger = w == 0 ? KeyMappingUtil.TRIGGER_TAP
                            : w == 1 ? KeyMappingUtil.TRIGGER_DOUBLE_TAP
                            : KeyMappingUtil.TRIGGER_LONG_PRESS;
                    // 检查是否已存在
                    if (KeyMappingUtil.getMapping(this, keyCode, trigger) != null) {
                        Toast.makeText(this, "该按键的此触发模式已存在映射", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    chooseAction(keyCode, trigger);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 选择动作（单键） */
    private void chooseAction(int keyCode, int trigger) {
        showActionPicker("选择动作", actionType -> handleActionInput(actionType,
                (data, label, targetPkg) -> saveSingle(keyCode, trigger, actionType, data, label, targetPkg)));
    }

    /** 选择动作（组合键） */
    private void chooseActionForCombo(int key1, int key2) {
        showActionPicker("选择动作（" + KeyMappingUtil.getKeyLabel(key1) + " + " + KeyMappingUtil.getKeyLabel(key2) + "）",
                actionType -> handleActionInput(actionType,
                        (data, label, targetPkg) -> saveCombo(key1, key2, actionType, data, label, targetPkg)));
    }

    /** 根据动作类型决定后续输入流程 */
    private void handleActionInput(int actionType, ActionSaveCallback cb) {
        android.util.Log.d("KeyMapping", "handleActionInput: actionType=" + actionType
                + " isMedia=" + KeyMappingUtil.isMediaAction(actionType)
                + " isNeedApp=" + KeyMappingUtil.isActionNeedApp(actionType));
        if (KeyMappingUtil.isActionNeedApp(actionType)) {
            pickApp((pkg, label) -> cb.onActionReady(pkg, "打开 " + label, ""));
        } else if (KeyMappingUtil.isMediaAction(actionType)) {
            // 媒体控制类动作：询问是否指定定向目标应用
            chooseMediaTarget(actionType, targetPkg -> {
                String label = KeyMappingUtil.getActionLabel(actionType);
                if (targetPkg != null && !targetPkg.isEmpty()) {
                    label += " → " + getAppLabel(targetPkg);
                }
                cb.onActionReady("", label, targetPkg != null ? targetPkg : "");
            });
        } else if (KeyMappingUtil.isActionNeedIntent(actionType)
                || KeyMappingUtil.isActionNeedUrl(actionType)
                || KeyMappingUtil.isActionNeedBroadcast(actionType)) {
            inputActionData(actionType, data -> cb.onActionReady(data, buildActionLabel(actionType, data), ""));
        } else if (KeyMappingUtil.isActionNeedNumber(actionType)) {
            inputNumber(actionType, num -> cb.onActionReady(num, buildActionLabel(actionType, num), ""));
        } else {
            cb.onActionReady("", KeyMappingUtil.getActionLabel(actionType), "");
        }
    }

    /**
     * 媒体动作目标应用选择器。
     * 弹出二选一对话框：
     * - 当前播放器（系统默认）：由系统路由到当前活跃播放器（targetPackage=""）
     * - 选择指定应用：进入应用选择弹窗，选定后定向派发到该应用
     */
    private void chooseMediaTarget(int actionType, MediaTargetCallback cb) {
        android.util.Log.d("KeyMapping", "chooseMediaTarget: actionType=" + actionType);
        // 注意：AlertDialog 的 setMessage 和 setItems 互斥，同时调用时 items 会被隐藏。
        // 改用自定义布局：顶部显示提示文字，下方是两个可选按钮 + 取消。
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_media_target, null);
        TextView tvHint = view.findViewById(R.id.tv_media_target_hint);
        View btnAny = view.findViewById(R.id.btn_target_any);
        View btnPick = view.findViewById(R.id.btn_target_pick);
        tvHint.setText(getString(R.string.media_target_app_hint));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.media_target_app))
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();

        btnAny.setOnClickListener(v -> {
            android.util.Log.d("KeyMapping", "chooseMediaTarget: 选当前播放器");
            dialog.dismiss();
            cb.onTargetSelected("");
        });
        btnPick.setOnClickListener(v -> {
            android.util.Log.d("KeyMapping", "chooseMediaTarget: 选指定应用");
            dialog.dismiss();
            // 可选模式：显示"不选，用系统默认"按钮，允许跳过
            pickApp((pkg, label) -> cb.onTargetSelected(pkg),
                    true, getString(R.string.media_target_skip));
        });
    }

    interface MediaTargetCallback { void onTargetSelected(String targetPackage); }

    /** 获取应用可读名称（用于动作标签展示） */
    private String getAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** 构建动作显示标签（针对带数据的动作） */
    private String buildActionLabel(int actionType, String data) {
        switch (actionType) {
            case KeyMappingUtil.ACTION_OPEN_ACTIVITY: return "打开 " + data;
            case KeyMappingUtil.ACTION_OPEN_URL: return "网址 " + data;
            case KeyMappingUtil.ACTION_SEND_BROADCAST: return "广播 " + data;
            case KeyMappingUtil.ACTION_SET_VOLUME: return "音量 " + data + "%";
            default: return KeyMappingUtil.getActionLabel(actionType);
        }
    }

    interface ActionSaveCallback { void onActionReady(String data, String label, String targetPackage); }

    /** 动作选择器（按分类分组） */
    private void showActionPicker(String title, ActionSelectedCallback cb) {
        // 按分类组织动作
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        int[] all = KeyMappingUtil.getAllActionTypes();
        for (int t : all) {
            String cat = KeyMappingUtil.getActionCategory(t);
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(t);
        }

        // 构造展开列表：分类标题 + 动作项
        List<Object> items = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : grouped.entrySet()) {
            items.add(e.getKey());  // 分类标题
            items.addAll(e.getValue());  // 动作 int
        }

        // 构造字符串数组
        String[] arr = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Object o = items.get(i);
            arr[i] = o instanceof String ? (String) o : "    " + KeyMappingUtil.getActionLabel((Integer) o);
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(arr, (d, which) -> {
                    Object o = items.get(which);
                    if (o instanceof Integer) cb.onActionSelected((Integer) o);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    interface ActionSelectedCallback { void onActionSelected(int actionType); }

    /** 选择应用（必选模式：点击应用立即回调并关闭） */
    private void pickApp(AppPickedCallback cb) {
        pickApp(cb, false, null);
    }

    /**
     * 选择应用
     * @param cb 回调
     * @param optional true=可选模式（显示确认按钮，允许不选直接确认）；false=必选（点击即选）
     * @param confirmText 可选模式下的确认按钮文字；为 null 时使用默认"确定"
     */
    private void pickApp(AppPickedCallback cb, boolean optional, String confirmText) {
        android.util.Log.d("KeyMapping", "pickApp: optional=" + optional + " confirmText=" + confirmText);
        // 复用悬浮球/自启管理的应用选择弹窗
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pick_app, null);
        RecyclerView rvPick = view.findViewById(R.id.rv_pick);
        rvPick.setLayoutManager(new LinearLayoutManager(this));

        TextView tvTitle = view.findViewById(R.id.tv_pick_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_pick_subtitle);
        tvTitle.setText("选择应用");
        tvSubtitle.setText(optional ? "点击选择目标应用，或点下方按钮跳过"
                                     : "点击选择要启动的应用");

        com.carassistant.adapter.PickAppAdapter adapter = new com.carassistant.adapter.PickAppAdapter();
        rvPick.setAdapter(adapter);

        EditText etSearch = view.findViewById(R.id.et_pick_search);
        ImageView ivClear = view.findViewById(R.id.iv_pick_clear);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        dialog.show();

        adapter.setOnAppClickListener((position, info) -> {
            cb.onAppPicked(info.packageName, info.name);
            dialog.dismiss();
        });

        view.findViewById(R.id.btn_pick_cancel).setOnClickListener(v -> dialog.dismiss());

        // 可选模式：显示确认按钮，点击则回调空（表示不选，用系统默认）
        android.widget.Button btnConfirm = view.findViewById(R.id.btn_pick_confirm);
        if (optional) {
            btnConfirm.setVisibility(View.VISIBLE);
            btnConfirm.setText(confirmText != null ? confirmText : "确定");
            btnConfirm.setOnClickListener(v -> {
                cb.onAppPicked("", "");
                dialog.dismiss();
            });
        } else {
            btnConfirm.setVisibility(View.GONE);
        }

        // 异步加载应用列表：用 getInstalledApplications + getLaunchIntentForPackage 过滤
        // （与 AutostartActivity 一致，规避 Android 11+ queryIntentActivities 可见性限制）
        final Context appCtx = getApplicationContext();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            List<com.carassistant.util.AppUtil.AppInfo> result = new ArrayList<>();
            try {
                PackageManager pm = appCtx.getPackageManager();
                List<ApplicationInfo> ais = pm.getInstalledApplications(0);
                android.util.Log.d("KeyMapping", "pickApp: getInstalledApplications 返回 " + ais.size() + " 个应用");
                for (ApplicationInfo ai : ais) {
                    try {
                        // 仅保留可启动的应用（有 LAUNCHER 入口）
                        if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                        com.carassistant.util.AppUtil.AppInfo info = new com.carassistant.util.AppUtil.AppInfo();
                        info.packageName = ai.packageName;
                        info.name = pm.getApplicationLabel(ai).toString();
                        info.icon = pm.getApplicationIcon(ai);
                        info.launchIntent = pm.getLaunchIntentForPackage(ai.packageName);
                        info.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        result.add(info);
                    } catch (Exception ignored) {}
                }
                Collections.sort(result, (a, b) -> {
                    // 用户应用优先，再按名称排序
                    if (a.system != b.system) return a.system ? 1 : -1;
                    return a.name.compareToIgnoreCase(b.name);
                });
                android.util.Log.d("KeyMapping", "pickApp: 可启动应用数 " + result.size());
            } catch (Exception e) {
                android.util.Log.e("KeyMapping", "pickApp: 加载应用列表失败", e);
            }

            final List<com.carassistant.util.AppUtil.AppInfo> all = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                adapter.setData(all);
                if (all.isEmpty()) {
                    tvSubtitle.setText("未获取到应用列表，请检查权限或反馈问题");
                }
                // 搜索过滤
                etSearch.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        String q = s.toString().trim().toLowerCase();
                        ivClear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                        List<com.carassistant.util.AppUtil.AppInfo> f = new ArrayList<>();
                        for (com.carassistant.util.AppUtil.AppInfo info : all) {
                            if (info.name.toLowerCase().contains(q) || info.packageName.toLowerCase().contains(q)) {
                                f.add(info);
                            }
                        }
                        adapter.setData(f);
                    }
                });
                ivClear.setOnClickListener(v -> etSearch.setText(""));
            });
        });
        executor.shutdown();
    }

    interface AppPickedCallback { void onAppPicked(String pkg, String label); }

    /** 通用输入对话框：用于 Intent / URL / 广播 */
    private void inputActionData(int actionType, ActionDataCallback cb) {
        final EditText et = new EditText(this);
        et.setHint(KeyMappingUtil.getActionInputHint(actionType));
        new AlertDialog.Builder(this)
                .setTitle(KeyMappingUtil.getActionInputTitle(actionType))
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) cb.onDataInput(s);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 数字输入对话框：用于音量百分比等 */
    private void inputNumber(int actionType, ActionDataCallback cb) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint(KeyMappingUtil.getActionInputHint(actionType));
        new AlertDialog.Builder(this)
                .setTitle(KeyMappingUtil.getActionInputTitle(actionType))
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    String s = et.getText().toString().trim();
                    if (!s.isEmpty()) {
                        try {
                            int n = Integer.parseInt(s);
                            if (actionType == KeyMappingUtil.ACTION_SET_VOLUME) {
                                n = Math.max(0, Math.min(100, n));
                            }
                            cb.onDataInput(String.valueOf(n));
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    interface ActionDataCallback { void onDataInput(String data); }

    private void saveSingle(int keyCode, int trigger, int actionType, String data, String label, String targetPackage) {
        KeyMappingUtil.saveMapping(this, keyCode, trigger, actionType, data, label, targetPackage);
        loadMappings();
        Toast.makeText(this, "映射已添加", Toast.LENGTH_SHORT).show();
    }

    private void saveCombo(int key1, int key2, int actionType, String data, String label, String targetPackage) {
        KeyMappingUtil.saveComboMapping(this, key1, key2, actionType, data, label, targetPackage);
        loadMappings();
        Toast.makeText(this, "组合键映射已添加", Toast.LENGTH_SHORT).show();
    }

    // ============ 编辑流程 ============

    /** 编辑现有映射的动作（保留原按键和触发模式） */
    private void startEditAction(KeyMappingUtil.KeyMapping m) {
        showActionPicker("修改动作：" + m.keyLabel, actionType ->
                handleActionInput(actionType, (data, label, targetPkg) -> {
                    // 删除旧映射
                    KeyMappingUtil.removeMapping(this, m);
                    // 保存新映射（保留原按键和触发模式）
                    if (m.comboKeyCode != 0) {
                        KeyMappingUtil.saveComboMapping(this, m.keyCode, m.comboKeyCode,
                                actionType, data, label, targetPkg);
                    } else {
                        KeyMappingUtil.saveMapping(this, m.keyCode, m.trigger,
                                actionType, data, label, targetPkg);
                    }
                    loadMappings();
                    Toast.makeText(this, "动作已更新", Toast.LENGTH_SHORT).show();
                }));
    }

    // ============ 删除/清空 ============

    private void confirmDelete(KeyMappingUtil.KeyMapping m) {
        ConfirmDialog.show(this,
                R.drawable.ic_panel_close, ConfirmDialog.TYPE_DANGER,
                "删除映射",
                "确定删除「" + m.keyLabel + " → " + m.actionLabel + "」？",
                "删除",
                () -> {
                    KeyMappingUtil.removeMapping(this, m);
                    loadMappings();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmClearAll() {
        ConfirmDialog.show(this,
                R.drawable.ic_panel_close, ConfirmDialog.TYPE_DANGER,
                "清空全部",
                "确定清空所有按键映射？此操作不可撤销。",
                "清空",
                () -> {
                    KeyMappingUtil.clearAll(this);
                    loadMappings();
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                });
    }

    // ============ 导入/导出 ============

    /** 导出文件回调 */
    private void onExportFile(Uri uri) {
        if (uri == null) return;
        try {
            String json = KeyMappingUtil.exportMappings(this);
            if (json == null) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show(); return; }
            android.content.ContentResolver cr = getContentResolver();
            android.os.ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "w");
            if (pfd == null) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show(); return; }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
            fos.write(json.getBytes("UTF-8"));
            fos.close();
            pfd.close();
            Toast.makeText(this, "已导出 " + mappings.size() + " 条映射", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 导入文件回调 */
    private void onImportFile(Uri uri) {
        if (uri == null) return;
        try {
            android.content.ContentResolver cr = getContentResolver();
            java.io.InputStream is = cr.openInputStream(uri);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            is.close();
            String json = new String(bos.toByteArray(), "UTF-8");
            if (KeyMappingUtil.importMappings(this, json)) {
                loadMappings();
                Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导入失败：文件格式错误", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- Adapter ----------------

    private static class KeyMappingAdapter extends RecyclerView.Adapter<KeyMappingAdapter.VH> {
        private final List<KeyMappingUtil.KeyMapping> items = new ArrayList<>();
        private OnMappingActionListener listener;

        interface OnMappingActionListener {
            void onDelete(KeyMappingUtil.KeyMapping m);
            void onToggleEnabled(KeyMappingUtil.KeyMapping m, boolean on);
            void onTest(KeyMappingUtil.KeyMapping m);
            void onEditAction(KeyMappingUtil.KeyMapping m);
        }

        void setListener(OnMappingActionListener l) { this.listener = l; }

        void setData(List<KeyMappingUtil.KeyMapping> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_key_mapping, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            KeyMappingUtil.KeyMapping m = items.get(position);
            holder.tvKey.setText(m.keyLabel);
            holder.tvAction.setText(m.actionLabel);

            // 显示触发模式标签
            if (m.comboKeyCode == 0 && m.trigger != KeyMappingUtil.TRIGGER_TAP) {
                holder.tvTrigger.setVisibility(View.VISIBLE);
                holder.tvTrigger.setText(KeyMappingUtil.getTriggerLabel(m.trigger));
            } else {
                holder.tvTrigger.setVisibility(View.GONE);
            }

            // 分类标签 + 数据
            String category = KeyMappingUtil.getActionCategory(m.actionType);
            boolean hasData = m.actionData != null && !m.actionData.isEmpty();
            if (hasData || m.comboKeyCode != 0) {
                holder.llMeta.setVisibility(View.VISIBLE);
                holder.tvCategory.setText(category);
                if (hasData) {
                    holder.tvData.setVisibility(View.VISIBLE);
                    holder.tvData.setText(m.actionData);
                } else {
                    holder.tvData.setVisibility(View.GONE);
                }
            } else {
                holder.llMeta.setVisibility(View.VISIBLE);
                holder.tvCategory.setText(category);
                holder.tvData.setVisibility(View.GONE);
            }

            // 启用开关
            holder.swEnabled.setOnCheckedChangeListener(null);
            holder.swEnabled.setChecked(m.enabled);
            holder.swEnabled.setOnCheckedChangeListener((b, checked) -> {
                if (listener != null) listener.onToggleEnabled(m, checked);
            });

            // 删除按钮
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(m);
            });

            // 测试按钮
            holder.btnTest.setOnClickListener(v -> {
                if (listener != null) listener.onTest(m);
            });

            // 长按编辑动作
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onEditAction(m);
                    return true;
                }
                return false;
            });

            // 禁用时降低透明度
            holder.itemView.setAlpha(m.enabled ? 1.0f : 0.5f);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvKey, tvAction, tvTrigger, tvCategory, tvData;
            View llMeta, btnDelete, btnTest, itemView;
            SwitchCompat swEnabled;
            VH(View v) {
                super(v);
                itemView = v;
                tvKey = v.findViewById(R.id.tv_key);
                tvAction = v.findViewById(R.id.tv_action);
                tvTrigger = v.findViewById(R.id.tv_trigger);
                tvCategory = v.findViewById(R.id.tv_category);
                tvData = v.findViewById(R.id.tv_data);
                llMeta = v.findViewById(R.id.ll_meta);
                swEnabled = v.findViewById(R.id.sw_enabled);
                btnDelete = v.findViewById(R.id.btn_delete);
                btnTest = v.findViewById(R.id.btn_test);
            }
        }
    }

    // ---------------- 按键录制对话框 ----------------

    private static class KeyCaptureDialog extends AlertDialog {
        interface Callback {
            void onSingleKeyCaptured(int keyCode);
            void onComboCaptured(int key1, int key2);
        }

        private final Callback callback;
        private int firstKey = -1;
        private int secondKey = -1;
        private final List<Integer> pressed = new ArrayList<>();

        KeyCaptureDialog(KeyMappingActivity act, Callback cb) {
            super(act);
            this.callback = cb;
            setTitle("按键录制");
            setMessage("请按下要映射的物理按键...\n\n"
                    + "• 单键：按一次后点击「确定」\n"
                    + "• 组合键：按住一个键的同时按另一个键，然后点击「确定」\n\n"
                    + "支持的按键：媒体键 / 音量键 / F1-F12 / 数字键 / 方向键 / 字母键 / 手柄键等");
            setButton(BUTTON_POSITIVE, "确定", (d, w) -> {
                if (secondKey != -1) {
                    callback.onComboCaptured(firstKey, secondKey);
                } else if (firstKey != -1) {
                    callback.onSingleKeyCaptured(firstKey);
                }
                dismiss();
            });
            setButton(BUTTON_NEUTRAL, "手动输入", (d, w) -> showManualInput());
            setButton(BUTTON_NEGATIVE, "取消", (d, w) -> dismiss());
            setOnKeyListener((d, keyCode, event) -> {
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    if (!pressed.contains(keyCode)) {
                        pressed.add(keyCode);
                        if (firstKey == -1) {
                            firstKey = keyCode;
                            setMessage("已捕获按键：" + KeyMappingUtil.getKeyLabel(keyCode)
                                    + "\n\n可继续按其他按键组合，或点击「确定」。");
                        } else if (secondKey == -1 && keyCode != firstKey) {
                            secondKey = keyCode;
                            setMessage("已捕获组合键：\n"
                                    + KeyMappingUtil.getKeyLabel(firstKey) + " + "
                                    + KeyMappingUtil.getKeyLabel(secondKey)
                                    + "\n\n点击「确定」保存，或继续按其他键重新捕获。");
                        }
                    }
                    return true;
                } else if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    pressed.remove(Integer.valueOf(keyCode));
                    return true;
                }
                return false;
            });
        }

        private void showManualInput() {
            final EditText et = new EditText(getContext());
            et.setHint("输入 keyCode 数字（如 79=媒体播放）");
            new AlertDialog.Builder(getContext())
                    .setTitle("手动输入 keyCode")
                    .setView(et)
                    .setPositiveButton("确定", (d, w) -> {
                        try {
                            int code = Integer.parseInt(et.getText().toString().trim());
                            callback.onSingleKeyCaptured(code);
                            dismiss();
                        } catch (NumberFormatException e) {
                            Toast.makeText(getContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }
}
