package com.naiwa;

/** 琵琶曲的可选表现状态：十 tick 收放侧躺姿势；不改变飞行控制、存档或图腾飞升。 */
public final class NaiwaPipaFlight {
    private int previous, ticks;

    /** 侧躺模型头朝 -X；绕 Y 正转 90° 后头朝实体前方 +Z，起落沿半秒缓动同步转回。 */
    public static float headingOffset(NaiwaAnimations.ClipTime clip) {
        if (clip.name().equals("pipa_flight")) return 90;
        if (!clip.name().equals("pipa_enter")) return 0;
        double t = Math.max(0, Math.min(1, clip.seconds() / .5));
        return (float)(90 * t * t * (3 - 2 * t));
    }

    /** 每个实体 tick 调用一次；途中撤销标记时从当前姿势反向恢复，避免跳变。 */
    public void tick(boolean flying) {
        previous = ticks;
        ticks = Math.max(0, Math.min(10, ticks + (flying ? 1 : -1)));
    }

    /** 返回独立飞行动画；完全恢复后返回 null，由调用方继续原来的整蛊或行走动画。 */
    public NaiwaAnimations.ClipTime clip(float partial, double seconds) {
        float progress = previous + (ticks - previous) * Math.max(0, Math.min(1, partial));
        if (progress <= 0) return null;
        return progress >= 10 ? new NaiwaAnimations.ClipTime("pipa_flight", seconds)
                : new NaiwaAnimations.ClipTime("pipa_enter", progress / 20.0);
    }
}
