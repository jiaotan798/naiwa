package com.naiwa.client;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** 隔离验收用的渲染线程计时。主画面和实体阴影分别统计，不把异步 GPU 耗时算入。 */
final class NaiwaRenderProfile {
    static final boolean ENABLED = Boolean.getBoolean("naiwa.profileStages");
    private static final long[][] TIMES = new long[2][5];

    static void record(long skin, long transform, long submit, int faces) {
        var pass = TIMES[NaiwaShadowPass.active() ? 1 : 0];
        pass[0]++;
        pass[1] += skin;
        pass[2] += transform;
        pass[3] += submit;
        pass[4] += faces;
    }

    static void reset() {
        for (var pass : TIMES) Arrays.fill(pass, 0);
    }

    static Map<String, Long> snapshot() {
        var totals = new LinkedHashMap<String, Long>();
        String[] fields = {"draws", "skinNs", "transformNs", "submitNs", "faces"};
        for (int pass = 0; pass < TIMES.length; pass++) {
            String prefix = pass == 0 ? "main." : "shadow.";
            for (int i = 0; i < fields.length; i++) totals.put(prefix + fields[i], TIMES[pass][i]);
        }
        return totals;
    }

}
