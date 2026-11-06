package com.naiwa;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 原版格挡与耐久行为保持不变；每面盾积累进度，服务端按玩家顺序结算已消耗的释放。 */
public final class NaiwaShield extends ShieldItem {
    public static final int PERFORMANCE_TICKS = 40, HIT_TICK = 12;
    private static final Map<UUID, ArrayDeque<Burst>> BURSTS = new HashMap<>();
    // 同一次伤害管线即使重复投递事件也只能计一次；弱引用不保留已经结束的伤害上下文。
    private static final Map<DamageContainer, Boolean> COUNTED = new WeakHashMap<>();

    public NaiwaShield(Properties properties) {super(properties);}
    // 统一使用奶蛙盾名称及固定纹章；原盾染色和旗帜组件保留，不拼接不存在的染色翻译键。
    @Override public String getDescriptionId(ItemStack stack) {return getDescriptionId();}
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.naiwa.shield", charge(stack)));
    }

    public static int charge(ItemStack stack) {return Mth.clamp(stack.getOrDefault(Naiwa.CHARGE.get(), 0), 0, 2);}
    public static double elapsed(ItemStack stack, double time) {
        Long start = stack.get(Naiwa.LAUGH_START.get());
        return start == null ? -1 : time - start;
    }

    static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NaiwaShield::block);
        NeoForge.EVENT_BUS.addListener(NaiwaShield::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> clear(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> clear(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {if (e.getEntity() instanceof ServerPlayer p) clear(p.getUUID());});
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            // 清理时不能边遍历 keySet 边删除。
            for (UUID id : BURSTS.keySet().toArray(UUID[]::new)) clear(id);
            COUNTED.clear();
        });
    }

    /** 只读取框架实际阻挡结果；举盾、绕盾、零伤害和已取消的攻击均不产生进度。 */
    static void block(LivingShieldBlockEvent event) {
        if (event.isCanceled() || !event.getBlocked() || event.getBlockedDamage() <= 0
                || !(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        ItemStack stack = player.getUseItem();
        if (!stack.is(Naiwa.SHIELD.get()) || COUNTED.put(event.getDamageContainer(), true) != null) return;
        int progress = charge(stack) + 1;
        stack.set(Naiwa.CHARGE.get(), progress % 3);
        if (progress < 3) return;
        // 先消耗三次进度，再排队；演出期间的新格挡属于下一轮，不能被动画占用吞掉。
        Vec3 facing = Vec3.directionFromRotation(0, player.getYHeadRot());
        BURSTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(new Burst(stack, facing));
    }

    /** 每轮只启动一次，在固定时刻命中一次后等待收场；下一轮从实际启动时刻计时，避免延迟后挤在同 tick 结算。 */
    private static void tick(ServerTickEvent.Post event) {
        for (UUID id : BURSTS.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator()) {clear(id); continue;}
            var queue = BURSTS.get(id); Burst burst = queue.peek();
            long now = player.level().getGameTime();
            if (burst.start < 0) {
                burst.start = now;
                if (!burst.stack.isEmpty()) burst.stack.set(Naiwa.LAUGH_START.get(), now);
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
                burst.stack.remove(Naiwa.LAUGH_START.get()); queue.remove();
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

    private static void clear(UUID id) {
        var queue = BURSTS.remove(id);
        if (queue != null) for (Burst burst : queue) burst.stack.remove(Naiwa.LAUGH_START.get());
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
