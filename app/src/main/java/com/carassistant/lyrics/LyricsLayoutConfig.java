package com.carassistant.lyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 自定义样式布局配置（1:1 复刻自歌词伴侣 LyricsLayoutConfig）。
 *
 * 每个 Item 描述一个 UI 元素（标题/封面/当前歌词/翻译/上一行/下一行/进度条/源信息）
 * 在面板中的相对位置（0~1）和启用状态。
 *
 * 默认配置提供一个合理的初始布局，用户可在 LayoutEditor 中拖拽调整（本项目暂未实现编辑器）。
 */
public final class LyricsLayoutConfig {

    public static final List<Item> DEFAULT = Collections.unmodifiableList(Arrays.asList(
            new Item("source", 0.06f, 0.06f, true),
            new Item("title", 0.06f, 0.14f, true),
            new Item("artist", 0.06f, 0.24f, true),
            new Item("cover", 0.06f, 0.40f, true),
            new Item("previous", 0.42f, 0.42f, true),
            new Item("current", 0.42f, 0.56f, true),
            new Item("translation", 0.42f, 0.74f, true),
            new Item("next", 0.42f, 0.84f, true),
            new Item("progress", 0.06f, 0.93f, true)
    ));

    private final List<Item> items;

    public LyricsLayoutConfig() {
        this(new ArrayList<>(DEFAULT));
    }

    public LyricsLayoutConfig(List<Item> items) {
        this.items = items == null ? new ArrayList<Item>() : new ArrayList<>(items);
    }

    public List<Item> items() {
        return items;
    }

    public static final class Item {
        public final String id;
        public final float x;
        public final float y;
        public final boolean enabled;

        public Item(String id, float x, float y, boolean enabled) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.enabled = enabled;
        }
    }
}
