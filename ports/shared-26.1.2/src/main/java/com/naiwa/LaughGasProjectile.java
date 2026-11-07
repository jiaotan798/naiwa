package com.naiwa;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** 范围效果只在服务端首次碰撞结算，不透墙、不控制玩家或免疫名单中的生物。 */
public final class LaughGasProjectile extends ThrowableItemProjectile {
    private boolean burst;
    public LaughGasProjectile(EntityType<? extends LaughGasProjectile> type, Level level) {super(type, level);}
    public LaughGasProjectile(EntityType<? extends LaughGasProjectile> type, LivingEntity owner, Level level, net.minecraft.world.item.ItemStack stack) {super(type, owner, level, stack);}
    @Override protected Item getDefaultItem() {return Naiwa.GAS.get();}

    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel server) || burst) return;
        burst = true;
        // 首次权威碰撞只发两组粒子；随机方向由客户端粒子提供器生成，数量和五秒寿命与网易参考一致。
        server.sendParticles(Naiwa.GAS_CLOUD.get(),getX(),getY()+.16,getZ(),36,0,0,0,0);
        server.sendParticles(Naiwa.GAS_FACE.get(),getX(),getY()+.12,getZ(),4,0,0,0,0);
        server.playSound(null,getX(),getY(),getZ(),net.minecraft.sounds.SoundEvents.GLASS_BREAK,net.minecraft.sounds.SoundSource.NEUTRAL,.65F,1.15F);
        // Enemy 同时覆盖史莱姆、恶魂和幻翼；它们并不都继承 Monster，不能只按类层级筛选。
        for (Mob mob : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(3))) {
            if (mob instanceof Enemy && mob.isAlive() && distanceToSqr(mob) <= 9 && !mob.is(NaiwaGameplay.GAS_IMMUNE)
                    && !(mob instanceof net.minecraft.world.entity.OwnableEntity pet && (pet.getOwnerReference() == null ? null : pet.getOwnerReference().getUUID())!=null)
                    && (getOwner()==null || !mob.isAlliedTo(getOwner()))
                    && NaiwaGameplay.clearLine(server, position(), mob.getEyePosition())) NaiwaGameplay.laugh(mob, 50);
        }
        discard();
    }
}
