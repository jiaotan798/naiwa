package com.naiwa;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** 使用真实伤害管线、物品组件和世界碰撞验证盾牌；测试类不进入玩家安装包。 */
@GameTestHolder("naiwa")
@PrefixGameTestTemplate(false)
public final class NaiwaShieldTests {
    static ServerPlayer player(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, "shield-"+id.toString().substring(0,8)), false);
        var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        NetworkRegistry.configureMockConnection(connection);
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        player.setPos(h.absoluteVec(new Vec3(3.5, 3, 3.5)));
        player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
        // 消除正常登录保护；不覆盖受伤或格挡判定，后续攻击仍走原版流程。
        for (int i=0;i<70;i++) player.tick();
        return player;
    }
    static void remove(ServerPlayer player) {player.getServer().getPlayerList().remove(player);}
    private static void finish(GameTestHelper h, ServerPlayer p, Runnable checks) {
        try {checks.run(); h.succeed();} finally {remove(p);}
    }
    private static NaiwaEntity frog(GameTestHelper h) {
        var frog = h.spawn(Naiwa.ENTITY.get(), 2, 3, 2); frog.setNoAi(true); frog.setNoGravity(true); return frog;
    }
    private static void raise(ServerPlayer p, ItemStack shield) {
        p.setItemInHand(InteractionHand.OFF_HAND, shield); p.startUsingItem(InteractionHand.OFF_HAND);
        for (int i=0;i<6;i++) p.doTick();
    }
    private static void attack(ServerPlayer p, Vec3 source) {
        var arrow = EntityType.ARROW.create(p.level()); arrow.setPos(source);
        p.invulnerableTime = 0; p.hurt(p.damageSources().arrow(arrow, null), 4);
    }
    private static Mob target(GameTestHelper h, Vec3 position) {
        var mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(position);
        h.getLevel().addFreshEntity(mob); return mob;
    }

    @GameTest(template="empty", timeoutTicks=235)
    public static void conversionPreservesOriginalComponents(GameTestHelper h) {
        var p = player(h); var frog = frog(h);
        var shield = new ItemStack(Items.SHIELD); shield.setDamageValue(123);
        shield.set(DataComponents.CUSTOM_NAME, Component.literal("我的旧盾")); shield.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
        shield.set(DataComponents.BANNER_PATTERNS,new BannerPatternLayers.Builder().add(
                p.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.CROSS),DyeColor.YELLOW).build());
        shield.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 2);
        p.setItemInHand(InteractionHand.OFF_HAND, shield);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.runAfterDelay(25, () -> {
            h.assertTrue(frog.animation().equals("laugh_sequence"), "接近持盾玩家后先表演");
            h.assertTrue(p.getOffhandItem() == shield, "大笑尚未结束不能提前替换原盾");
        });
        h.runAfterDelay(185, () -> h.assertTrue(p.getOffhandItem()==shield, "新模型完整恢复动作结束前不提前转盾"));
        h.runAfterDelay(210, () -> finish(h,p, () -> {
            var converted = p.getOffhandItem();
            h.assertTrue(converted.is(Naiwa.SHIELD.get()) && converted.getCount()==1, "只原位转化一面副手盾");
            var expected=shield.transmuteCopy(Naiwa.SHIELD.get()); expected.set(Naiwa.CHARGE.get(),0);
            h.assertTrue(converted.getDamageValue()==123 && converted.getComponents().equals(expected.getComponents()),
                    "只改变物品种类并初始化零进度，保留全部适用组件");
            h.assertTrue(converted.getHoverName().getString().equals("我的旧盾") && converted.get(DataComponents.BASE_COLOR)==DyeColor.GREEN
                    && converted.get(DataComponents.ENCHANTMENTS).equals(shield.get(DataComponents.ENCHANTMENTS)), "名称、染色和附魔不丢失");
            h.assertTrue(NaiwaShield.charge(converted)==0 && p.getMainHandItem().is(Items.STICK), "不改动另一只手或凭空补发");
        }));
    }

    @GameTest(template="empty", timeoutTicks=140)
    public static void heldShieldFindsOneSafeVisibleEncounter(GameTestHelper h) {
        var p=player(h); p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));
        BlockPos floor=BlockPos.containing(p.position()).below();
        for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) h.getLevel().setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        h.runAfterDelay(110, () -> finish(h,p, () -> {
            var frogs=h.getLevel().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(8),NaiwaEntity::isAlive);
            h.assertTrue(frogs.size()==1, "持盾相遇只生成一只奶蛙");
            var frog=frogs.getFirst();
            h.assertTrue(p.hasLineOfSight(frog) && !frog.getBoundingBox().intersects(p.getBoundingBox())
                    && h.getLevel().noCollision(frog,frog.getBoundingBox()), "现身位置可见、有支撑且不挤进玩家或墙里");
            h.assertTrue(frog.animation().equals("laugh_sequence"), "自动现身后接上转化演出");
        }));
    }

    @GameTest(template="empty", timeoutTicks=210)
    public static void conversionCancelsWhenShieldLeavesSelectedHand(GameTestHelper h) {
        var p=player(h); frog(h); var shield=new ItemStack(Items.SHIELD);
        p.setItemInHand(InteractionHand.MAIN_HAND, shield);
        h.runAfterDelay(40, () -> {p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); p.getInventory().setItem(4, shield);});
        h.runAfterDelay(70, () -> {p.getInventory().setItem(4, ItemStack.EMPTY); p.setItemInHand(InteractionHand.OFF_HAND, shield);});
        h.runAfterDelay(185, () -> finish(h,p, () -> h.assertTrue(p.getOffhandItem()==shield && shield.is(Items.SHIELD),
                "中途收起再换到副手，原演出不能在结束时补发新盾")));
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void countsOnlyRealBlocksAndKeepsVanillaDurability(GameTestHelper h) {
        var p=player(h); var shield=new ItemStack(Naiwa.SHIELD.get());
        try {
            raise(p,shield); h.assertTrue(p.isBlocking(), "使用真实五 tick 举盾延迟");
            h.assertTrue(NaiwaShield.charge(shield)==0, "空举盾没有进度");
            attack(p,p.position().add(0,0,-2));
            h.assertTrue(NaiwaShield.charge(shield)==0 && p.getHealth()<20, "背后攻击绕盾，不计进度");
            p.setHealth(20);
            attack(p,p.position().add(0,0,2));
            h.assertTrue(NaiwaShield.charge(shield)==1 && shield.getDamageValue()==5 && p.getHealth()==20, "前方攻击实际格挡一次并扣原版耐久");
            attack(p,p.position().add(0,0,2)); attack(p,p.position().add(0,0,2));
            h.assertTrue(NaiwaShield.charge(shield)==0 && shield.getDamageValue()==15, "第三次立即消耗进度，保持原版耐久损耗");
            p.disableShield();
            h.assertTrue(!p.isUsingItem() && p.getCooldowns().isOnCooldown(Naiwa.SHIELD.get()), "原版破盾冷却应用于奶蛙盾本身");
            h.succeed();
        } finally {remove(p);}
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void duplicateDamageContextCountsOnceAndChargeSurvivesSave(GameTestHelper h) {
        var p=player(h); var shield=new ItemStack(Naiwa.SHIELD.get());
        try {
            raise(p,shield);
            var damage=new DamageContainer(p.damageSources().generic(),4);
            var canceled=new LivingShieldBlockEvent(p,new DamageContainer(p.damageSources().generic(),4),true); canceled.setCanceled(true);
            NaiwaNeoForgeGameplay.shieldBlock(canceled);
            NaiwaNeoForgeGameplay.shieldBlock(new LivingShieldBlockEvent(p,new DamageContainer(p.damageSources().generic(),4),false));
            h.assertTrue(NaiwaShield.charge(shield)==0, "取消或未实际阻挡的事件不计数");
            NaiwaNeoForgeGameplay.shieldBlock(new LivingShieldBlockEvent(p,damage,true));
            NaiwaNeoForgeGameplay.shieldBlock(new LivingShieldBlockEvent(p,damage,true));
            h.assertTrue(NaiwaShield.charge(shield)==1, "同一次伤害上下文重复回调只能累计一次");
            shield.set(Naiwa.LAUGH_START.get(), h.getLevel().getGameTime());
            var saved=ItemStack.parseOptional(h.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag)shield.save(h.getLevel().registryAccess()));
            h.assertTrue(saved.is(Naiwa.SHIELD.get()) && NaiwaShield.charge(saved)==1 && !saved.has(Naiwa.LAUGH_START.get()),
                    "进度随物品存档，临时动画不进入磁盘");
            h.succeed();
        } finally {remove(p);}
    }

    /** 共用击退入口必须区分图腾与盾牌，不能将盾牌强化误带到图腾。 */
    @GameTest(template="empty",timeoutTicks=20)
    public static void totemPushKeepsOriginalHorizontalAndVerticalImpulse(GameTestHelper h) {
        var p=player(h);var mob=target(h,p.position().add(0,0,2));
        try {
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            mob.setOnGround(true);mob.setDeltaMovement(Vec3.ZERO);NaiwaShield.pushAround(p);
            h.assertTrue(Math.abs(mob.getDeltaMovement().z-.9)<.0001&&Math.abs(mob.getDeltaMovement().y-.4)<.0001,"图腾仍为水平0.9、地面抬升上限0.4");
            h.assertTrue(mob.getHealth()==20,"无额外伤害");h.succeed();
        } finally {remove(p);}
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void queuedBurstsKeepCapturedFacingAndNeverDealDamage(GameTestHelper h) {
        var p=player(h); var shield=new ItemStack(Naiwa.SHIELD.get()); raise(p,shield);
        var front=target(h,p.position().add(0,0,2)); var back=target(h,p.position().add(0,0,-2));
        h.assertTrue(p.hasLineOfSight(front), "测试夹具前方目标可见");
        for (int i=0;i<6;i++) attack(p,p.position().add(0,0,2));
        p.setYRot(180); p.setYHeadRot(180);
        h.runAfterDelay(20, () -> {
            // NoAI 夹具不会自行 travel；验证真实 knockback 产生的速度，避免把静止夹具当成击退失败。
            h.assertTrue(front.getDeltaMovement().z>1.3 && back.getDeltaMovement().horizontalDistanceSqr()<.001,
                    "转头不会把已排队的击退改到身后：front="+front.getDeltaMovement()+", back="+back.getDeltaMovement());
            h.assertTrue(front.getHealth()==20 && back.getHealth()==20 && p.getHealth()==20, "击退不额外造成伤害");
            front.setPos(p.position().add(0,0,2)); front.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(63, () -> {
            h.assertTrue(front.getDeltaMovement().z>.1, "六次格挡排队两次释放，动画期间不吞掉下一轮");
            front.setPos(p.position().add(0,0,2)); front.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(88, () -> finish(h,p, () -> h.assertTrue(front.getDeltaMovement().horizontalDistanceSqr()<.001 && !shield.has(Naiwa.LAUGH_START.get()),
                "每轮只击退一次，演出结束清理临时状态")));
    }

    @GameTest(template="empty", timeoutTicks=45)
    public static void burstRechecksWallsAndDistanceAtHitTime(GameTestHelper h) {
        var p=player(h); var shield=new ItemStack(Naiwa.SHIELD.get()); raise(p,shield);
        var behindWall=target(h,p.position().add(0,0,3)); var leaving=target(h,p.position().add(1,0,2));
        for (int i=0;i<3;i++) attack(p,p.position().add(0,0,2));
        h.runAfterDelay(4, () -> {
            leaving.setPos(p.position().add(0,0,6));
            for (int y=0;y<3;y++) for (int x=-1;x<=1;x++)
                h.getLevel().setBlock(BlockPos.containing(p.position()).offset(x,y,1),Blocks.STONE.defaultBlockState(),3);
        });
        h.runAfterDelay(22, () -> finish(h,p, () -> h.assertTrue(behindWall.getDeltaMovement().horizontalDistanceSqr()<.001
                && leaving.getDeltaMovement().horizontalDistanceSqr()<.001, "结算时新增墙体与已离开范围的目标均不受击退")));
    }

    @GameTest(template="empty", timeoutTicks=95)
    public static void burstProtectsOwnedPetsAndClearsPendingReleaseOnLogout(GameTestHelper h) {
        var p=player(h); var shield=new ItemStack(Naiwa.SHIELD.get()); raise(p,shield);
        var front=target(h,p.position().add(0,0,2));
        var wolf=EntityType.WOLF.create(h.getLevel()); wolf.setNoAi(true); wolf.setNoGravity(true); wolf.tame(p);
        wolf.setPos(p.position().add(1.5,0,2)); h.getLevel().addFreshEntity(wolf);
        var horse=EntityType.HORSE.create(h.getLevel()); horse.setNoAi(true); horse.setNoGravity(true); horse.setOwnerUUID(p.getUUID());
        horse.setPos(p.position().add(-1.5,0,2)); h.getLevel().addFreshEntity(horse);
        for(int i=0;i<6;i++) attack(p,p.position().add(0,0,2));
        h.runAfterDelay(25, () -> {
            h.assertTrue(front.getDeltaMovement().z>.1 && wolf.getDeltaMovement().horizontalDistanceSqr()<.001
                    && horse.getDeltaMovement().horizontalDistanceSqr()<.001, "正常目标被击退，自家狼和坐骑均排除");
            remove(p); front.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(75, () -> {
            h.assertTrue(front.getDeltaMovement().horizontalDistanceSqr()<.001 && !shield.has(Naiwa.LAUGH_START.get()),
                    "离线后清理未结算演出，不能继续产生第二次击退");
            h.succeed();
        });
    }
}
