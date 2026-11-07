package com.naiwa;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 持原版盾时的独立相遇入口；只在已加载、可站立且玩家看得见的位置现身。 */
final class NaiwaEncounters {
    private static final Map<ServerPlayer, Long> NEXT = new WeakHashMap<>();
    static void stopped() {NEXT.clear();}
    /** 等待首次观察持盾后的延迟，失败则延后重试；只有成功现身才进入五分钟间隔。 */
    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long now=player.level().getGameTime();
            if (now%20!=0 || !player.isAlive() || player.isSpectator() || NaiwaPranks.busy(player)) continue;
            boolean holding=false;
            for (InteractionHand hand : InteractionHand.values()) holding |= player.getItemInHand(hand).is(Items.SHIELD);
            if (!holding) continue;
            Long next=NEXT.putIfAbsent(player,now+60); // 首次持盾至少等三秒，再按每秒一次的节奏尝试。
            if (next==null || now<next) continue;
            // 无安全位置时五秒后重试；已有奶蛙交由本体处理转化，不额外生成第二只。
            NEXT.put(player,now+100);
            if (!player.level().getEntitiesOfClass(NaiwaEntity.class,player.getBoundingBox().inflate(8),NaiwaEntity::isAlive).isEmpty()) continue;
            if (appear(player)) NEXT.put(player,now+6000); // 持盾现身成功后独立等待五分钟，不改十种整蛊的共享冷却。
        }
    }

    /** 检查脚底支撑、全身碰撞、液体和视线；不拆方块，也不为演出强制加载远处区块。 */
    private static boolean appear(ServerPlayer player) {
        var level=player.level();
        for (int angle : new int[]{0,20,-20,35,-35}) {
            Vec3 offset=Vec3.directionFromRotation(0,player.getYHeadRot()+angle).scale(3.5);
            BlockPos base=BlockPos.containing(player.position().add(offset));
            for (int dy : new int[]{0,1,-1,-2}) {
                BlockPos feet=base.above(dy);
                if (!level.hasChunkAt(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)) continue;
                var frog=Naiwa.ENTITY.get().create(level,net.minecraft.world.entity.EntitySpawnReason.TRIGGERED); frog.setPos(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
                if (!level.noCollision(frog,frog.getBoundingBox()) || level.containsAnyLiquid(frog.getBoundingBox()) || !player.hasLineOfSight(frog)) continue;
                float yaw=(float)Math.toDegrees(Math.atan2(player.getZ()-frog.getZ(),player.getX()-frog.getX()))-90;
                frog.setYRot(yaw); frog.setYHeadRot(yaw); frog.yBodyRot=yaw;
                if (!level.addFreshEntity(frog)) continue;
                level.sendParticles(ParticleTypes.POOF,frog.getX(),frog.getY()+.7,frog.getZ(),12,.3,.5,.3,.02);
                return true;
            }
        }
        return false;
    }
}
