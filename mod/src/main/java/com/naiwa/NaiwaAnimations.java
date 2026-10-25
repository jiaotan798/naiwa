package com.naiwa;

import java.util.List;

/** 动画名称及时长来自随附原模型；纯数据可供专服、客户端和结构检查共同使用。 */
public final class NaiwaAnimations {
    public static final List<String> NAMES = List.of("idle", "walk", "run", "jump", "wave", "laugh_enter", "laugh", "laugh_exit", "ascend_enter", "ascend_hover", "ascend_exit");
    private static final double[] LENGTHS = {2.4, 1.2, .8, 1.6, 2.4, .6, 7.04, .8, .6, 3, .6};
    private NaiwaAnimations() {}

    /** 返回源模型片段时长，完整大笑则返回三段之和，单位为秒。 */
    public static double length(String name) {
        // 完整演出按源片段求和；替换模型时不能沿用旧的七秒边界截掉表情收尾。
        if (name.equals("laugh_sequence")) return length("laugh_enter") + length("laugh") + length("laugh_exit");
        int index = NAMES.indexOf(name);
        if (index < 0) throw new IllegalArgumentException("未知奶蛙动画：" + name);
        return LENGTHS[index];
    }

    public static boolean loops(String name) {
        return name.equals("idle") || name.equals("walk") || name.equals("run") || name.equals("ascend_hover");
    }

    /** 普通交互串起入场、大笑和恢复；显式播放单段时仍保留原片段时长。 */
    public static ClipTime resolve(String name, double seconds) {
        if (!name.equals("laugh_sequence")) return new ClipTime(name, seconds);
        double enter = length("laugh_enter"), exit = enter + length("laugh");
        if (seconds < enter) return new ClipTime("laugh_enter", seconds);
        if (seconds < exit) return new ClipTime("laugh", seconds - enter);
        return new ClipTime("laugh_exit", Math.min(length("laugh_exit"), seconds - exit));
    }

    public record ClipTime(String name, double seconds) {}
}
