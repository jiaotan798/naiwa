package com.naiwa;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;

/** 原版格挡与耐久行为保持不变；每面盾积累进度，服务端按玩家顺序结算已消耗的释放。 */
public class NaiwaShield extends ShieldItem {
    public static final int PERFORMANCE_TICKS = 40, HIT_TICK = 12;
    private static final Map<UUID, ArrayDeque<Burst>> BURSTS = new HashMap<>();
    // 同一次伤害管线即使重复投递事件也只能计一次；弱引用不保留已经结束的伤害上下文。
    private static final Map<Object, Boolean> COUNTED = new WeakHashMap<>();

    public NaiwaShield(Properties properties) {super(properties);}
    // 统一使用奶蛙盾名称及固定纹章；原盾染色和旗帜组件保留，不拼接不存在的染色翻译键。
    @Override public String getDescriptionId(ItemStack stack) {return getDescriptionId();}
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.naiwa.shield", charge(stack)));
    }

    private static final String CHARGE = "naiwa:shield_charge";
    private static final String LAUGH_START = "naiwa:shield_laugh_start";

    public static int charge(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(CHARGE),0,2);
    }

    static void reset(ItemStack stack) {
        stack.getOrCreateTag().putInt(CHARGE,0);
        stack.removeTagKey(LAUGH_START);
    }

    /** 旧版通过栈标签同步动画，写入存档时剥离演出起点，避免重载后补播。 */
    public static void stripTransientTag(net.minecraft.nbt.CompoundTag saved) {
        var tag = saved.getCompound("tag");
        if (!tag.contains(LAUGH_START)) return;
        var copy = tag.copy();
        copy.remove(LAUGH_START);
        saved.put("tag",copy);
    }
    public static double elapsed(ItemStack stack, double time) {
        var tag = stack.getTag();
        return tag == null || !tag.contains(LAUGH_START) ? -1 : time - tag.getLong(LAUGH_START);
    }

    static void stopped() {
        // 清理时不能边遍历 keySet 边删除。
        for (UUID id : BURSTS.keySet().toArray(UUID[]::new)) clear(id);
        COUNTED.clear();
    }

    /** 只接收实际阻挡结果；同一次伤害重复通知时传同一个结算上下文。 */
    public static void block(ServerPlayer player, Object damageContext) {
        if (!player.isAlive()) return;
        ItemStack stack = player.getUseItem();
        if (!stack.is(Naiwa.SHIELD.get()) || (damageContext != null && COUNTED.put(damageContext, true) != null)) return;
        int progress = charge(stack) + 1;
        stack.getOrCreateTag().putInt(CHARGE,progress % 3);
        if (progress < 3) return;
        // 先消耗三次进度，再排队；演出期间的新格挡属于下一轮，不能被动画占用吞掉。
        Vec3 facing = Vec3.directionFromRotation(0, player.getYHeadRot());
        BURSTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(new Burst(stack, facing));
    }

    /** 每轮只启动一次，在固定时刻命中一次后等待收场；下一轮从实际启动时刻计时，避免延迟后挤在同 tick 结算。 */
    static void tick(MinecraftServer server) {
        for (UUID id : BURSTS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator()) {clear(id); continue;}
            var queue = BURSTS.get(id); Burst burst = queue.peek();
            long now = player.level().getGameTime();
            if (burst.start < 0) {
                burst.start = now;
                if (!burst.stack.isEmpty()) burst.stack.getOrCreateTag().putLong(LAUGH_START,now);
            }
            // 探头 0.3 秒后使用原速 1.7 秒片段，与本轮两秒演出一起结束，不加速完整人声。
            if (!burst.sounded && now-burst.start>=6) {
                burst.sounded=true;
                player.level().playSound(null,player,Naiwa.SHIELD_LAUGH.get(),SoundSource.PLAYERS,.7F,1);
            }
            if (!burst.hit && now - burst.start >= HIT_TICK) {
                burst.hit = true; push(player, burst.facing);
            }
            if (now - burst.start >= PERFORMANCE_TICKS) {
                burst.stack.removeTagKey(LAUGH_START); queue.remove();
                if (queue.isEmpty()) BURSTS.remove(id);
            }
        }
    }

    /** 图腾沿用四周 0.9；盾牌使用正面半角 60°、四格范围和水平 1.8，抬升上限不变。 */
    static void pushAround(ServerPlayer player) {push(player, Vec3.ZERO);}

    private static void push(ServerPlayer player, Vec3 facing) {
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4))) {
            if (target == player || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand
                    || target.isAlliedTo(player) || target instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) continue;
            if (target instanceof ServerPlayer other && !player.canHarmPlayer(other)) continue;
            Vec3 offset = target.position().subtract(player.position());
            Vec3 direction = new Vec3(offset.x, 0, offset.z).normalize();
            if (offset.lengthSqr() > 16 || direction.lengthSqr() < .5 || facing.lengthSqr() > 0 && facing.dot(direction) < .5 || !player.hasLineOfSight(target)) continue;
            // 图腾沿用原力度；只有记录了水平朝向的盾牌释放增强，垂直上限仍由原版控制。
            target.knockback(facing.lengthSqr()>0?1.8:.9, -direction.x, -direction.z);
            target.hurtMarked = true;
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
    }

    static void clear(UUID id) {
        var queue = BURSTS.remove(id);
        if (queue != null) for (Burst burst : queue) burst.stack.removeTagKey(LAUGH_START);
    }

    private static final class Burst {
        final ItemStack stack;
        final Vec3 facing;
        long start = -1;
        boolean hit;
        boolean sounded;
        Burst(ItemStack stack, Vec3 facing) {this.stack = stack; this.facing = facing;}
    }
}
