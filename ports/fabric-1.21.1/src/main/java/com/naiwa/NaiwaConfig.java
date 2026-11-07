package com.naiwa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.LevelResource;

/** 同一世界的三项整蛊设置由服务端读取，独立于客户端和其他存档。 */
final class NaiwaConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static int minimum = 300, maximum = 600;
    private static boolean consecutive;

    private NaiwaConfig() {}

    static void load(MinecraftServer server) {
        minimum = 300;
        maximum = 600;
        consecutive = false;
        var folder = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        var path = folder.resolve("naiwa-server.json");
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(folder);
                var defaults = new JsonObject();
                defaults.addProperty("prankIntervalMinSeconds",minimum);
                defaults.addProperty("prankIntervalMaxSeconds",maximum);
                defaults.addProperty("allowConsecutiveSamePrank",consecutive);
                Files.writeString(path,JSON.toJson(defaults));
                return;
            }
            try (var reader = Files.newBufferedReader(path)) {
                var values = JsonParser.parseReader(reader).getAsJsonObject();
                minimum = seconds(values,"prankIntervalMinSeconds",minimum);
                maximum = seconds(values,"prankIntervalMaxSeconds",maximum);
                var repeat = values.get("allowConsecutiveSamePrank");
                if (repeat != null && repeat.isJsonPrimitive() && repeat.getAsJsonPrimitive().isBoolean()) consecutive = repeat.getAsBoolean();
            }
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("读取奶蛙整蛊设置失败，使用默认值：{}",path,error);
        }
    }

    private static int seconds(JsonObject values, String key, int fallback) {
        var value = values.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        double seconds = value.getAsDouble();
        return Double.isFinite(seconds) && seconds == Math.rint(seconds) ? (int)Mth.clamp(seconds,0,86400) : fallback;
    }

    static boolean allowConsecutive() {return consecutive;}

    /** 配置使用秒，世界调度使用 20 Hz tick；随机区间包含两端。 */
    static int nextIntervalTicks(RandomSource random) {
        int lower = Math.min(minimum,maximum)*20, upper = Math.max(minimum,maximum)*20;
        return lower + random.nextInt(upper-lower+1);
    }
}
