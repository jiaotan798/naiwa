package com.naiwa;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** 范围效果只在服务端首次碰撞结算，不透墙、不控制玩家或免疫名单中的生物。 */
public final class LaughGasProjectile extends ThrowableItemProjectile {
    private boolean burst;
    public LaughGasProjectile(EntityType<? extends LaughGasProjectile> type, Level level) {super(type, level);}
    public LaughGasProjectile(EntityType<? extends LaughGasProjectile> type, LivingEntity owner, Level level) {super(type, owner, level);}
    @Override protected Item getDefaultItem() {return Naiwa.GAS.get();}

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel server) || burst) return;
        burst = true;
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 30, 1,.5,1,.05);
        // Enemy 同时覆盖史莱姆、恶魂和幻翼；它们并不都继承 Monster，不能只按类层级筛选。
        for (Mob mob : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(3))) {
            if (mob instanceof Enemy && mob.isAlive() && distanceToSqr(mob) <= 9 && !mob.getType().is(NaiwaGameplay.GAS_IMMUNE)
                    && !(mob instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerUUID()!=null)
                    && (getOwner()==null || !mob.isAlliedTo(getOwner()))
                    && NaiwaGameplay.clearLine(server, position(), mob.getEyePosition())) NaiwaGameplay.laugh(mob, 50);
        }
        discard();
    }
}
