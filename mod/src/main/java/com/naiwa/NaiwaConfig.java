package com.naiwa;

import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.ModConfigSpec;

/** 服务端统一决定整蛊调度和嘲笑出场距离；NeoForge 负责文件校验、存档覆盖及客户端同步。 */
public final class NaiwaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS;
    public static final ModConfigSpec.BooleanValue ALLOW_CONSECUTIVE_SAME_PRANK;
    public static final ModConfigSpec.DoubleValue RESPAWN_APPEAR_DISTANCE, TOTEM_LANDING_APPEAR_DISTANCE;
    // 单位格；低于场景演员的二十格离场边界，受阻候选距离也不能超过该上限。
    static final double MAX_APPEAR_DISTANCE = 16;
    static {
        var builder = new ModConfigSpec.Builder();
        MIN_INTERVAL_SECONDS = builder.comment("整蛊最小间隔，单位秒。与最大值相同则固定间隔；0 允许无额外等待，但不重叠演出。",
                "修改后用于下一次开始的整蛊，当前已排定的冷却不重置；最小值大于最大值时按两者较小/较大值取范围。")
                .translation("naiwa.configuration.prankIntervalMinSeconds").defineInRange("prankIntervalMinSeconds", 300, 0, 86400);
        MAX_INTERVAL_SECONDS = builder.comment("整蛊最大间隔，单位秒。默认 300～600 秒，即五至十分钟。",
                "仅影响十种普通整蛊，不改变首次十秒准备、复活/图腾嘲笑、盾牌现身或本体采气间隔。")
                .translation("naiwa.configuration.prankIntervalMaxSeconds").defineInRange("prankIntervalMaxSeconds", 600, 0, 86400);
        ALLOW_CONSECUTIVE_SAME_PRANK = builder.comment("允许同一种整蛊在下一轮再次触发；仍须冷却结束并满足原触发及安全条件。")
                .translation("naiwa.configuration.allowConsecutiveSamePrank").define("allowConsecutiveSamePrank", false);
        RESPAWN_APPEAR_DISTANCE = builder.comment("死亡实际复活后，奶蛙在玩家当前水平正前方的优先出场距离，单位格，允许小数。",
                "默认 2.5，范围 1.5～16；受阻时尝试增加 1 或 2 格，但不超过 16 格。仅影响后续出场，不挪动正在演出的奶蛙。")
                .translation("naiwa.configuration.respawnAppearDistance").defineInRange("respawnAppearDistance", 2.5, 1.5, MAX_APPEAR_DISTANCE);
        TOTEM_LANDING_APPEAR_DISTANCE = builder.comment("图腾飞升后真正落地时，奶蛙在玩家当前水平正前方的优先出场距离，单位格，允许小数。",
                "默认 2.5，范围 1.5～16；受阻时尝试增加 1 或 2 格，但不超过 16 格。不改变飞升高度、落地时序或免摔规则。")
                .translation("naiwa.configuration.totemLandingAppearDistance").defineInRange("totemLandingAppearDistance", 2.5, 1.5, MAX_APPEAR_DISTANCE);
        SPEC = builder.build();
    }
    private NaiwaConfig() {}

    /** 将秒换算成 20 Hz tick 后按闭区间抽签，保留原五至十分钟的分布；颠倒输入不会产生负随机范围。 */
    public static int nextIntervalTicks(RandomSource random) {
        int first = MIN_INTERVAL_SECONDS.get(), second = MAX_INTERVAL_SECONDS.get();
        int minimum = Math.min(first, second) * 20, maximum = Math.max(first, second) * 20;
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
