package com.naiwa;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** 可存档的奶蛙本体；演出状态只由服务端启动，同步原片段名称与世界起始时刻。 */
public final class NaiwaEntity extends PathfinderMob {
    // 只登记已入场的探头演员 ID；弱世界键且值不引用实体/世界，避免退出后静态表保留整个世界。
    // 两端共用生命周期规则，箱盖仅在客户端读取；集成服与客户端线程可能并发，整次读写共用此表的锁。
    private static final java.util.Map<Level, java.util.Set<Integer>> CHEST_PEEKERS = new java.util.WeakHashMap<>();
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<String> ACT = SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> BOAT_STEERING = SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCOPE_VIEWER = SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.INT);
    private long airborneStart = -1;
    private boolean laughSoundPlayed;
    private boolean miningRotation;
    private Player convertingPlayer;
    private InteractionHand convertingHand;
    private ItemStack convertingShield;
    private boolean collected;
    private long nextGas;
    // 仅供原版堆叠空间比较；不交给 inventory.add，失败重试不再逐 tick 创建待交付物品栈。
    private ItemStack gasSpaceProbe;
    // 整蛊实例不保存到存档；重载时删除临时演员，避免永久占座或留下无主道具。
    private boolean temporary;
    // 仅作服务端场景查找提示，不写入存档；是否仍为演员必须再核对当前场景中的实体实例。
    java.util.UUID prankOwner;

    public NaiwaEntity(EntityType<? extends NaiwaEntity> type, Level level) {super(type, level);}

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .2);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ANIMATION, "auto"); builder.define(START, 0L); builder.define(ACT, ""); builder.define(BOAT_STEERING, 0);
        builder.define(SCOPE_VIEWER,-1);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** 重复交互不会重置正在大笑的时间轴；命令播放则允许明确替换或停止。 */
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.GLASS_BOTTLE)) {
            if (level().isClientSide) return InteractionResult.SUCCESS;
            return collect(player,hand) ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide && animation().equals("auto") && !temporary && !NaiwaPranks.isActor(this)) play("laugh_sequence");
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    public String animation() {return entityData.get(ANIMATION);}
    public String act() {return entityData.get(ACT);}
    public int boatSteering() {return entityData.get(BOAT_STEERING);}
    public int scopeViewer() {return entityData.get(SCOPE_VIEWER);}
    /** 仅同步本轮镜内图片的观看者，-1 撤图；临时演员重载即清理，不保存旧玩家实体 ID。 */
    void setScopeViewer(int viewer) {entityData.set(SCOPE_VIEWER,viewer);}
    /** 0 为不接管，1 为前进，2/3 为前进并间歇左/右转，4 为大笑时只保留回头视角；不写入存档。 */
    public void setBoatSteering(int command) {
        if(command<0||command>4)throw new IllegalArgumentException("未知划船指令："+command);
        if(!level().isClientSide)entityData.set(BOAT_STEERING,command);
    }
    /** 箱内探头不显示腿和躯干，碰撞/眼高也缩到箱盖上方，避免两格高的隐形身体挡住玩家。 */
    @Override protected net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return act().equals("chest_peek")?net.minecraft.world.entity.EntityDimensions.scalable(.65F,.7F).withEyeHeight(.3F):super.getDefaultDimensions(pose);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if(ACT.equals(key)) {refreshDimensions();updateChestPeeker(NaiwaPlatform.isInLevel(this));}
    }
    /** 动作与入场先后顺序不固定，两处都更新集合；离开世界时立即清除旧 ID。 */
    public void updateChestPeeker(boolean added) {
        synchronized(CHEST_PEEKERS) {
            if(added && act().equals("chest_peek")) {
                CHEST_PEEKERS.computeIfAbsent(level(),key->new java.util.HashSet<>()).add(getId());
            } else {
                var actors=CHEST_PEEKERS.get(level());
                if(actors!=null) {actors.remove(getId());if(actors.isEmpty())CHEST_PEEKERS.remove(level());}
            }
        }
    }
    /** 仅作无演员时的快速拒绝；有演员时仍按原空间、存活及位置条件实时检查，不缓存箱盖结果。 */
    public static boolean hasChestPeekers(Level level) {return chestPeekerCount(level)>0;}
    /** 只遍历已登记的探头 ID，再读取实时位置和包围盒；其他箱子不再扫描普通实体或创建结果列表。 */
    public static boolean isChestPeeking(Level level,net.minecraft.core.BlockPos pos) {
        synchronized(CHEST_PEEKERS) {
            var actors=CHEST_PEEKERS.get(level);if(actors==null)return false;
            for(int id:actors) {
                var entity=level.getEntity(id);
                if(entity instanceof NaiwaEntity frog&&frog.isAlive()&&frog.act().equals("chest_peek")&&frog.blockPosition().equals(pos)
                        &&frog.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos).inflate(.5,1,.5)))return true;
            }
            return false;
        }
    }
    static int chestPeekerCount(Level level) {
        synchronized(CHEST_PEEKERS) {
            var actors=CHEST_PEEKERS.get(level);return actors==null?0:actors.size();
        }
    }
    public void setAct(String act) {if (!level().isClientSide) entityData.set(ACT,act);}
    /** 世界时间按 20 tick/秒换算，渲染插值不单独累加客户端时钟，避免围观者播放速度不同。 */
    public double animationSeconds(float partialTick) {return (level().getGameTime() - entityData.get(START) + partialTick) / 20.0;}

    /** 本入口拒绝客户端发起；不持久化临时演出，区块重载后回到正常活动。 */
    public void play(String name) {
        if (level().isClientSide) return;
        if (!name.equals("auto") && !name.equals("laugh_sequence") && !NaiwaAnimations.NAMES.contains(name))
            throw new IllegalArgumentException("未知奶蛙动画：" + name);
        convertingPlayer = null; convertingShield = null;
        laughSoundPlayed = false;
        collected = false;
        entityData.set(START, level().getGameTime()); entityData.set(ANIMATION, name);
        if (!name.equals("auto")) getNavigation().stop();
    }

    /** 转化先检查并结算，再结束演出；临时动作不会阻塞正常生物更新。 */
    @Override public void tick() {
        super.tick();
        // 静止生物的原版身体转向有延迟；矿洞演出应跟随服务器旋转，并延续到本轮大笑结束。
        // 本地标记跨过清空 ACT 的时刻，避免转身与大笑数据同批到达时又回到旧身体朝向；不写入存档。
        if(level().isClientSide) {
            if(act().startsWith("mine"))miningRotation=true;
            else if(!act().isEmpty()||!animation().equals("laugh_sequence"))miningRotation=false;
            if(miningRotation)yBodyRot=getYRot();
        }
        if (onGround() || isInWater()) airborneStart = -1;
        else if (airborneStart < 0) airborneStart = level().getGameTime();
        if (!level().isClientSide) conversion();
        if (!level().isClientSide && !animation().equals("auto")) {
            getNavigation().stop();
            // 新模型身体起拍在 laugh 约 1.44 秒；音轨取原素材 0.6～8.85 秒，保留约 1.44 秒准备静音。
            // 入场结束后启动，完整演出约 2.04 秒音画起拍；单轮只广播一次，不改变原声速。
            boolean full=animation().equals("laugh_sequence");
            if (!laughSoundPlayed && (animation().equals("laugh") || full && animationSeconds(0)>=.6)) {
                laughSoundPlayed=true;
                level().playSound(null,this,Naiwa.LAUGH.get(),net.minecraft.sounds.SoundSource.NEUTRAL,.8F,1);
            }
            double duration = NaiwaAnimations.length(animation());
            if ((animation().equals("laugh_sequence") || !NaiwaAnimations.loops(animation())) && animationSeconds(0) >= duration) play("auto");
        }
        // 场景演员由所属场景结算；常驻本体转盾时优先给持盾者，其余按三格内无遮挡玩家由近到远尝试，成功即停止。
        if(!level().isClientSide&&!temporary&&!NaiwaPranks.isActor(this)&&collectionReady()) {
            if(convertingPlayer instanceof ServerPlayer owner)collectAutomatically(owner);
            else level().getEntitiesOfClass(ServerPlayer.class,getBoundingBox().inflate(3),
                    p->p.isAlive()&&!p.isSpectator()&&distanceToSqr(p)<=9&&p.hasLineOfSight(this)).stream()
                    .sorted(java.util.Comparator.comparingDouble(this::distanceToSqr)).anyMatch(this::collectAutomatically);
        }
    }

    /** 演出期间逐 tick 检查同一持盾实例；换手、丢弃、转交或离开视线立即取消，不能事后补发。 */
    private void conversion() {
        // 当前望远镜使用临时演员；临时或已被场景占用的奶蛙均不能启动盾牌转化，避免覆盖整蛊演出。
        if (temporary || NaiwaPranks.isActor(this)) return;
        if (convertingPlayer != null) {
            if (!isAlive() || !convertingPlayer.isAlive() || convertingPlayer.level() != level()
                    || convertingPlayer.getItemInHand(convertingHand) != convertingShield || !convertingShield.is(Items.SHIELD)
                    || distanceToSqr(convertingPlayer) > 64 || !hasLineOfSight(convertingPlayer)) {
                convertingPlayer = null; convertingShield = null; return;
            }
            if (animationSeconds(0) >= NaiwaAnimations.length("laugh_sequence")) {
                // transmuteCopy 携带原盾的组件补丁，包括耐久、附魔、名称、旗帜及其他适用数据。
                ItemStack converted = convertingShield.transmuteCopy(Naiwa.SHIELD.get());
                converted.set(Naiwa.CHARGE.get(), 0);
                converted.remove(Naiwa.LAUGH_START.get());
                convertingPlayer.setItemInHand(convertingHand, converted);
                convertingPlayer = null; convertingShield = null;
            }
            return;
        }
        if (!isAlive() || !animation().equals("auto") || tickCount % 10 != 0) return;
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(5), p -> p.isAlive() && !p.isSpectator())) {
            // 无普通盾的玩家不参与转化，先跳过以免空做视线和竞争演员查询。
            if (!player.getMainHandItem().is(Items.SHIELD) && !player.getOffhandItem().is(Items.SHIELD)) continue;
            if (!hasLineOfSight(player)) continue;
            // 附近可能有多只常驻本体；同一玩家只允许一只启动转化，避免重叠大笑和竞争同一面盾。
            if (!level().getEntitiesOfClass(NaiwaEntity.class,player.getBoundingBox().inflate(8),
                    other->other!=this && other.isAlive() && other.convertingPlayer==player).isEmpty()) continue;
            for (InteractionHand hand : InteractionHand.values()) if (player.getItemInHand(hand).is(Items.SHIELD)) {
                play("laugh_sequence");
                convertingPlayer = player; convertingHand = hand; convertingShield = player.getItemInHand(hand);
                getLookControl().setLookAt(player, 360, 360);
                return;
            }
        }
    }

    public NaiwaAnimations.ClipTime clip(float partialTick) {
        if (!animation().equals("auto")) return NaiwaAnimations.resolve(animation(), Math.max(0, animationSeconds(partialTick)));
        String name = !onGround() && !isInWater() ? "jump" : getDeltaMovement().horizontalDistanceSqr() > .015 ? "run"
                : getDeltaMovement().horizontalDistanceSqr() > .0001 ? "walk" : "idle";
        // 起跳是单次片段，必须从本次离地计时；世界总时长会直接把它截到末帧。
        double seconds = name.equals("jump") ? (airborneStart < 0 ? 0 : (level().getGameTime() - airborneStart + partialTick) / 20.0)
                : (level().getGameTime() + partialTick) / 20.0;
        return new NaiwaAnimations.ClipTime(name, seconds);
    }

    public void temporary() {temporary=true; setPersistenceRequired(); setNoAi(true);}
    public boolean isTemporary() {return temporary;}

    /** 一轮只交付一瓶；先检查容纳空间，再一次性消耗与交付，多人交互由服务端串行处理。 */
    public boolean collect(Player player, InteractionHand hand) {
        if(distanceToSqr(player)>9||!player.hasLineOfSight(this))return false;
        return collectSlot(player,hand==InteractionHand.MAIN_HAND?player.getInventory().selected:Inventory.SLOT_OFFHAND);
    }

    /** 成功整蛊的触发者自动换瓶，不要求拿在手里或冲到演员身边；场景已负责距离、归属和存活检查。 */
    boolean collectAutomatically(ServerPlayer player) {
        if(!collectionReady()||!player.isAlive()||player.isSpectator()||player.level()!=level())return false;
        var inventory=player.getInventory();int stacked=-1;
        // 单个空瓶可直接原位替换，优先使用它，满背包也不会因先选中整组瓶子而错误拒绝。
        for(int slot=0;slot<inventory.items.size();slot++) {
            ItemStack item=inventory.getItem(slot);
            if(!item.is(Items.GLASS_BOTTLE))continue;
            if(item.getCount()==1)return automaticResult(player,slot);
            if(stacked<0)stacked=slot;
        }
        ItemStack offhand=inventory.getItem(Inventory.SLOT_OFFHAND);
        if(offhand.is(Items.GLASS_BOTTLE)&&(offhand.getCount()==1||stacked<0))stacked=Inventory.SLOT_OFFHAND;
        return stacked>=0&&automaticResult(player,stacked);
    }

    private boolean automaticResult(ServerPlayer player,int slot) {
        if(!collectSlot(player,slot))return false;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.naiwa.gas_collected"),true);
        return true;
    }

    /** 手动与自动共用大笑窗口、单轮次数和常驻冷却，不让重播动作或多次 tick 复制奖励。 */
    private boolean collectionReady() {
        double seconds=animationSeconds(0);
        return !level().isClientSide&&isAlive()&&!collected&&level().getGameTime()>=nextGas
                &&animation().equals("laugh_sequence")&&seconds>=NaiwaAnimations.length("laugh_enter")
                &&seconds<NaiwaAnimations.length("laugh_enter")+NaiwaAnimations.length("laugh");
    }

    /** 只动主背包/快捷栏或副手的来源格；有空间才能消耗整组中的一个，无法交付绝不吞瓶。 */
    private boolean collectSlot(Player player,int slot) {
        var inventory=player.getInventory();ItemStack bottles=inventory.getItem(slot);
        if(!collectionReady()||!player.isAlive()||player.isSpectator()||player.level()!=level()||!bottles.is(Items.GLASS_BOTTLE))return false;
        if (bottles.getCount()==1) inventory.setItem(slot,new ItemStack(Naiwa.GAS.get()));
        else {
            // 原版空间判定同时包含副手的未满堆叠，避免满背包但副手可合并时误判无处交付。
            if(gasSpaceProbe==null)gasSpaceProbe=new ItemStack(Naiwa.GAS.get());
            boolean room=inventory.getFreeSlot()>=0||inventory.getSlotWithRemainingSpace(gasSpaceProbe)>=0;
            if (!room || !inventory.add(new ItemStack(Naiwa.GAS.get()))) return false;
            bottles.shrink(1);
        }
        // 常驻本体也能被空手逗笑，因此另设五分钟采集间隔，不能绕过整蛊间隔连续刷瓶。
        collected=true; nextGas=level().getGameTime()+6000; inventory.setChanged();return true;
    }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putBoolean("NaiwaTemporary",temporary);tag.putLong("NaiwaNextGas",nextGas);
        NaiwaPranks.saveScopeActor(this,tag);
    }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        nextGas=tag.getLong("NaiwaNextGas");
        // 兼容 0.3.13 及以前暂借常驻奶蛙的存档；当前版本只创建临时演员。
        if(level() instanceof net.minecraft.server.level.ServerLevel server && tag.contains("NaiwaScopeRecovery")) {
            var recovery=tag.getCompound("NaiwaScopeRecovery");setNoAi(recovery.getBoolean("NoAI"));
            var original=new net.minecraft.world.phys.Vec3(recovery.getDouble("X"),recovery.getDouble("Y"),recovery.getDouble("Z"));
            // 重载时原位未加载或已被占用就保留当前落点，但无条件恢复原 AI，不能强制加载区块或塞回障碍。
            if(Double.isFinite(original.x)&&Double.isFinite(original.y)&&Double.isFinite(original.z)&&NaiwaPranks.safe(server,original,true))setPos(original);
        }
        if (tag.getBoolean("NaiwaTemporary")) discard();
    }
}
