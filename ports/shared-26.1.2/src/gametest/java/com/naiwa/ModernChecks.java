package com.naiwa;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** 测试实际使用 26.1.2 格挡组件和服务端结算 的伤害、物品和配方入口。 */
final class ModernChecks {
    static void totemAndRecipe(GameTestHelper helper) {
        var input = CraftingInput.of(2,1,List.of(new ItemStack(Naiwa.GAS.get()),new ItemStack(Items.TOTEM_OF_UNDYING)));
        var level = helper.getLevel();
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow().value();
        var totem = recipe.assemble(input);
        helper.assertTrue(totem.is(Naiwa.TOTEM.get()) && recipe.getRemainingItems(input).getFirst().is(Items.GLASS_BOTTLE),"合成保留空瓶");
        var player = player(helper);
        try {
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
            var arrow = EntityType.ARROW.create(helper.getLevel(),net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            arrow.setPos(player.position().add(0,0,2));
            player.hurt(player.damageSources().arrow(arrow,null),4);
            helper.assertTrue(NaiwaShield.charge(shield)==1 && shield.getDamageValue()==5 && player.getHealth()==20,"正面真实格挡只计一次，并扣盾牌耐久");
            shield.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS).disable(player.level(),player,5,shield);
            helper.assertTrue(player.getCooldowns().isOnCooldown(shield) && !player.isUsingItem(),"破盾冷却应用于举起的奶蛙盾");
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
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id,"naiwa-"+id.toString().substring(0,8)),false);
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(),level,cookie.gameProfile(),cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5,3,3.5)),0,0);
        player.setYHeadRot(0);
        player.setNoGravity(true);
        for (int tick=0;tick<70;tick++) player.tick();
        return player;
    }

    private static void remove(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
