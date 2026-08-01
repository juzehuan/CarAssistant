package com.carassistant.lyrics;

import android.content.Context;
import android.util.Log;

import com.carassistant.lyrics.source.KugouLyricSource;
import com.carassistant.lyrics.source.KuwoLyricSource;
import com.carassistant.lyrics.source.NeteaseLyricSource;
import com.carassistant.lyrics.source.QQLyricSource;
import com.carassistant.lyrics.source.SodaLyricSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 多源歌词调度器（1:1 复刻自歌词伴侣 MultiSourceLyricClient）。
 *
 * 串行尝试多个源，第一个返回非空 timeline 的源即胜出。
 *
 * 默认顺序：netease → qqmusic → kugou → kuwo → soda
 * 用户可指定首选源，会排到最前面。
 */
public final class MultiSourceLyricClient {

    private static final String TAG = "MultiSourceLyricClient";

    private final List<LyricSource> sources;
    private final String preferredSource; // 用户首选源 ID，可为空

    public MultiSourceLyricClient(Context context) {
        this(context, "");
    }

    public MultiSourceLyricClient(Context context, String preferredSource) {
        this.sources = Arrays.asList(
                new NeteaseLyricSource(context),
                new QQLyricSource(context),
                new KugouLyricSource(context),
                new KuwoLyricSource(context),
                new SodaLyricSource(context)
        );
        this.preferredSource = preferredSource == null ? "" : preferredSource;
    }

    /** 获取所有源 */
    public List<LyricSource> getSources() {
        return sources;
    }

    /**
     * 加载歌词。
     *
     * @param currentSource 当前播放器源 ID（如 "netease"），可为空
     * @param mediaId       媒体 ID（NetEase/Soda 支持直查）
     * @param title         歌名
     * @param artist        歌手
     * @param durationMs    时长（毫秒）
     * @return 歌词结果，失败返回 {@link LyricResult#EMPTY}
     */
    public LyricResult load(String currentSource, String mediaId,
                            String title, String artist, long durationMs) {
        List<String> plan = buildPlan(currentSource, preferredSource);
        Log.d(TAG, "load plan: " + plan + " for " + title + " - " + artist);

        for (String sourceId : plan) {
            if (Thread.currentThread().isInterrupted()) {
                Log.d(TAG, "interrupted, abort");
                return LyricResult.EMPTY;
            }
            LyricSource src = findSource(sourceId);
            if (src == null) continue;

            String directMediaId = directMediaId(sourceId, currentSource, mediaId);
            try {
                LyricResult result = src.load(directMediaId, title, artist, durationMs);
                if (!result.isEmpty()) {
                    Log.d(TAG, "hit on " + sourceId);
                    return result;
                }
            } catch (Exception e) {
                Log.w(TAG, "source " + sourceId + " failed", e);
            }
        }
        return LyricResult.EMPTY;
    }

    private List<String> buildPlan(String currentSource, String preferred) {
        ArrayList<String> base = new ArrayList<>(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo", "soda"));
        ArrayList<String> plan = new ArrayList<>();

        if (preferred != null && !preferred.isEmpty() && !preferred.equals(currentSource)) {
            plan.add(preferred);
        }
        if (currentSource != null && !currentSource.isEmpty()) {
            if (!plan.contains(currentSource)) plan.add(currentSource);
        }
        for (String s : base) {
            if (!plan.contains(s)) plan.add(s);
        }
        return plan;
    }

    private LyricSource findSource(String id) {
        for (LyricSource s : sources) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    /** 仅当"当前播放器源 == 要尝试的源"且源是 NetEase 或 Soda 时，才直传 mediaId */
    private static String directMediaId(String targetSource, String currentSource, String mediaId) {
        if (currentSource != null && currentSource.equals(targetSource)
                && ("netease".equals(targetSource) || "soda".equals(targetSource))) {
            return mediaId == null ? "" : mediaId;
        }
        return "";
    }
}
