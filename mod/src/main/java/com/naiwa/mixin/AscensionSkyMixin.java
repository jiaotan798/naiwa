package com.naiwa.mixin;

import com.naiwa.client.NaiwaAscensionVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;

/** 覆盖本机天体绘制角度，日月、星空、云色与天空光照按午夜绘制；不改真实时间或存档。 */
@Mixin(ClientLevel.class)
abstract class AscensionSkyMixin implements LevelTimeAccess {
    // 原版从 LevelTimeAccess 继承此默认方法；新增覆盖，非飞升时严格沿用原版公式。
    @Override public float getTimeOfDay(float partialTick) {
        return (Object)this==Minecraft.getInstance().level&&NaiwaAscensionVisuals.active()?.5F:dimensionType().timeOfDay(dayTime());
    }
}
