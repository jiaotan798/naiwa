package com.naiwa;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** 会跑的钻石暂存本次真实掉落；不继承 ItemEntity，演出中玩家、漏斗和物品合并都无法提前取走。 */
public final class NaiwaDecoy extends Entity {
    private boolean exposed;
    private boolean transferred;
    // 不落盘、不代表场景仍有效；旧实例及重载副本必须由当前场景实例核对后才能被认作活动诱饵。
    java.util.UUID prankOwner;
    private final List<ItemStack> diamonds=new ArrayList<>();
    public NaiwaDecoy(EntityType<? extends NaiwaDecoy> type,Level level) {super(type,level);setNoGravity(true);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(ValueInput tag) {
        diamonds.clear();
        for (var stack : tag.listOrEmpty("HeldDiamonds",ItemStack.CODEC)) if (!stack.isEmpty()) diamonds.add(stack);
        // 不恢复无法同步重建的追逐场景；有真实物品则首 tick 原地返还，旧版空诱饵直接清理。
        if(diamonds.isEmpty())discard();else setInvisible(true);
    }
    @Override protected void addAdditionalSaveData(ValueOutput tag) {
        var saved = tag.list("HeldDiamonds",ItemStack.CODEC);
        for (var stack : diamonds) saved.add(stack);
    }
    /** 接收调用者已校验的本次真实掉落；独立复制，避免原掉落列表后续修改影响返还数量及组件。 */
    public void holdDiamonds(List<ItemStack> stacks) {for(var stack:stacks)diamonds.add(stack.copy());}
    /** 已入网才扣除暂存栈；加入世界失败时保留原栈，交由后续 tick 重试。 */
    private void pay() {
        if(level().isClientSide()||transferred)return;
        for(var iterator=diamonds.iterator();iterator.hasNext();) {
            var drop=new ItemEntity(level(),getX(),getY()+.1,getZ(),iterator.next().copy());drop.setDefaultPickUpDelay();
            // 按需求在消失点原地落下，不施加原版新物品实体默认的随机水平抛散。
            drop.setDeltaMovement(Vec3.ZERO);
            if(level().addFreshEntity(drop))iterator.remove();
        }
    }
    /** 可重复调用的最终结算；区块卸载后旧实例不再有权结算，保存副本在加载时接管。 */
    public void finishAt(Vec3 position) {
        if(transferred)return;setPos(position);setInvisible(true);pay();if(diamonds.isEmpty())discard();
    }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide()&&!diamonds.isEmpty()&&!NaiwaPranks.isDecoyActive(this)&&tickCount%20==1)finishAt(position());
    }
    @Override public void remove(RemovalReason reason) {
        // 卸载和跨维度复制的保存副本仍持有物品，旧对象退场时不能再补发一份。
        if(!reason.shouldDestroy())transferred=true;
        else if(!level().isClientSide()&&!transferred&&!diamonds.isEmpty()) {
            pay();if(!diamonds.isEmpty()){setInvisible(true);return;}
        }
        super.remove(reason);
    }
    @Override public boolean isPickable() {return !isInvisible();}
    @Override public boolean hurtServer(ServerLevel level,DamageSource source,float amount) {if(!level().isClientSide()&&NaiwaPranks.canExposeDecoy(this))exposed=true;return false;}
    @Override public InteractionResult interact(Player player,InteractionHand hand,Vec3 hit) {
        if(!level().isClientSide()&&NaiwaPranks.canExposeDecoy(this))exposed=true;
        return level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
    public boolean exposed() {return exposed;}
}
