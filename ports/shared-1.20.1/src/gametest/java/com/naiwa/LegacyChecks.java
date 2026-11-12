package com.naiwa;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** 测试实际使用旧版事件和 Mixin 的伤害、物品和配方入口。 */
final class LegacyChecks {
    /** 覆盖入服整蛊使用的射线构造，实际方块既能阻挡也能在移除后恢复通视。 */
    static void raycastContext(GameTestHelper helper) {
        var observer=helper.spawn(EntityType.COW,1,2,1);
        var from=helper.absoluteVec(new Vec3(1.5,2.5,1.5));
        var to=helper.absoluteVec(new Vec3(3.5,2.5,1.5));
        helper.setBlock(2,2,1,net.minecraft.world.level.block.Blocks.GLASS);
        helper.assertTrue(!NaiwaGameplay.clearLine(observer,from,to),"普通玻璃阻挡整蛊视线");
        helper.setBlock(2,2,1,net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(NaiwaGameplay.clearLine(observer,from,to),"移除遮挡后射线恢复通视");
        helper.succeed();
    }

    static void totemAndRecipe(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(helper);
        try {
            var input = new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu,2,1);
            input.setItem(0,new ItemStack(Naiwa.GAS.get()));
            input.setItem(1,new ItemStack(Items.TOTEM_OF_UNDYING));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
            var totem = recipe.assemble(input,level.registryAccess());
            helper.assertTrue(totem.is(Naiwa.TOTEM.get()) && recipe.getRemainingItems(input).get(0).is(Items.GLASS_BOTTLE),"合成保留空瓶");
            player.setItemInHand(InteractionHand.MAIN_HAND,totem);
            player.hurt(player.damageSources().generic(),100);
            helper.assertTrue(player.isAlive() && totem.isEmpty() && player.hasEffect(Naiwa.ASCENDING),"真实致命伤害消耗图腾并启动飞升");
            helper.assertTrue(player.isNoGravity() && NaiwaGameplay.blocksFall(player),"飞升期间启用免摔");
            helper.succeed();
        } finally {
            NaiwaGameplay.cleanup(player);
            remove(player);
        }
    }

    static void realShieldBlock(GameTestHelper helper) {
        var player = player(helper);
        try {
            var shield = new ItemStack(Naiwa.SHIELD.get());
            player.setItemInHand(InteractionHand.OFF_HAND,shield);
            player.startUsingItem(InteractionHand.OFF_HAND);
            for (int tick=0;tick<6;tick++) player.doTick();
            var arrow = EntityType.ARROW.create(helper.getLevel());
            arrow.setPos(player.position().add(0,0,2));
            player.hurt(player.damageSources().arrow(arrow,null),4);
            helper.assertTrue(NaiwaShield.charge(shield)==1 && shield.getDamageValue()==5 && player.getHealth()==20,"正面真实格挡只计一次，并扣盾牌耐久");
            shield.getOrCreateTag().putLong("naiwa:shield_laugh_start",helper.getLevel().getGameTime());
            var restored = ItemStack.of(shield.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(NaiwaShield.charge(restored)==1 && NaiwaShield.elapsed(restored,0)==-1
                    && NaiwaShield.elapsed(shield,helper.getLevel().getGameTime())==0,"格挡进度保存，瞬时演出仅同步");
            player.disableShield(true);
            helper.assertTrue(player.getCooldowns().isOnCooldown(Naiwa.SHIELD.get()) && !player.isUsingItem(),"破盾冷却应用于举起的奶蛙盾");
            helper.succeed();
        } finally {
            remove(player);
        }
    }

    static void gasDamageHooks(GameTestHelper helper) {
        var mob = helper.spawn(EntityType.HUSK,2,3,2);
        NaiwaGameplay.laugh(mob,50);
        float health = mob.getHealth();
        var target = helper.spawn(EntityType.COW,3,3,2);
        target.hurt(target.damageSources().mobAttack(mob),4);
        helper.assertTrue(target.getHealth()==target.getMaxHealth(),"大笑生物的直接近战被阻止");
        mob.hurt(mob.damageSources().generic(),4);
        helper.assertTrue(mob.getHealth()<health && !mob.hasEffect(Naiwa.LAUGHING),"实际扣血后解除怪物的大笑");
        helper.succeed();
    }

    /** 原版假玩家会固定报告创造模式；真实生存伤害使用嵌入连接。 */
    private static ServerPlayer player(GameTestHelper helper) {
        var id = UUID.randomUUID();
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(),level,new GameProfile(id,"naiwa-"+id.toString().substring(0,8)));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,player);
        player.setGameMode(GameType.SURVIVAL);
        var position = helper.absoluteVec(new Vec3(3.5,3,3.5));
        player.moveTo(position.x,position.y,position.z,0,0);
        player.setYHeadRot(0);
        player.setNoGravity(true);
        for (int tick=0;tick<70;tick++) player.tick();
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.getServer().getPlayerList().remove(player);
    }
}
