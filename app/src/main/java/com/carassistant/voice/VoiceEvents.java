package com.carassistant.voice;

/* JADX INFO: loaded from: classes.dex */
public final class VoiceEvents {
    public static final String GEAR_P = "gear_p";
    public static final String GEAR_R = "gear_r";
    public static final String GEAR_N = "gear_n";
    public static final String GEAR_D = "gear_d";
    public static final String DOOR_FL_OPEN = "door_driver_open";
    public static final String DOOR_FL_CLOSE = "door_driver_close";
    public static final String DOOR_FR_OPEN = "door_passenger_open";
    public static final String DOOR_FR_CLOSE = "door_passenger_close";
    public static final String DOOR_RL_OPEN = "door_rl_open";
    public static final String DOOR_RL_CLOSE = "door_rl_close";
    public static final String DOOR_RR_OPEN = "door_rr_open";
    public static final String DOOR_RR_CLOSE = "door_rr_close";
    public static final String TURN_LEFT = "turn_left";
    public static final String TURN_RIGHT = "turn_right";
    public static final String HAZARD = "hazard";
    private static final Event[] ALL = {new Event(GEAR_P, "挂入 P 档", GEAR_P, false), new Event(GEAR_R, "挂入 R 档（循环）", GEAR_R, true), new Event(GEAR_N, "挂入 N 档", GEAR_N, false), new Event(GEAR_D, "挂入 D 档", GEAR_D, false), new Event(DOOR_FL_OPEN, "主驾开门时", DOOR_FL_OPEN, false), new Event(DOOR_FL_CLOSE, "主驾关门时", DOOR_FL_CLOSE, false), new Event(DOOR_FR_OPEN, "副驾开门时", DOOR_FR_OPEN, false), new Event(DOOR_FR_CLOSE, "副驾关门时", DOOR_FR_CLOSE, false), new Event(DOOR_RL_OPEN, "左后门开门时", DOOR_RL_OPEN, false), new Event(DOOR_RL_CLOSE, "左后门关门时", DOOR_RL_CLOSE, false), new Event(DOOR_RR_OPEN, "右后门开门时", DOOR_RR_OPEN, false), new Event(DOOR_RR_CLOSE, "右后门关门时", DOOR_RR_CLOSE, false), new Event(TURN_LEFT, "打左转向灯时", "asset:zhuanxiang_e28fc3c6_cd63a944810", false), new Event(TURN_RIGHT, "打右转向灯时", "asset:zhuanxiang_e28fc3c6_c0e1f2d3e1c", false), new Event(HAZARD, "开双闪时", "asset:zhuanxiang_e28fc3c6_c0e1f2d3e1c", false)};

    private VoiceEvents() {
    }

    public static final class Event {
        public final String defaultRaw;
        public final String key;
        public final boolean loopPreview;
        public final String title;

        public Event(String str, String str2, String str3, boolean z) {
            this.key = str;
            this.title = str2;
            this.defaultRaw = str3;
            this.loopPreview = z;
        }
    }

    public static Event[] all() {
        return ALL;
    }

    public static Event find(String str) {
        if (str == null) {
            return null;
        }
        int i = 0;
        while (true) {
            Event[] eventArr = ALL;
            if (i >= eventArr.length) {
                return null;
            }
            if (!str.equals(eventArr[i].key)) {
                i++;
            } else {
                return eventArr[i];
            }
        }
    }

    public static String prefKey(String str) {
        return "key_carvoice_clip_" + str;
    }

    public static String defaultClipId(Event event) {
        if (event == null || event.defaultRaw == null || event.defaultRaw.length() == 0) {
            return "";
        }
        if (event.defaultRaw.indexOf(58) >= 0) {
            return event.defaultRaw;
        }
        return "raw:" + event.defaultRaw;
    }
}
