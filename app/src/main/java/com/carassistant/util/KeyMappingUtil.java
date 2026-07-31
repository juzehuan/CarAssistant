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

package com.carassistant.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 按键映射工具类
 *
 * 支持的触发模式：
 * - 单击：按下立即触发
 * - 双击：500ms 内连续按下两次
 * - 长按：按住超过 600ms
 *
 * 支持的组合键：最多 2 键组合（key1+key2）
 *
 * 动作类型：
 * - 打开应用 / 启动 Activity（自定义 Intent）
 * - 音量上/下 / 静音
 * - 媒体控制：播放/暂停、上一首、下一首、停止
 * - 开关 WiFi / 蓝牙 / 飞行模式 / 手电筒 / 自动亮度
 * - 屏幕亮度：增加 / 减小 / 最大
 * - 锁屏 / 截屏 / 清理内存
 * - 打开控制面板 / 返回首页 / 打开设置 / 打开文件管理
 * - 返回 / 最近任务
 *
 * 存储：SharedPreferences
 * - 单键映射 key="k:{keyCode}:{trigger}"，value=JSON
 * - 组合键映射 key="c:{key1}+{key2}"，value=JSON
 * - 导入/导出使用 JSON 字符串
 */
public final class KeyMappingUtil {

    private static final String PREFS = "key_mapping_prefs";

    private KeyMappingUtil() {}

    // ============ 触发模式 ============
    public static final int TRIGGER_TAP = 0;      // 单击
    public static final int TRIGGER_DOUBLE_TAP = 1; // 双击
    public static final int TRIGGER_LONG_PRESS = 2; // 长按

    /** 双击间隔（毫秒） */
    public static final long DOUBLE_TAP_TIMEOUT = 500;
    /** 长按阈值（毫秒） */
    public static final long LONG_PRESS_TIMEOUT = 600;

    // ============ 动作类型 ============
    // 应用启动类
    public static final int ACTION_OPEN_APP = 1;
    public static final int ACTION_OPEN_ACTIVITY = 2;  // 自定义 Intent（包名/类名）
    // 音量类
    public static final int ACTION_VOLUME_UP = 10;
    public static final int ACTION_VOLUME_DOWN = 11;
    public static final int ACTION_VOLUME_MUTE = 12;
    // 媒体控制类
    public static final int ACTION_MEDIA_PLAY_PAUSE = 20;
    public static final int ACTION_MEDIA_NEXT = 21;
    public static final int ACTION_MEDIA_PREVIOUS = 22;
    public static final int ACTION_MEDIA_STOP = 23;
    // 开关类
    public static final int ACTION_TOGGLE_WIFI = 30;
    public static final int ACTION_TOGGLE_BLUETOOTH = 31;
    public static final int ACTION_TOGGLE_AIRPLANE = 32;
    public static final int ACTION_TOGGLE_TORCH = 33;
    public static final int ACTION_TOGGLE_AUTO_BRIGHTNESS = 34;
    // 屏幕亮度类
    public static final int ACTION_BRIGHTNESS_UP = 40;
    public static final int ACTION_BRIGHTNESS_DOWN = 41;
    public static final int ACTION_BRIGHTNESS_MAX = 42;
    // 系统操作类
    public static final int ACTION_LOCK_SCREEN = 50;
    public static final int ACTION_SCREENSHOT = 51;
    public static final int ACTION_CLEAN_MEMORY = 52;
    public static final int ACTION_BACK = 53;
    public static final int ACTION_RECENT_TASKS = 54;
    public static final int ACTION_POWER_DIALOG = 55;
    // 车机助手内跳转
    public static final int ACTION_OPEN_CONTROL_PANEL = 60;
    public static final int ACTION_BACK_HOME = 61;
    public static final int ACTION_OPEN_SETTINGS = 62;
    public static final int ACTION_OPEN_FILE_MANAGER = 63;
    public static final int ACTION_OPEN_KEY_MAPPING = 64;
    public static final int ACTION_OPEN_DEVICE_INFO = 65;
    // 系统设置子页类
    public static final int ACTION_SETTINGS_WIFI = 70;
    public static final int ACTION_SETTINGS_BLUETOOTH = 71;
    public static final int ACTION_SETTINGS_DISPLAY = 72;
    public static final int ACTION_SETTINGS_SOUND = 73;
    public static final int ACTION_SETTINGS_APPS = 74;
    public static final int ACTION_SETTINGS_LOCATION = 75;
    public static final int ACTION_SETTINGS_DATE_TIME = 76;
    public static final int ACTION_SETTINGS_LANGUAGE = 77;
    public static final int ACTION_SETTINGS_ABOUT = 78;
    public static final int ACTION_SETTINGS_BATTERY = 79;
    // 常用系统应用类
    public static final int ACTION_OPEN_CAMERA = 80;
    public static final int ACTION_OPEN_CLOCK = 81;
    public static final int ACTION_OPEN_CALENDAR = 82;
    public static final int ACTION_OPEN_CALCULATOR = 83;
    public static final int ACTION_OPEN_BROWSER = 84;
    public static final int ACTION_OPEN_DIALER = 85;
    public static final int ACTION_OPEN_CONTACTS = 86;
    public static final int ACTION_OPEN_GALLERY = 87;
    // 高级动作类
    public static final int ACTION_OPEN_URL = 90;             // 需输入 URL
    public static final int ACTION_SEND_BROADCAST = 91;        // 需输入 action
    public static final int ACTION_SET_VOLUME = 92;            // 需输入 0-100
    public static final int ACTION_EXPAND_NOTIFICATIONS = 93;
    public static final int ACTION_EXPAND_QUICK_SETTINGS = 94;
    public static final int ACTION_TOGGLE_INPUT_METHOD = 95;
    public static final int ACTION_TOGGLE_ORIENTATION = 96;
    public static final int ACTION_KILL_CURRENT_APP = 97;
    public static final int ACTION_OPEN_VOICE_ASSISTANT = 98;
    public static final int ACTION_OPEN_SEARCH = 99;

    public static class KeyMapping {
        public int keyCode;         // 主键
        public int comboKeyCode;    // 组合键（0 表示无组合键）
        public int trigger = TRIGGER_TAP;  // 触发模式
        public String keyLabel;     // 按键名称
        public int actionType;
        public String actionData;   // 动作数据（如包名/类名）
        public String actionLabel;  // 动作显示名称
        public boolean enabled = true; // 是否启用
        /**
         * 媒体按键定向派发的目标应用包名（可选）。
         * 仅对媒体控制类动作（ACTION_MEDIA_*）有效：
         * - 空串：由系统路由到当前活跃播放器（原有行为）
         * - 非空：优先通过 TargetMediaSessionService 定向派发到该应用的 MediaController
         */
        public String targetPackage = "";
    }

    /** 构造单键映射的存储 key */
    private static String singleKey(int keyCode, int trigger) {
        return "k:" + keyCode + ":" + trigger;
    }

    /** 构造组合键映射的存储 key */
    private static String comboKey(int key1, int key2) {
        // 保证 key1 < key2，使组合键顺序无关
        int a = Math.min(key1, key2);
        int b = Math.max(key1, key2);
        return "c:" + a + "+" + b;
    }

    /** 获取所有按键映射 */
    public static List<KeyMapping> getAllMappings(Context ctx) {
        List<KeyMapping> list = new ArrayList<>();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, ?> all = sp.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                String key = entry.getKey();
                if (key == null || !key.startsWith("k:") && !key.startsWith("c:")) continue;
                JSONObject json = new JSONObject((String) entry.getValue());
                KeyMapping m = parseFromJson(key, json);
                if (m != null) list.add(m);
            } catch (Exception ignored) {}
        }
        // 排序：先按主键 keyCode，再按 trigger
        Collections.sort(list, (a, b) -> {
            int r = Integer.compare(a.keyCode, b.keyCode);
            if (r != 0) return r;
            return Integer.compare(a.trigger, b.trigger);
        });
        return list;
    }

    private static KeyMapping parseFromJson(String key, JSONObject json) {
        try {
            KeyMapping m = new KeyMapping();
            if (key.startsWith("c:")) {
                // 组合键 c:key1+key2
                String[] parts = key.substring(2).split("\\+");
                m.keyCode = Integer.parseInt(parts[0]);
                m.comboKeyCode = Integer.parseInt(parts[1]);
                m.trigger = TRIGGER_TAP; // 组合键只支持单击
            } else {
                // 单键 k:keyCode:trigger
                String[] parts = key.substring(2).split(":");
                m.keyCode = Integer.parseInt(parts[0]);
                m.trigger = parts.length > 1 ? Integer.parseInt(parts[1]) : TRIGGER_TAP;
                m.comboKeyCode = 0;
            }
            m.keyLabel = m.comboKeyCode == 0
                    ? getKeyLabel(m.keyCode)
                    : getKeyLabel(m.keyCode) + " + " + getKeyLabel(m.comboKeyCode);
            m.actionType = json.getInt("actionType");
            m.actionData = json.optString("actionData", "");
            m.actionLabel = json.optString("actionLabel", "");
            m.enabled = json.optBoolean("enabled", true);
            m.targetPackage = json.optString("targetPackage", "");
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** 保存单键映射 */
    public static void saveMapping(Context ctx, int keyCode, int trigger, int actionType,
                                   String actionData, String actionLabel) {
        saveMapping(ctx, keyCode, trigger, actionType, actionData, actionLabel, "");
    }

    /** 保存单键映射（带媒体定向目标包名） */
    public static void saveMapping(Context ctx, int keyCode, int trigger, int actionType,
                                   String actionData, String actionLabel, String targetPackage) {
        saveMappingInternal(ctx, singleKey(keyCode, trigger), 0, trigger,
                actionType, actionData, actionLabel, true, targetPackage);
    }

    /** 保存组合键映射 */
    public static void saveComboMapping(Context ctx, int key1, int key2, int actionType,
                                        String actionData, String actionLabel) {
        saveComboMapping(ctx, key1, key2, actionType, actionData, actionLabel, "");
    }

    /** 保存组合键映射（带媒体定向目标包名） */
    public static void saveComboMapping(Context ctx, int key1, int key2, int actionType,
                                        String actionData, String actionLabel, String targetPackage) {
        saveMappingInternal(ctx, comboKey(key1, key2), 0, TRIGGER_TAP,
                actionType, actionData, actionLabel, true, targetPackage);
    }

    private static void saveMappingInternal(Context ctx, String storeKey, int comboKeyCode,
                                            int trigger, int actionType,
                                            String actionData, String actionLabel,
                                            boolean enabled, String targetPackage) {
        try {
            JSONObject json = new JSONObject();
            json.put("actionType", actionType);
            json.put("actionData", actionData == null ? "" : actionData);
            json.put("actionLabel", actionLabel == null ? "" : actionLabel);
            json.put("enabled", enabled);
            json.put("trigger", trigger);
            json.put("combo", comboKeyCode);
            json.put("targetPackage", targetPackage == null ? "" : targetPackage);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(storeKey, json.toString())
                    .apply();
        } catch (JSONException ignored) {}
    }

    /** 删除单键映射 */
    public static void removeMapping(Context ctx, int keyCode, int trigger) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(singleKey(keyCode, trigger))
                .apply();
    }

    /** 删除组合键映射 */
    public static void removeComboMapping(Context ctx, int key1, int key2) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(comboKey(key1, key2))
                .apply();
    }

    /** 根据 KeyMapping 对象删除（自动判断单键/组合键） */
    public static void removeMapping(Context ctx, KeyMapping m) {
        if (m.comboKeyCode != 0) {
            removeComboMapping(ctx, m.keyCode, m.comboKeyCode);
        } else {
            removeMapping(ctx, m.keyCode, m.trigger);
        }
    }

    /** 删除某个按键的所有触发模式映射 */
    public static void removeAllMappingsForKey(Context ctx, int keyCode) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        for (int trigger = 0; trigger <= 2; trigger++) {
            ed.remove(singleKey(keyCode, trigger));
        }
        ed.apply();
    }

    /** 查询单键映射 */
    public static KeyMapping getMapping(Context ctx, int keyCode, int trigger) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String val = sp.getString(singleKey(keyCode, trigger), null);
        if (val == null) return null;
        try {
            return parseFromJson(singleKey(keyCode, trigger), new JSONObject(val));
        } catch (Exception e) {
            return null;
        }
    }

    /** 查询组合键映射 */
    public static KeyMapping getComboMapping(Context ctx, int key1, int key2) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String val = sp.getString(comboKey(key1, key2), null);
        if (val == null) return null;
        try {
            return parseFromJson(comboKey(key1, key2), new JSONObject(val));
        } catch (Exception e) {
            return null;
        }
    }

    /** 启用/禁用某个映射 */
    public static void setEnabled(Context ctx, KeyMapping m, boolean enabled) {
        if (m.comboKeyCode != 0) {
            saveMappingInternal(ctx, comboKey(m.keyCode, m.comboKeyCode), 0, TRIGGER_TAP,
                    m.actionType, m.actionData, m.actionLabel, enabled, m.targetPackage);
        } else {
            saveMappingInternal(ctx, singleKey(m.keyCode, m.trigger), 0, m.trigger,
                    m.actionType, m.actionData, m.actionLabel, enabled, m.targetPackage);
        }
    }

    /** 清空所有映射 */
    public static void clearAll(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    /** 导出全部映射为 JSON 字符串 */
    public static String exportMappings(Context ctx) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            JSONArray arr = new JSONArray();
            for (KeyMapping m : getAllMappings(ctx)) {
                JSONObject item = new JSONObject();
                item.put("keyCode", m.keyCode);
                item.put("comboKeyCode", m.comboKeyCode);
                item.put("trigger", m.trigger);
                item.put("actionType", m.actionType);
                item.put("actionData", m.actionData);
                item.put("actionLabel", m.actionLabel);
                item.put("enabled", m.enabled);
                item.put("targetPackage", m.targetPackage);
                arr.put(item);
            }
            root.put("mappings", arr);
            return root.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /** 从 JSON 字符串导入映射（覆盖现有） */
    public static boolean importMappings(Context ctx, String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("mappings");
            if (arr == null) return false;
            clearAll(ctx);
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                int keyCode = item.getInt("keyCode");
                int comboKeyCode = item.optInt("comboKeyCode", 0);
                int trigger = item.optInt("trigger", TRIGGER_TAP);
                int actionType = item.getInt("actionType");
                String actionData = item.optString("actionData", "");
                String actionLabel = item.optString("actionLabel", "");
                boolean enabled = item.optBoolean("enabled", true);
                String targetPackage = item.optString("targetPackage", "");

                JSONObject storeJson = new JSONObject();
                storeJson.put("actionType", actionType);
                storeJson.put("actionData", actionData);
                storeJson.put("actionLabel", actionLabel);
                storeJson.put("enabled", enabled);
                storeJson.put("trigger", trigger);
                storeJson.put("combo", comboKeyCode);
                storeJson.put("targetPackage", targetPackage);

                String storeKey;
                if (comboKeyCode != 0) {
                    storeKey = comboKey(keyCode, comboKeyCode);
                } else {
                    storeKey = singleKey(keyCode, trigger);
                }
                ed.putString(storeKey, storeJson.toString());
            }
            ed.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============ 按键标签 ============

    /** 获取按键可读名称 */
    public static String getKeyLabel(int keyCode) {
        switch (keyCode) {
            // 媒体键
            case KeyEvent.KEYCODE_MEDIA_PLAY: return "媒体播放";
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return "媒体暂停";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return "播放/暂停";
            case KeyEvent.KEYCODE_MEDIA_NEXT: return "下一首";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return "上一首";
            case KeyEvent.KEYCODE_MEDIA_STOP: return "停止";
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return "快进";
            case KeyEvent.KEYCODE_MEDIA_REWIND: return "快退";
            // 音量键
            case KeyEvent.KEYCODE_VOLUME_UP: return "音量+";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "音量-";
            case KeyEvent.KEYCODE_VOLUME_MUTE: return "静音";
            // 通话键
            case KeyEvent.KEYCODE_CALL: return "通话键";
            case KeyEvent.KEYCODE_ENDCALL: return "挂断键";
            // 系统键
            case KeyEvent.KEYCODE_HOME: return "Home 键";
            case KeyEvent.KEYCODE_BACK: return "返回键";
            case KeyEvent.KEYCODE_MENU: return "菜单键";
            case KeyEvent.KEYCODE_SEARCH: return "搜索键";
            case KeyEvent.KEYCODE_APP_SWITCH: return "最近任务";
            case KeyEvent.KEYCODE_POWER: return "电源键";
            case KeyEvent.KEYCODE_HEADSETHOOK: return "耳机键";
            case KeyEvent.KEYCODE_NOTIFICATION: return "通知键";
            case KeyEvent.KEYCODE_BRIGHTNESS_UP: return "亮度+";
            case KeyEvent.KEYCODE_BRIGHTNESS_DOWN: return "亮度-";
            // 符号键
            case KeyEvent.KEYCODE_STAR: return "* 键";
            case KeyEvent.KEYCODE_POUND: return "# 键";
            case KeyEvent.KEYCODE_PLUS: return "+ 键";
            case KeyEvent.KEYCODE_MINUS: return "- 键";
            case KeyEvent.KEYCODE_EQUALS: return "= 键";
            case KeyEvent.KEYCODE_AT: return "@ 键";
            // 方向键
            case KeyEvent.KEYCODE_DPAD_UP: return "方向上";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "方向下";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "方向左";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "方向右";
            case KeyEvent.KEYCODE_DPAD_CENTER: return "确认键";
            // 数字键
            case KeyEvent.KEYCODE_0: return "数字 0";
            case KeyEvent.KEYCODE_1: return "数字 1";
            case KeyEvent.KEYCODE_2: return "数字 2";
            case KeyEvent.KEYCODE_3: return "数字 3";
            case KeyEvent.KEYCODE_4: return "数字 4";
            case KeyEvent.KEYCODE_5: return "数字 5";
            case KeyEvent.KEYCODE_6: return "数字 6";
            case KeyEvent.KEYCODE_7: return "数字 7";
            case KeyEvent.KEYCODE_8: return "数字 8";
            case KeyEvent.KEYCODE_9: return "数字 9";
            // 字母键 A-Z
            case KeyEvent.KEYCODE_A: return "A";
            case KeyEvent.KEYCODE_B: return "B";
            case KeyEvent.KEYCODE_C: return "C";
            case KeyEvent.KEYCODE_D: return "D";
            case KeyEvent.KEYCODE_E: return "E";
            case KeyEvent.KEYCODE_F: return "F";
            case KeyEvent.KEYCODE_G: return "G";
            case KeyEvent.KEYCODE_H: return "H";
            case KeyEvent.KEYCODE_I: return "I";
            case KeyEvent.KEYCODE_J: return "J";
            case KeyEvent.KEYCODE_K: return "K";
            case KeyEvent.KEYCODE_L: return "L";
            case KeyEvent.KEYCODE_M: return "M";
            case KeyEvent.KEYCODE_N: return "N";
            case KeyEvent.KEYCODE_O: return "O";
            case KeyEvent.KEYCODE_P: return "P";
            case KeyEvent.KEYCODE_Q: return "Q";
            case KeyEvent.KEYCODE_R: return "R";
            case KeyEvent.KEYCODE_S: return "S";
            case KeyEvent.KEYCODE_T: return "T";
            case KeyEvent.KEYCODE_U: return "U";
            case KeyEvent.KEYCODE_V: return "V";
            case KeyEvent.KEYCODE_W: return "W";
            case KeyEvent.KEYCODE_X: return "X";
            case KeyEvent.KEYCODE_Y: return "Y";
            case KeyEvent.KEYCODE_Z: return "Z";
            // 功能键
            case KeyEvent.KEYCODE_F1: return "F1";
            case KeyEvent.KEYCODE_F2: return "F2";
            case KeyEvent.KEYCODE_F3: return "F3";
            case KeyEvent.KEYCODE_F4: return "F4";
            case KeyEvent.KEYCODE_F5: return "F5";
            case KeyEvent.KEYCODE_F6: return "F6";
            case KeyEvent.KEYCODE_F7: return "F7";
            case KeyEvent.KEYCODE_F8: return "F8";
            case KeyEvent.KEYCODE_F9: return "F9";
            case KeyEvent.KEYCODE_F10: return "F10";
            case KeyEvent.KEYCODE_F11: return "F11";
            case KeyEvent.KEYCODE_F12: return "F12";
            // 控制键
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_SPACE: return "空格";
            case KeyEvent.KEYCODE_ENTER: return "回车";
            case KeyEvent.KEYCODE_DEL: return "退格";
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "Ctrl 左";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "Ctrl 右";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "Shift 左";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "Shift 右";
            case KeyEvent.KEYCODE_ALT_LEFT: return "Alt 左";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "Alt 右";
            case KeyEvent.KEYCODE_CAPS_LOCK: return "Caps Lock";
            case KeyEvent.KEYCODE_SCROLL_LOCK: return "Scroll Lock";
            case KeyEvent.KEYCODE_NUM_LOCK: return "Num Lock";
            // 游戏手柄键
            case KeyEvent.KEYCODE_BUTTON_A: return "手柄 A";
            case KeyEvent.KEYCODE_BUTTON_B: return "手柄 B";
            case KeyEvent.KEYCODE_BUTTON_X: return "手柄 X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "手柄 Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "手柄 L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "手柄 R1";
            case KeyEvent.KEYCODE_BUTTON_START: return "手柄 Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "手柄 Select";
            // 车机专用键（常见厂商定义）
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS: return "导航前";
            case KeyEvent.KEYCODE_NAVIGATE_NEXT: return "导航后";
            default: return "按键 #" + keyCode;
        }
    }

    /** 获取触发模式可读名称 */
    public static String getTriggerLabel(int trigger) {
        switch (trigger) {
            case TRIGGER_TAP: return "单击";
            case TRIGGER_DOUBLE_TAP: return "双击";
            case TRIGGER_LONG_PRESS: return "长按";
            default: return "未知";
        }
    }

    /** 获取动作可读名称 */
    public static String getActionLabel(int actionType) {
        switch (actionType) {
            // 应用启动
            case ACTION_OPEN_APP: return "打开应用";
            case ACTION_OPEN_ACTIVITY: return "打开 Activity";
            // 音量
            case ACTION_VOLUME_UP: return "音量增大";
            case ACTION_VOLUME_DOWN: return "音量减小";
            case ACTION_VOLUME_MUTE: return "静音切换";
            // 媒体
            case ACTION_MEDIA_PLAY_PAUSE: return "播放/暂停";
            case ACTION_MEDIA_NEXT: return "下一首";
            case ACTION_MEDIA_PREVIOUS: return "上一首";
            case ACTION_MEDIA_STOP: return "停止播放";
            // 开关
            case ACTION_TOGGLE_WIFI: return "切换 WiFi";
            case ACTION_TOGGLE_BLUETOOTH: return "切换蓝牙";
            case ACTION_TOGGLE_AIRPLANE: return "切换飞行模式";
            case ACTION_TOGGLE_TORCH: return "切换手电筒";
            case ACTION_TOGGLE_AUTO_BRIGHTNESS: return "切换自动亮度";
            // 亮度
            case ACTION_BRIGHTNESS_UP: return "亮度增加";
            case ACTION_BRIGHTNESS_DOWN: return "亮度减小";
            case ACTION_BRIGHTNESS_MAX: return "亮度最大";
            // 系统
            case ACTION_LOCK_SCREEN: return "锁屏";
            case ACTION_SCREENSHOT: return "截屏";
            case ACTION_CLEAN_MEMORY: return "清理内存";
            case ACTION_BACK: return "返回";
            case ACTION_RECENT_TASKS: return "最近任务";
            case ACTION_POWER_DIALOG: return "电源菜单";
            // 车机助手内
            case ACTION_OPEN_CONTROL_PANEL: return "打开控制面板";
            case ACTION_BACK_HOME: return "返回首页";
            case ACTION_OPEN_SETTINGS: return "打开设置";
            case ACTION_OPEN_FILE_MANAGER: return "打开文件管理";
            case ACTION_OPEN_KEY_MAPPING: return "打开按键映射";
            case ACTION_OPEN_DEVICE_INFO: return "打开设备信息";
            // 系统设置子页
            case ACTION_SETTINGS_WIFI: return "WiFi 设置";
            case ACTION_SETTINGS_BLUETOOTH: return "蓝牙设置";
            case ACTION_SETTINGS_DISPLAY: return "显示设置";
            case ACTION_SETTINGS_SOUND: return "声音设置";
            case ACTION_SETTINGS_APPS: return "应用管理设置";
            case ACTION_SETTINGS_LOCATION: return "位置信息设置";
            case ACTION_SETTINGS_DATE_TIME: return "日期时间设置";
            case ACTION_SETTINGS_LANGUAGE: return "语言设置";
            case ACTION_SETTINGS_ABOUT: return "关于设备";
            case ACTION_SETTINGS_BATTERY: return "电池设置";
            // 常用系统应用
            case ACTION_OPEN_CAMERA: return "打开相机";
            case ACTION_OPEN_CLOCK: return "打开时钟";
            case ACTION_OPEN_CALENDAR: return "打开日历";
            case ACTION_OPEN_CALCULATOR: return "打开计算器";
            case ACTION_OPEN_BROWSER: return "打开浏览器";
            case ACTION_OPEN_DIALER: return "打开拨号盘";
            case ACTION_OPEN_CONTACTS: return "打开联系人";
            case ACTION_OPEN_GALLERY: return "打开图库";
            // 高级动作
            case ACTION_OPEN_URL: return "打开网址";
            case ACTION_SEND_BROADCAST: return "发送广播";
            case ACTION_SET_VOLUME: return "设置音量";
            case ACTION_EXPAND_NOTIFICATIONS: return "展开通知栏";
            case ACTION_EXPAND_QUICK_SETTINGS: return "展开快捷设置";
            case ACTION_TOGGLE_INPUT_METHOD: return "切换输入法";
            case ACTION_TOGGLE_ORIENTATION: return "切换屏幕方向";
            case ACTION_KILL_CURRENT_APP: return "结束当前应用";
            case ACTION_OPEN_VOICE_ASSISTANT: return "打开语音助手";
            case ACTION_OPEN_SEARCH: return "打开搜索";
            default: return "未知动作";
        }
    }

    /** 获取动作所属分类 */
    public static String getActionCategory(int actionType) {
        if (actionType == ACTION_OPEN_APP || actionType == ACTION_OPEN_ACTIVITY) return "应用启动";
        if (actionType >= 10 && actionType <= 12) return "音量";
        if (actionType >= 20 && actionType <= 23) return "媒体控制";
        if (actionType >= 30 && actionType <= 34) return "开关";
        if (actionType >= 40 && actionType <= 42) return "屏幕亮度";
        if (actionType >= 50 && actionType <= 55) return "系统操作";
        if (actionType >= 60 && actionType <= 65) return "车机助手";
        if (actionType >= 70 && actionType <= 79) return "系统设置";
        if (actionType >= 80 && actionType <= 87) return "常用应用";
        if (actionType >= 90 && actionType <= 99) return "高级动作";
        return "其他";
    }

    /** 获取所有可选动作列表 */
    public static int[] getAllActionTypes() {
        return new int[] {
                ACTION_OPEN_APP,
                ACTION_OPEN_ACTIVITY,
                ACTION_VOLUME_UP,
                ACTION_VOLUME_DOWN,
                ACTION_VOLUME_MUTE,
                ACTION_MEDIA_PLAY_PAUSE,
                ACTION_MEDIA_NEXT,
                ACTION_MEDIA_PREVIOUS,
                ACTION_MEDIA_STOP,
                ACTION_TOGGLE_WIFI,
                ACTION_TOGGLE_BLUETOOTH,
                ACTION_TOGGLE_AIRPLANE,
                ACTION_TOGGLE_TORCH,
                ACTION_TOGGLE_AUTO_BRIGHTNESS,
                ACTION_BRIGHTNESS_UP,
                ACTION_BRIGHTNESS_DOWN,
                ACTION_BRIGHTNESS_MAX,
                ACTION_LOCK_SCREEN,
                ACTION_SCREENSHOT,
                ACTION_CLEAN_MEMORY,
                ACTION_BACK,
                ACTION_RECENT_TASKS,
                ACTION_POWER_DIALOG,
                ACTION_OPEN_CONTROL_PANEL,
                ACTION_BACK_HOME,
                ACTION_OPEN_SETTINGS,
                ACTION_OPEN_FILE_MANAGER,
                ACTION_OPEN_KEY_MAPPING,
                ACTION_OPEN_DEVICE_INFO,
                ACTION_SETTINGS_WIFI,
                ACTION_SETTINGS_BLUETOOTH,
                ACTION_SETTINGS_DISPLAY,
                ACTION_SETTINGS_SOUND,
                ACTION_SETTINGS_APPS,
                ACTION_SETTINGS_LOCATION,
                ACTION_SETTINGS_DATE_TIME,
                ACTION_SETTINGS_LANGUAGE,
                ACTION_SETTINGS_ABOUT,
                ACTION_SETTINGS_BATTERY,
                ACTION_OPEN_CAMERA,
                ACTION_OPEN_CLOCK,
                ACTION_OPEN_CALENDAR,
                ACTION_OPEN_CALCULATOR,
                ACTION_OPEN_BROWSER,
                ACTION_OPEN_DIALER,
                ACTION_OPEN_CONTACTS,
                ACTION_OPEN_GALLERY,
                ACTION_OPEN_URL,
                ACTION_SEND_BROADCAST,
                ACTION_SET_VOLUME,
                ACTION_EXPAND_NOTIFICATIONS,
                ACTION_EXPAND_QUICK_SETTINGS,
                ACTION_TOGGLE_INPUT_METHOD,
                ACTION_TOGGLE_ORIENTATION,
                ACTION_KILL_CURRENT_APP,
                ACTION_OPEN_VOICE_ASSISTANT,
                ACTION_OPEN_SEARCH
        };
    }

    /** 判断该动作是否需要选择应用作为 actionData */
    public static boolean isActionNeedApp(int actionType) {
        return actionType == ACTION_OPEN_APP;
    }

    /** 判断该动作是否需要输入 Intent 作为 actionData */
    public static boolean isActionNeedIntent(int actionType) {
        return actionType == ACTION_OPEN_ACTIVITY;
    }

    /** 判断该动作是否需要输入 URL 作为 actionData */
    public static boolean isActionNeedUrl(int actionType) {
        return actionType == ACTION_OPEN_URL;
    }

    /** 判断该动作是否需要输入广播 action 作为 actionData */
    public static boolean isActionNeedBroadcast(int actionType) {
        return actionType == ACTION_SEND_BROADCAST;
    }

    /** 判断该动作是否需要输入数字参数（如音量百分比） */
    public static boolean isActionNeedNumber(int actionType) {
        return actionType == ACTION_SET_VOLUME;
    }

    /** 判断该动作是否需要任何输入数据 */
    public static boolean isActionNeedInput(int actionType) {
        return isActionNeedApp(actionType) || isActionNeedIntent(actionType)
                || isActionNeedUrl(actionType) || isActionNeedBroadcast(actionType)
                || isActionNeedNumber(actionType);
    }

    /** 判断该动作是否为媒体控制类（可选择定向目标应用） */
    public static boolean isMediaAction(int actionType) {
        return actionType == ACTION_MEDIA_PLAY_PAUSE
                || actionType == ACTION_MEDIA_NEXT
                || actionType == ACTION_MEDIA_PREVIOUS
                || actionType == ACTION_MEDIA_STOP;
    }

    /** 媒体动作是否支持选择定向目标应用（仅媒体控制类支持） */
    public static boolean isActionNeedMediaTarget(int actionType) {
        return isMediaAction(actionType);
    }

    /** 获取动作输入提示文案 */
    public static String getActionInputHint(int actionType) {
        switch (actionType) {
            case ACTION_OPEN_ACTIVITY: return "格式：包名/类名（如 com.android.settings/.Settings）";
            case ACTION_OPEN_URL: return "输入网址（如 https://www.example.com）";
            case ACTION_SEND_BROADCAST: return "输入广播 action（如 android.intent.action.AIRPLANE_MODE）";
            case ACTION_SET_VOLUME: return "输入音量百分比 0-100";
            default: return "";
        }
    }

    /** 获取动作输入对话框标题 */
    public static String getActionInputTitle(int actionType) {
        switch (actionType) {
            case ACTION_OPEN_ACTIVITY: return "输入 Activity";
            case ACTION_OPEN_URL: return "输入网址";
            case ACTION_SEND_BROADCAST: return "输入广播 action";
            case ACTION_SET_VOLUME: return "输入音量百分比";
            default: return "输入";
        }
    }

    // ============ 预设模板 ============

    /** 预设模板：方向盘按键常用配置 */
    public static class Preset {
        public final String name;
        public final String desc;
        public final KeyMapping[] mappings;
        public Preset(String name, String desc, KeyMapping[] mappings) {
            this.name = name; this.desc = desc; this.mappings = mappings;
        }
    }

    /** 获取内置预设模板列表 */
    public static List<Preset> getBuiltinPresets() {
        List<Preset> list = new ArrayList<>();
        // 模板1：车机方向盘按键通用方案
        list.add(new Preset(
                "车机方向盘通用方案",
                "上一首/下一首/音量+/音量-/播放暂停",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_MEDIA_NEXT, TRIGGER_TAP, ACTION_MEDIA_NEXT),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_PREVIOUS, TRIGGER_TAP, ACTION_MEDIA_PREVIOUS),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, TRIGGER_TAP, ACTION_MEDIA_PLAY_PAUSE),
                        buildPreset(KeyEvent.KEYCODE_VOLUME_UP, TRIGGER_TAP, ACTION_VOLUME_UP),
                        buildPreset(KeyEvent.KEYCODE_VOLUME_DOWN, TRIGGER_TAP, ACTION_VOLUME_DOWN),
                }));
        // 模板2：通话键方案
        list.add(new Preset(
                "通话键快捷方案",
                "接听/挂断/静音/免提",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_CALL, TRIGGER_TAP, ACTION_OPEN_DIALER),
                        buildPreset(KeyEvent.KEYCODE_ENDCALL, TRIGGER_TAP, ACTION_BACK_HOME),
                        buildPreset(KeyEvent.KEYCODE_HEADSETHOOK, TRIGGER_DOUBLE_TAP, ACTION_OPEN_VOICE_ASSISTANT),
                        buildPreset(KeyEvent.KEYCODE_HEADSETHOOK, TRIGGER_LONG_PRESS, ACTION_OPEN_SEARCH),
                }));
        // 模板3：媒体键方案
        list.add(new Preset(
                "媒体键完整方案",
                "播放暂停/上一首/下一首/停止/快进/快退",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, TRIGGER_TAP, ACTION_MEDIA_PLAY_PAUSE),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_PREVIOUS, TRIGGER_TAP, ACTION_MEDIA_PREVIOUS),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_NEXT, TRIGGER_TAP, ACTION_MEDIA_NEXT),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_STOP, TRIGGER_TAP, ACTION_MEDIA_STOP),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, TRIGGER_TAP, ACTION_BRIGHTNESS_UP),
                        buildPreset(KeyEvent.KEYCODE_MEDIA_REWIND, TRIGGER_TAP, ACTION_BRIGHTNESS_DOWN),
                }));
        // 模板4：快速开关方案
        list.add(new Preset(
                "快速开关方案",
                "WiFi/蓝牙/手电筒/飞行模式/自动亮度",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_F1, TRIGGER_TAP, ACTION_TOGGLE_WIFI),
                        buildPreset(KeyEvent.KEYCODE_F2, TRIGGER_TAP, ACTION_TOGGLE_BLUETOOTH),
                        buildPreset(KeyEvent.KEYCODE_F3, TRIGGER_TAP, ACTION_TOGGLE_TORCH),
                        buildPreset(KeyEvent.KEYCODE_F4, TRIGGER_TAP, ACTION_TOGGLE_AIRPLANE),
                        buildPreset(KeyEvent.KEYCODE_F5, TRIGGER_TAP, ACTION_TOGGLE_AUTO_BRIGHTNESS),
                        buildPreset(KeyEvent.KEYCODE_F6, TRIGGER_TAP, ACTION_EXPAND_QUICK_SETTINGS),
                }));
        // 模板5：系统操作方案
        list.add(new Preset(
                "系统操作方案",
                "锁屏/截屏/清理内存/最近任务/电源菜单",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_F7, TRIGGER_TAP, ACTION_LOCK_SCREEN),
                        buildPreset(KeyEvent.KEYCODE_F8, TRIGGER_TAP, ACTION_SCREENSHOT),
                        buildPreset(KeyEvent.KEYCODE_F9, TRIGGER_TAP, ACTION_CLEAN_MEMORY),
                        buildPreset(KeyEvent.KEYCODE_F10, TRIGGER_TAP, ACTION_RECENT_TASKS),
                        buildPreset(KeyEvent.KEYCODE_F11, TRIGGER_TAP, ACTION_POWER_DIALOG),
                        buildPreset(KeyEvent.KEYCODE_F12, TRIGGER_TAP, ACTION_EXPAND_NOTIFICATIONS),
                }));
        // 模板6：常用应用启动方案
        list.add(new Preset(
                "常用应用方案",
                "F1-F8 启动常用系统应用",
                new KeyMapping[] {
                        buildPreset(KeyEvent.KEYCODE_F1, TRIGGER_TAP, ACTION_OPEN_BROWSER),
                        buildPreset(KeyEvent.KEYCODE_F2, TRIGGER_TAP, ACTION_OPEN_DIALER),
                        buildPreset(KeyEvent.KEYCODE_F3, TRIGGER_TAP, ACTION_OPEN_CONTACTS),
                        buildPreset(KeyEvent.KEYCODE_F4, TRIGGER_TAP, ACTION_OPEN_CAMERA),
                        buildPreset(KeyEvent.KEYCODE_F5, TRIGGER_TAP, ACTION_OPEN_GALLERY),
                        buildPreset(KeyEvent.KEYCODE_F6, TRIGGER_TAP, ACTION_OPEN_CLOCK),
                        buildPreset(KeyEvent.KEYCODE_F7, TRIGGER_TAP, ACTION_OPEN_CALENDAR),
                        buildPreset(KeyEvent.KEYCODE_F8, TRIGGER_TAP, ACTION_OPEN_CALCULATOR),
                }));
        return list;
    }

    /** 构建预设映射项（不带应用数据） */
    private static KeyMapping buildPreset(int keyCode, int trigger, int actionType) {
        KeyMapping m = new KeyMapping();
        m.keyCode = keyCode;
        m.trigger = trigger;
        m.actionType = actionType;
        m.actionData = "";
        m.actionLabel = getActionLabel(actionType);
        m.enabled = true;
        m.comboKeyCode = 0;
        m.keyLabel = getKeyLabel(keyCode);
        return m;
    }

    /** 应用预设模板（覆盖同按键同触发模式的映射） */
    public static void applyPreset(Context ctx, Preset preset) {
        for (KeyMapping m : preset.mappings) {
            // 先删除已存在的同按键同触发模式映射
            removeMapping(ctx, m.keyCode, m.trigger);
            // 再保存新的映射
            saveMapping(ctx, m.keyCode, m.trigger, m.actionType, m.actionData, m.actionLabel);
        }
    }
}
