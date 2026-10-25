package com.naiwa.client;

import com.mojang.logging.LogUtils;
import com.naiwa.Naiwa;
import com.naiwa.NaiwaEntity;
import com.naiwa.NaiwaGameplay;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** 使用专用验收存档检查资源、实际采集/投掷、捧腹双视角及图腾背面变身；飞升第一人称隐藏另由 AscensionHands 检查，成品排除此类。 */
final class FullGameplayProbe {
    private static final String[] ACTIONS={"mirror","door_giggle","tree","tree_exposed","mine","mine_hide","fish","fish_land","boat"};
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
        server.execute(()->action.accept(server.getPlayerList().getPlayer(id)));
    }
    private static NaiwaEntity frog(net.minecraft.server.level.ServerPlayer p) {
        return p.level().getEntitiesOfClass(NaiwaEntity.class,p.getBoundingBox().inflate(12),NaiwaEntity::isAlive).getFirst();
    }
    private static void screenshot(String name) {
        var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->LogUtils.getLogger().info("NAIWA_FULL_ACCEPTANCE {}",m.getString()));
    }
    static void tick(int time) {
        var mc=Minecraft.getInstance();
        if(time==5) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.keyUse.setDown(false);mc.options.hideGui=false;
            server(p->{
                p.stopUsingItem();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);
                p.connection.teleport(.5,201,3,180,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GLASS_BOTTLE));
                var frog=frog(p);frog.setPos(.5,201,.5);frog.setAct("");frog.play("laugh_sequence");
            });
        }
        if(time==30) {
            NaiwaEntity frog=null;
            for(var entity:mc.level.entitiesForRendering()) if(entity instanceof NaiwaEntity found) {frog=found;break;}
            if(frog==null) throw new IllegalStateException("采集验收缺少奶蛙");
            mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);
        }
        if(time==45) {
            if(!mc.player.getMainHandItem().is(Naiwa.GAS.get())) throw new IllegalStateException("实际采集没有收到笑气瓶");
            screenshot("25-gas-collected");
        }
        if(time==50) server(p->{
            var mob=EntityType.HUSK.create(p.level());mob.setPos(.5,201,0);mob.setNoAi(true);p.serverLevel().addFreshEntity(mob);
            // 对着实体投掷，碰撞管线仍由投掷物完成，不能在探针里直接施加控场。
            p.connection.teleport(.5,201,3,180,15);
        });
        if(time==60) mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        if(time==78) {
            if(!mc.player.getMainHandItem().isEmpty()) throw new IllegalStateException("真实投掷未消耗笑气瓶");
            screenshot("26-gas-thrown");
        }
        if(time==90) server(p->{NaiwaGameplay.laugh(p,90);});
        if(time==110) screenshot("27-player-laugh-first");
        if(time==120) {
            server(p->p.connection.teleport(.5,201,7.5,0,0));
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if(time==145) screenshot("28-player-laugh-third");
        for(int i=0;i<ACTIONS.length;i++) {
            final String action=ACTIONS[i];
            if(time==200+i*24) {mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->{
                var actor=frog(p);actor.play("idle");actor.setAct(action);actor.setYRot(0);actor.yBodyRot=0;
                actor.setItemInHand(InteractionHand.MAIN_HAND,action.startsWith("mine")?new ItemStack(Items.IRON_PICKAXE):ItemStack.EMPTY);
                p.connection.teleport(.5,201,4.5,180,5);
            });}
            if(time==215+i*24) screenshot("29-prank-"+action);
        }
        if(time==445) server(p->{var actor=frog(p);actor.setAct("");actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);actor.play("auto");});
        if(time==470) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(p->{
                p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Naiwa.TOTEM.get()));p.setHealth(20);p.invulnerableTime=0;
                p.hurt(p.damageSources().generic(),100);
                if(!p.isAlive()||!p.getOffhandItem().isEmpty()||!p.hasEffect(Naiwa.ASCENDING)) throw new IllegalStateException("GPU 验收真实图腾管线失败");
            });
        }
        if(time==495) screenshot("30-totem-rise-back");
        if(time==510) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if(time==530) screenshot("31-totem-stays-back");
        if(time==555) {
            if(!mc.player.hasEffect(Naiwa.ASCENDING)) throw new IllegalStateException("客户端没有同步飞升状态");
            if(mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)throw new IllegalStateException("飞升期间未保持角色背面第三人称");
            screenshot("32-totem-hover-third");
        }
        if(time==660) screenshot("33-totem-descend-back");
        if(time==720) {
            if(mc.player.hasEffect(Naiwa.ASCENDING)) throw new IllegalStateException("飞升结束客户端残留变身");
            if(mc.options.getCameraType()!=CameraType.FIRST_PERSON)throw new IllegalStateException("飞升结束未恢复原第一人称");
            screenshot("34-totem-restored");
        }
        if(time==740) {
            LogUtils.getLogger().info("NAIWA_FULL_ACCEPTANCE 完成：采集、投掷、九种动作姿势、玩家捧腹及图腾自动背面视角与恢复");mc.stop();
        }
    }
}
