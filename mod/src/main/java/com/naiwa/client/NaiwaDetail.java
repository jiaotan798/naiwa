package com.naiwa.client;

import com.naiwa.NaiwaEntity;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** 以主相机估算世界实体在屏幕上的高度，维持同一个实体的两档选择。 */
final class NaiwaDetail {
    private static final double STANDING_HEIGHT = 2.0;
    private static final double NEAR_DISTANCE_SQUARED = 6.0 * 6.0;
    private static final int SIMPLIFY_BELOW_PIXELS = 100;
    private static final int RESTORE_AT_PIXELS = 120;
    private final Set<NaiwaEntity> simplified = Collections.newSetFromMap(new WeakHashMap<>());

    /** 距离以格计，窗口高度为帧缓冲像素；两条阈值防止视角略变时反复切换。 */
    boolean useSimplified(NaiwaEntity entity, double cameraDistanceSquared, int renderHeight, double fovDegrees) {
        if (cameraDistanceSquared <= NEAR_DISTANCE_SQUARED || renderHeight <= 0) {
            simplified.remove(entity);
            return false;
        }
        double focalLength = renderHeight / (2 * Math.tan(Math.toRadians(fovDegrees / 2)));
        double pixels = STANDING_HEIGHT * focalLength / Math.sqrt(cameraDistanceSquared);
        int threshold = simplified.contains(entity) ? RESTORE_AT_PIXELS : SIMPLIFY_BELOW_PIXELS;
        if (pixels < threshold) {
            simplified.add(entity);
            return true;
        }
        simplified.remove(entity);
        return false;
    }
}
