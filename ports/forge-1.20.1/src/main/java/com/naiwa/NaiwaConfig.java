package com.naiwa;

import net.minecraft.util.RandomSource;
import net.minecraftforge.common.ForgeConfigSpec;

/** 服务端统一决定三项整蛊调度设置；Forge 负责旧字段清理、存档覆盖及客户端同步。 */
public final class NaiwaConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CONSECUTIVE_SAME_PRANK;
    static {
        var builder = new ForgeConfigSpec.Builder();
        MIN_INTERVAL_SECONDS = builder.comment("整蛊最小间隔，单位秒。与最大值相同则固定间隔；0 允许无额外等待，但不重叠演出。",
                "修改后用于下一次开始的整蛊，当前已排定的冷却不重置；最小值大于最大值时按两者较小/较大值取范围。")
                .translation("naiwa.configuration.prankIntervalMinSeconds").defineInRange("prankIntervalMinSeconds", 300, 0, 86400);
        MAX_INTERVAL_SECONDS = builder.comment("整蛊最大间隔，单位秒。默认 300～600 秒，即五至十分钟。",
                "仅影响十种普通整蛊，不改变首次十秒准备、复活/图腾嘲笑、盾牌现身或本体采气间隔。")
                .translation("naiwa.configuration.prankIntervalMaxSeconds").defineInRange("prankIntervalMaxSeconds", 600, 0, 86400);
        ALLOW_CONSECUTIVE_SAME_PRANK = builder.comment("允许同一种整蛊在下一轮再次触发；仍须冷却结束并满足原触发及安全条件。")
                .translation("naiwa.configuration.allowConsecutiveSamePrank").define("allowConsecutiveSamePrank", false);
        SPEC = builder.build();
    }
    private NaiwaConfig() {}

    static boolean allowConsecutive() {return ALLOW_CONSECUTIVE_SAME_PRANK.get();}

    /** 将秒换算成 20 Hz tick 后按闭区间抽签，保留原五至十分钟的分布；颠倒输入不会产生负随机范围。 */
    public static int nextIntervalTicks(RandomSource random) {
        int first = MIN_INTERVAL_SECONDS.get(), second = MAX_INTERVAL_SECONDS.get();
        int minimum = Math.min(first, second) * 20, maximum = Math.max(first, second) * 20;
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
