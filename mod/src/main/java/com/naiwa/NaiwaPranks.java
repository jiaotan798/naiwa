package com.naiwa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** 十种整蛊共用调度；钓鱼截走渔获，钻石整蛊暂存真实掉落并在演员退场时原数交还。 */
public final class NaiwaPranks {
    private static final String NEXT="naiwa.prank_next", LAST="naiwa.prank_last", RESPAWN="naiwa.respawn_mock";
    private static final Map<UUID, Scene> SCENES=new HashMap<>();
    // 只缓存候选几何，不缓存世界检查结果；拆墙、液体、实体和区块变化仍在下一 tick 生效。
    private static final Map<UUID,MockCandidates> MOCK_CANDIDATES=new HashMap<>();
    private record MockSpot(Vec3 position,BlockPos feet,net.minecraft.world.phys.AABB bounds) {}
    private record MockCandidates(Vec3 origin,float yaw,double distance,List<MockSpot> spots) {}
    private static final Map<UUID, BlockPos> DOORS=new HashMap<>();
    private static final Map<UUID, BlockPos> CHESTS=new HashMap<>();
    private static final Map<UUID, ClosedChest> CLOSED_CHESTS=new HashMap<>();
    private static final Map<UUID, Attention> ATTENTION=new HashMap<>();
    private record ClosedChest(BlockPos pos,long tick) {}
    private record Attention(Vec3 position,Vec3 look,long since) {}
    private NaiwaPranks() {}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(NaiwaPranks::tick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                cancel(p); NaiwaGameplay.cleanup(p);
                // 与临时场景分开记录；只有真正进入死亡流程才安排复活点嘲笑。
                p.getPersistentData().putBoolean(RESPAWN,true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            var from=e.getOriginal().getPersistentData(); var to=e.getEntity().getPersistentData();
            to.putLong(NEXT,from.getLong(NEXT)); to.putString(LAST,from.getString(LAST));
            if (e.isWasDeath() && from.getBoolean(RESPAWN)) to.putBoolean(RESPAWN,true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> cancel((ServerPlayer)e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> cancel((ServerPlayer)e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getHand()==InteractionHand.MAIN_HAND
                    && e.getLevel().getBlockState(e.getPos()).is(BlockTags.WOODEN_DOORS)) DOORS.put(p.getUUID(),e.getPos().immutable());
            if(!e.isCanceled() && e.getEntity() instanceof ServerPlayer p && e.getHand()==InteractionHand.MAIN_HAND
                    && e.getLevel().getBlockEntity(e.getPos()) instanceof ChestBlockEntity) CHESTS.put(p.getUUID(),e.getPos().immutable());
        });
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close e) -> {
            if(!(e.getEntity() instanceof ServerPlayer p))return;
            BlockPos pos=CHESTS.remove(p.getUUID());
            if(pos!=null && ready(p) && chestMenu(p,e.getContainer(),pos))CLOSED_CHESTS.put(p.getUUID(),new ClosedChest(pos,p.level().getGameTime()));
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            Scene active=SCENES.get(p.getUUID());
            if (active!=null && active.kind.equals("mine")) {active.lastDig=p.level().getGameTime(); return;}
            if (!ready(p)) return;
            if (p.level().getBlockState(e.getPos()).is(BlockTags.LOGS)) startBlock(p,"tree",e.getPos());
            // 钻石矿留给真实掉落事件决定整蛊，不能起镐时被摸鱼场景抢走同一轮调度。
            else if (!p.level().getBlockState(e.getPos()).is(BlockTags.DIAMOND_ORES)&&!p.level().canSeeSky(p.blockPosition()) && p.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) startBlock(p,"mine",e.getPos());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(BlockEvent.BreakEvent e)->{
            if(e.isCanceled()||!(e.getPlayer() instanceof ServerPlayer p))return;
            Scene scene=SCENES.get(p.getUUID());
            if(scene!=null&&scene.kind.equals("mine")&&p.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) {
                // 事件之后仍可能被其他监听者取消；在 tick 中确认方块实际变化，不能只挥镐就发出露馅提示。
                scene.pendingMinedBlock=e.getPos().immutable();scene.pendingMinedState=e.getState();
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,NaiwaPranks::diamondDrops);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->{
            // 在最终世界保存前结算暂存钻石，不能等停服保存完才生成返还物。
            for(Scene scene:SCENES.values())scene.close();SCENES.clear();MOCK_CANDIDATES.clear();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            for (Scene scene:SCENES.values()) scene.close();
            SCENES.clear();MOCK_CANDIDATES.clear(); DOORS.clear();CHESTS.clear();CLOSED_CHESTS.clear();ATTENTION.clear();
        });
    }

    /** 复活和图腾落地待办也占用调度，前方受阻时不能被另一种整蛊抢走出场。 */
    public static boolean busy(ServerPlayer p) {return SCENES.containsKey(p.getUUID()) || p.getPersistentData().getBoolean(RESPAWN) || p.hasEffect(Naiwa.ASCENDING) || NaiwaGameplay.waitingForTotemLanding(p);}
    /** 归属提示只定位场景，再按实体实例确认；取消、换场和重载后旧提示不会继续占用演员身份。 */
    public static boolean isActor(NaiwaEntity frog) {
        Scene scene=frog==null?null:SCENES.get(frog.prankOwner);
        return scene!=null&&scene.frog==frog;
    }
    /** 不以实体 UUID 代替实例判断，防止卸载后的旧对象或重载副本误抑制真实钻石返还。 */
    public static boolean isDecoyActive(NaiwaDecoy decoy) {
        Scene scene=decoy==null?null:SCENES.get(decoy.prankOwner);
        return scene!=null&&scene.decoy==decoy;
    }

    /** 四秒避让期间不能靠攻击包先于下一 tick 到达而提前识破；窗口未开始或已结束仍可识破。 */
    static boolean canExposeDecoy(NaiwaDecoy decoy) {
        Scene scene=SCENES.get(decoy.prankOwner);
        return scene==null||scene.decoy!=decoy||scene.escapeStart<0||decoy.level().getGameTime()-scene.escapeStart>=80;
    }

    /** 保留旧版借用本体的恢复格式；当前望远镜只用临时演员，不再往常驻实体写入恢复标记。 */
    static void saveScopeActor(NaiwaEntity frog,net.minecraft.nbt.CompoundTag tag) {
        for(Scene scene:SCENES.values())if(scene.frog==frog&&!frog.isTemporary()&&scene.kind.equals("scope")) {
            var recovery=new net.minecraft.nbt.CompoundTag();recovery.putBoolean("NoAI",scene.initialNoAi);
            recovery.putDouble("X",scene.initialPosition.x);recovery.putDouble("Y",scene.initialPosition.y);recovery.putDouble("Z",scene.initialPosition.z);
            tag.put("NaiwaScopeRecovery",recovery);return;
        }
    }
    private static boolean shield(ServerPlayer p) {return p.getMainHandItem().is(Items.SHIELD)||p.getOffhandItem().is(Items.SHIELD);}
    private static boolean ready(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || busy(p) || shield(p)) return false;
        long now=p.level().getGameTime(); var data=p.getPersistentData();
        if (!data.contains(NEXT)) {data.putLong(NEXT,now+200); return false;}
        return now>=data.getLong(NEXT);
    }
    private static boolean allowed(ServerPlayer p,String kind) {
        return NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get() || !p.getPersistentData().getString(LAST).equals(kind);
    }

    private static void tick(ServerTickEvent.Post e) {
        for (ServerPlayer p:e.getServer().getPlayerList().getPlayers()) {
            Scene active=SCENES.get(p.getUUID());
            if (active!=null) {
                if (!p.isAlive() || p.isSpectator() || active.frog.level()!=p.level() || !active.tick(p)) cancel(p);
                continue;
            }
            if (p.isAlive() && p.getPersistentData().getBoolean(RESPAWN)) {
                // 仅在正前方成功入场后消费；受阻期间等待当前位置和朝向变化，不启动其他整蛊。
                if(mock(p))p.getPersistentData().remove(RESPAWN);
                continue;
            }
            BlockPos door=DOORS.remove(p.getUUID());
            if (!ready(p)) {CLOSED_CHESTS.remove(p.getUUID());ATTENTION.remove(p.getUUID());continue;}
            ClosedChest chest=CLOSED_CHESTS.get(p.getUUID());
            if(chest!=null) {
                long elapsed=p.level().getGameTime()-chest.tick;
                // 关界面后留半秒让物品包到齐；只用真实关闭菜单作触发，演出开盖由客户端表现，不改红石打开人数。
                if(elapsed>40 || p.containerMenu!=p.inventoryMenu)CLOSED_CHESTS.remove(p.getUUID());
                else if(elapsed>=10 && ChestBlockEntity.getOpenCount(p.level(),chest.pos)==0) {
                    CLOSED_CHESTS.remove(p.getUUID());if(startChest(p,chest.pos))continue;
                }
            }
            if (door!=null && p.level().getBlockState(door).hasProperty(DoorBlock.OPEN)
                    && p.level().getBlockState(door).getValue(DoorBlock.OPEN) && allowed(p,"door")) {
                if (startBlock(p,"door",door)) continue;
            }
            // 持续挥镐或使用物品属于活动，不能被三秒观察计时误判为发呆，抢占尚未结算的钻石开采。
            if(p.swinging||p.isUsingItem())ATTENTION.remove(p.getUUID());
            if (p.level().getGameTime()%20!=0) continue;
            if (p.getVehicle() instanceof Boat boat && onWater(boat) && boat.getControllingPassenger()==p && boat.getType()==net.minecraft.world.entity.EntityType.BOAT
                    && boat.getVariant()!=Boat.Type.BAMBOO && boat.getPassengers().size()==1 && allowed(p,"boat")) {
                NaiwaEntity frog=create(p.serverLevel(),boat.position());
                if (frog!=null && frog.startRiding(boat)) {
                    Scene scene=begin(p,"boat",frog,null); scene.boat=boat; frog.setAct("boat"); continue;
                }
                if (frog!=null) frog.discard();
            }
            if (allowed(p,"mirror")) startMirror(p);
            if(busy(p)||p.fishing!=null||p.containerMenu!=p.inventoryMenu||p.isPassenger())continue;
            // 望远镜由客户端完成原版开镜后报告真实视野；服务端不能按固定近距离先行出场。
            if(p.isUsingItem())continue;
            Attention previous=ATTENTION.get(p.getUUID());long now=p.level().getGameTime();
            if(previous==null || previous.position.distanceToSqr(p.position())>.5 || previous.look.dot(p.getLookAngle())<.98) {
                ATTENTION.put(p.getUUID(),new Attention(p.position(),p.getLookAngle(),now));
            } else if(now-previous.since>=60)startCreeper(p);
        }
    }

    /** 普通及大箱子均按原版实际容器身份匹配，不改菜单、物品或红石打开人数。 */
    private static boolean chestMenu(ServerPlayer p,net.minecraft.world.inventory.AbstractContainerMenu menu,BlockPos pos) {
        if(!(menu instanceof ChestMenu chest) || !(p.level().getBlockEntity(pos) instanceof ChestBlockEntity block))return false;
        return chest.getContainer()==block || chest.getContainer() instanceof net.minecraft.world.CompoundContainer pair && pair.contains(block);
    }

    /** 十二格内出现真实引燃即让位，覆盖背后候选站位周围的可听危险提示。 */
    private static boolean realFuse(ServerPlayer p) {
        return !p.level().getEntitiesOfClass(Creeper.class,p.getBoundingBox().inflate(12),c->c.isAlive()&&(c.isIgnited()||c.getSwellDir()>0)).isEmpty();
    }

    /** 背后可听见且初始不可见才播一次假引燃声；真实苦力怕引燃优先，不重复催反应。 */
    static boolean startCreeper(ServerPlayer p) {
        if(!ready(p)||!allowed(p,"creeper")||!p.onGround()||realFuse(p))return false;
        Vec3 look=Vec3.directionFromRotation(0,p.getYRot());
        for(double radius:new double[]{3,2,4})for(double angle:new double[]{Math.PI,2.4,-2.4})for(int dy:new int[]{0,1,-1}) {
            Vec3 point=Vec3.atBottomCenterOf(BlockPos.containing(p.position().add(look.yRot((float)angle).scale(radius))).above(dy));
            if(!safe(p.serverLevel(),point,true)||!NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),point.add(0,1.5,0))
                    ||p.getLookAngle().dot(point.add(0,1.5,0).subtract(p.getEyePosition()).normalize())>.2)continue;
            NaiwaEntity frog=create(p.serverLevel(),point);if(frog==null)continue;
            begin(p,"creeper",frog,null);frog.setAct("creeper_wait");frog.play("idle");
            p.level().playSound(null,frog,SoundEvents.CREEPER_PRIMED,SoundSource.HOSTILE,.7F,1);return true;
        }
        return false;
    }

    /** 每 0.2 格验证避让路线及危险地面，不加载区块、不把玩家引向坑、岩浆或火源。 */
    static boolean lurePath(ServerLevel level,Vec3 from,Vec3 to) {
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)/.2));
        for(int i=0;i<=steps;i++) {
            Vec3 point=from.lerp(to,(double)i/steps);
            if(!safe(level,point,true))return false;
            for(BlockPos b:BlockPos.betweenClosed(BlockPos.containing(point.add(-.5,-1,-.5)),BlockPos.containing(point.add(.5,.5,.5)))) {
                var state=level.getBlockState(b);
                if(state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CACTUS)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.WITHER_ROSE)
                        ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE)||state.is(BlockTags.FIRE))return false;
            }
        }
        return true;
    }

    /** 使用已计算的原版掉落，保留时运数量和物品组件；不取消经验、精准采集或其它掉落。 */
    private static void diamondDrops(BlockDropsEvent event) {
        if(event.isCanceled()||!(event.getBreaker() instanceof ServerPlayer p)||!event.getState().is(BlockTags.DIAMOND_ORES)
                ||!event.getTool().is(net.minecraft.tags.ItemTags.PICKAXES))return;
        var diamonds=event.getDrops().stream().filter(item->item.getItem().is(Items.DIAMOND)).toList();
        if(!diamonds.isEmpty()&&startDiamond(p,diamonds.stream().map(item->item.getItem().copy()).toList()))event.getDrops().removeAll(diamonds);
    }

    /** 矿点附近先找完整安全路线；成功入网后才接管真实钻石，不满足条件就让原版正常掉落。 */
    static boolean startDiamond(ServerPlayer p,List<ItemStack> diamonds) {
        if(diamonds.isEmpty()||diamonds.stream().anyMatch(stack->stack.isEmpty()||!stack.is(Items.DIAMOND))
                ||!ready(p)||!allowed(p,"diamond")||!p.onGround()||p.isInWater())return false;
        Vec3 facing=Vec3.directionFromRotation(0,p.getYRot());
        // 优先沿视线，受矿壁阻挡时检查两侧及身后；整段路径仍禁止坑、液体和危险方块。
        for(double angle:new double[]{0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2,Math.PI*3/4,-Math.PI*3/4,Math.PI}) {
            Vec3 direction=facing.yRot((float)angle),first=p.position().add(direction.scale(3.5)),actorPoint=p.position().add(direction.scale(7));
            if(!lurePath(p.serverLevel(),p.position(),actorPoint)||!NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),first.add(0,.3,0)))continue;
            NaiwaEntity frog=prepareActor(p.serverLevel(),actorPoint);if(frog==null)continue;
            NaiwaDecoy decoy=Naiwa.DECOY.get().create(p.level());decoy.setPos(first);
            if(!p.serverLevel().addFreshEntity(decoy))return false;
            // 暂存于可保存的非物品实体，漏斗无法提取；场景尚未建立前不把钻石从原版列表移除。
            Scene scene=begin(p,"diamond",frog,null);scene.decoy=decoy;decoy.prankOwner=p.getUUID();scene.takeoff=direction;scene.land=actorPoint;scene.hopTo=first;
            decoy.holdDiamonds(diamonds);return true;
        }
        return false;
    }

    /** 真实开关箱后只探头，不制作额外动作；物理包围盒留在原版箱体上方，箱内部分仅作渲染。 */
    static boolean startChest(ServerPlayer p,BlockPos pos) {
        if(!ready(p)||!allowed(p,"chest")||!(p.level().getBlockEntity(pos) instanceof ChestBlockEntity)
                ||p.distanceToSqr(Vec3.atCenterOf(pos))>36||ChestBlockEntity.getOpenCount(p.level(),pos)!=0)return false;
        Vec3 top=new Vec3(pos.getX()+.5,pos.getY()+.875,pos.getZ()+.5);
        NaiwaEntity frog=prepareActor(p.serverLevel(),top,"chest_peek");if(frog==null||!p.serverLevel().addFreshEntity(frog))return false;
        begin(p,"chest",frog,pos.immutable());frog.setNoGravity(true);frog.play("idle");return true;
    }

    /** 真实开镜后在水平正前方四格找安全地面；图片标记只属于本轮触发者，不借用常驻奶蛙。 */
    static boolean startScope(ServerPlayer p,int distance) {
        if(distance!=4||!ready(p)||!allowed(p,"scope")||!p.isUsingItem()||!p.getUseItem().is(Items.SPYGLASS))return false;
        Vec3 forward=Vec3.directionFromRotation(0,p.getYRot()),point=null;
        for(int dy:new int[]{0,-1,1,-2,2}) {
            Vec3 candidate=p.position().add(forward.scale(4)).add(0,dy,0);
            if(safe(p.serverLevel(),candidate,true)&&NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),candidate.add(0,1.7,0))){point=candidate;break;}
        }
        if(point==null)return false;
        NaiwaEntity frog=prepareActor(p.serverLevel(),point);if(frog==null)return false;
        frog.setYRot(p.getYRot()+180);frog.setYHeadRot(frog.getYRot());frog.yBodyRot=frog.getYRot();frog.play("idle");frog.setScopeViewer(p.getId());
        if(!p.serverLevel().addFreshEntity(frog))return false;
        Scene scene=begin(p,"scope",frog,null);scene.land=p.position();return true;
    }

    /** 船和浮漂会浮到液面稍上方，普通 isInWater 可能为假；仅补查脚下 0.2 格的接触水面。 */
    private static boolean onWater(net.minecraft.world.entity.Entity entity) {
        return entity.isInWater() || entity.level().getFluidState(BlockPos.containing(entity.position().add(0,-.2,0))).is(net.minecraft.tags.FluidTags.WATER);
    }

    /** 仅供原版收竿结算点调用：事件全部结束且演员成功入网才返回 true，通知原版截走渔获并照常扣一次耐久。 */
    public static boolean stealFishingCatch(ItemFishedEvent caught) {
        if (caught.isCanceled() || !(caught.getEntity() instanceof ServerPlayer p) || !ready(p) || !allowed(p,"fish")) return false;
        FishingHook hook=caught.getHookEntity();
        if (!hook.isAlive() || hook.getPlayerOwner()!=p || hook.level()!=p.level()
                || p.distanceToSqr(hook)>=144 || caught.getDrops().stream().noneMatch(stack->!stack.isEmpty())) return false;
        var level=p.serverLevel();BlockPos surface=hook.blockPosition();
        if (!level.hasChunkAt(surface)) return false;
        // 咬钩会让浮漂下沉；按真实水面还原两格深的起跳空间，不再用“浮漂减 1.2”把演员压进池底。
        for(int i=0;i<3&&level.getFluidState(surface.above()).is(net.minecraft.tags.FluidTags.WATER);i++)surface=surface.above();
        if (!level.getFluidState(surface).is(net.minecraft.tags.FluidTags.WATER)
                || !level.getFluidState(surface.below()).is(net.minecraft.tags.FluidTags.WATER)) return false;
        Vec3 takeoff=new Vec3(hook.getX(),surface.getY()-1+.02,hook.getZ());
        NaiwaEntity frog=prepareActor(level,takeoff);
        if (frog==null) return false;
        frog.setNoGravity(true);frog.setAct("fish_land");
        Vec3 land=fishLanding(p,frog,hook.position(),null);
        // 先确认完整出水弧线，再加入世界并开始共享冷却；失败时客户端连一帧演员也不应收到。
        if (land==null || !level.addFreshEntity(frog)) return false;
        // 这是“抢走附魔书”的固定道具，不复制真实渔获，也不给玩家额外附魔或掉落。
        ItemStack book=new ItemStack(Items.ENCHANTED_BOOK);
        book.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
        frog.setItemInHand(InteractionHand.MAIN_HAND,book);
        Scene scene=begin(p,"fish",frog,null);scene.land=land;scene.takeoff=takeoff;scene.landed=level.getGameTime();
        return true;
    }

    /** 普通场景在 2.5、3.5、4.5 格近邻圈寻找完整站位；死亡和图腾嘲笑另走正前方搜索。 */
    public static Vec3 findSpot(ServerPlayer p,Vec3 center,boolean visible) {
        return findSpot(p,center,visible,null);
    }

    /** 收竿时用演员实际位置筛掉无法沿出水弧线到达的岸点；普通现身不附加水下路径条件。 */
    static Vec3 findSpot(ServerPlayer p,Vec3 center,boolean visible,NaiwaEntity swimmer) {
        Vec3 look=Vec3.directionFromRotation(0,p.getYRot());
        // 原有近圈与朝向优先；曲岸常在侧后方，浮漂惯性也会让岸边落在原先单圈之外。
        for (double radius:new double[]{2.5,3.5,4.5}) {
            for (double angle:new double[]{0,.6,-.6,1.2,-1.2,Math.PI,2,-2,2.6,-2.6}) {
                Vec3 candidate=center.add(look.yRot((float)angle).scale(radius));
                // 浮漂/船脚部常略低于整数水面；两格高的天然岸顶此时需要 +3，不能把水面取整误当作岸不可达。
                for (int dy:new int[]{0,1,-1,-2,2,3}) {
                    BlockPos feet=BlockPos.containing(candidate).above(dy);
                    Vec3 pos=Vec3.atBottomCenterOf(feet);
                    if (p.position().distanceToSqr(pos)<2) continue;
                    if (safe(p.serverLevel(),pos,true) && (!visible || NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),pos.add(0,1,0)))
                            && (swimmer==null || fishPathClear(p.serverLevel(),swimmer,swimmer.position(),pos))) return pos;
                }
            }
        }
        return null;
    }

    private static Vec3 fishPoint(Vec3 from,Vec3 to,double t) {return from.lerp(to,t).add(0,Math.sin(t*Math.PI)*1.2,0);}

    /** 最多约每 0.15 格检查整个身体；包含水下起点，不加载新区块，也不能只因岸顶可站立就穿过凸岸或枝叶。 */
    static boolean fishPathClear(ServerLevel level,NaiwaEntity frog,Vec3 from,Vec3 to) {
        int steps=Math.max(15,(int)Math.ceil((from.distanceTo(to)+Math.PI*1.2)/.15));
        for(int i=0;i<=steps;i++) {
            Vec3 point=fishPoint(from,to,(double)i/steps);
            var box=new net.minecraft.world.phys.AABB(point.x-.45,point.y,point.z-.45,point.x+.45,point.y+2,point.z+.45);
            if(point.y<level.getMinBuildHeight()||point.y+2>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box)
                    ||!level.hasChunkAt(BlockPos.containing(box.minX,box.minY,box.minZ))||!level.hasChunkAt(BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                    ||!level.noCollision(frog,box))return false;
        }
        return true;
    }

    /** 浮漂漂移后优先保留仍可达的原岸点；原路线失效才在当前浮漂附近寻找替代点。 */
    static Vec3 fishLanding(ServerPlayer p,NaiwaEntity frog,Vec3 center,Vec3 previous) {
        if(previous!=null&&safe(p.serverLevel(),previous,true)&&fishPathClear(p.serverLevel(),frog,frog.position(),previous))return previous;
        return findSpot(p,center,false,frog);
    }

    static boolean safe(ServerLevel level,Vec3 pos,boolean ground) {
        return safe(level,pos,ground,BlockPos.containing(pos),null);
    }
    private static boolean safe(ServerLevel level,Vec3 pos,boolean ground,BlockPos feet,net.minecraft.world.phys.AABB cachedBounds) {
        if (!level.hasChunkAt(feet) || pos.y<level.getMinBuildHeight() || pos.y+2>=level.getMaxBuildHeight()) return false;
        if (ground && !level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)) return false;
        var box=cachedBounds!=null?cachedBounds:new net.minecraft.world.phys.AABB(pos.x-.45,pos.y,pos.z-.45,pos.x+.45,pos.y+2,pos.z+.45);
        if (!level.hasChunkAt(BlockPos.containing(box.minX,box.minY,box.minZ)) || !level.hasChunkAt(BlockPos.containing(box.maxX,box.maxY,box.maxZ))) return false;
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(box) && !level.containsAnyLiquid(box);
    }

    /** 只构造并检查演员，调用者完成场景专属验证后才入网，防止失败候选闪现。 */
    private static NaiwaEntity prepareActor(ServerLevel level,Vec3 pos) {
        return prepareActor(level,pos,"");
    }

    /** 先应用探头尺寸再检查碰撞，不能用两格高全身空间误拒绝箱内头部表现。 */
    private static NaiwaEntity prepareActor(ServerLevel level,Vec3 pos,String action) {
        if (!level.hasChunkAt(BlockPos.containing(pos))) return null;
        NaiwaEntity frog=Naiwa.ENTITY.get().create(level);frog.setAct(action);frog.setPos(pos);frog.temporary();
        var volume=frog.getBoundingBox();
        if (!level.getWorldBorder().isWithinBounds(volume) || !level.hasChunkAt(BlockPos.containing(volume.minX,volume.minY,volume.minZ))
                || !level.hasChunkAt(BlockPos.containing(volume.maxX,volume.maxY,volume.maxZ))) return null;
        // 水中演员和船座允许液体及载具重叠，但整个身体仍不能生成在实心方块里。
        if (level.getBlockCollisions(frog,frog.getBoundingBox()).iterator().hasNext()) return null;
        // 原版装备掉落在概率为 0 时直接跳过，包含抢夺附魔；演出道具不能变成额外奖励。
        frog.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND,0);
        return frog;
    }

    private static NaiwaEntity create(ServerLevel level,Vec3 pos) {
        NaiwaEntity frog=prepareActor(level,pos);
        return frog!=null&&level.addFreshEntity(frog)?frog:null;
    }

    private static Scene begin(ServerPlayer p,String kind,NaiwaEntity frog,BlockPos anchor) {
        Scene scene=new Scene(p,kind,frog,anchor,p.level().getGameTime()); SCENES.put(p.getUUID(),scene);
        p.getPersistentData().putString(LAST,kind);
        // 开始时固定本轮等待，重载设置不重置已排定冷却；零间隔也由 SCENES 保证同一玩家不会重叠演出。
        p.getPersistentData().putLong(NEXT,p.level().getGameTime()+NaiwaConfig.nextIntervalTicks(p.getRandom()));
        if(frog.isAddedToLevel())p.serverLevel().sendParticles(ParticleTypes.POOF,frog.getX(),frog.getY()+1,frog.getZ(),10,.3,.5,.3,.01);
        return scene;
    }
    /** 先验证安全站位再创建演员并消费间隔；矿洞按玩家身后选点，门和树按目标另一侧选点。 */
    static boolean startBlock(ServerPlayer p,String kind,BlockPos block) {
        if (!allowed(p,kind)) return false;
        Vec3 center=Vec3.atBottomCenterOf(block);
        Vec3 away=center.subtract(p.position()).multiply(1,0,1).normalize();
        // 树与门使用目标另一侧；矿洞按玩家朝向找身后地面，不再把挖掘目标当作站位高度。
        Vec3 wanted=center.add(away.scale(1.3));
        Vec3 point=kind.equals("mine")?miningSpot(p):null;
        if(!kind.equals("mine"))for (int dy:new int[]{0,-1,1,-2}) {
            Vec3 candidate=new Vec3(wanted.x,block.getY()+dy,wanted.z);
            if (safe(p.serverLevel(),candidate,true)) {point=candidate; break;}
        }
        if (point==null || p.position().distanceToSqr(point)<1) return false;
        NaiwaEntity frog=create(p.serverLevel(),point); if (frog==null) return false;
        Scene scene=begin(p,kind,frog,block.immutable()); scene.lastDig=scene.start; scene.land=point;
        if (kind.equals("mine")) {
            frog.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));frog.setYRot(p.getYRot()+180);
            scene.mineFace=miningTarget(frog);scene.faceMiningBlock();
        }
        frog.setAct(kind); return true;
    }

    /** 以玩家脚下高度搜索身后 5～6 格；没有远处安全站位就放弃，不能为触发而退回贴身位置。 */
    private static Vec3 miningSpot(ServerPlayer p) {
        Vec3 forward=Vec3.directionFromRotation(0,p.getYRot());
        for(double radius:new double[]{5,6})for(double angle:new double[]{Math.PI,Math.PI-.4,Math.PI+.4,Math.PI-.8,Math.PI+.8})for(int dy:new int[]{0,1,-1,-2}) {
            Vec3 horizontal=p.position().add(forward.yRot((float)angle).scale(radius));
            Vec3 point=new Vec3(horizontal.x,p.blockPosition().getY()+dy,horizontal.z);
            if(safe(p.serverLevel(),point,true)
                    &&NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),point.add(0,1.4,0)))return point;
        }
        return null;
    }

    /** 按实际可见碰撞面到眼睛的距离选最近方块；只在出场或目标失效时搜索，不逐 tick 扫描。 */
    static BlockHitResult miningTarget(NaiwaEntity frog) {
        var level=frog.level();Vec3 eye=frog.getEyePosition();BlockHitResult nearest=null;double distance=Double.MAX_VALUE;
        for(BlockPos pos:BlockPos.betweenClosed(frog.blockPosition().offset(-2,-1,-2),frog.blockPosition().offset(2,3,2))) {
            if(!level.hasChunkAt(pos))continue;
            for(var local:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs()) {
                var box=local.move(pos);Vec3 point=new Vec3(net.minecraft.util.Mth.clamp(eye.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(eye.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(eye.z,box.minZ,box.maxZ));
                // 终点略进入碰撞体，避免射线恰好落在面边界而漏判；视线被其他方块挡住则不能选它。
                var hit=level.clip(new net.minecraft.world.level.ClipContext(eye,point.lerp(box.getCenter(),.001),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                double squared=eye.distanceToSqr(hit.getLocation());
                if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&squared<distance){distance=squared;nearest=hit;}
            }
        }
        return nearest;
    }

    /** 普通玻璃对侧有安全地面与光学视线时才出场；出场和跟随共用近墙净空规则。 */
    static void startMirror(ServerPlayer p) {
        HitResult hit=p.pick(5,1,false);
        if (!(hit instanceof BlockHitResult block) || !p.level().getBlockState(block.getBlockPos()).is(Blocks.GLASS)
                || block.getDirection().getAxis()==Direction.Axis.Y) return;
        Direction.Axis axis=block.getDirection().getAxis(); double plane=axis==Direction.Axis.X ? block.getBlockPos().getX()+.5 : block.getBlockPos().getZ()+.5;
        Vec3 point=mirrorPosition(p.position(),axis,plane);
        if (!safe(p.serverLevel(),point,true)) return;
        if (!glassLine(p.serverLevel(),p.getEyePosition(),point.add(0,1.5,0),block.getBlockPos(),axis)) return;
        NaiwaEntity frog=create(p.serverLevel(),point); if (frog==null) return;
        frog.setNoGravity(true); frog.setAct("mirror"); Scene scene=begin(p,"mirror",frog,block.getBlockPos());
        scene.axis=axis; scene.plane=plane; scene.side=coordinate(p.position(),axis)-plane;
    }

    static Vec3 reflect(Vec3 point,Direction.Axis axis,double plane) {
        return axis==Direction.Axis.X ? new Vec3(2*plane-point.x,point.y,point.z) : new Vec3(point.x,point.y,2*plane-point.z);
    }
    private static double coordinate(Vec3 point,Direction.Axis axis) {return axis==Direction.Axis.X?point.x:point.z;}

    /** 远处保持等距镜像；贴墙时仅限制垂直距离，避免更宽的奶蛙碰撞体把正常靠近误判为堵路。 */
    private static Vec3 mirrorPosition(Vec3 player,Direction.Axis axis,double plane) {
        Vec3 point=reflect(player,axis,plane);
        // 完整玻璃半厚 0.5 格，加 safe 的演员半宽 0.45 格及浮点余量；沿墙位置和高度不变。
        double distance=coordinate(point,axis)-plane;
        double normal=plane+Math.copySign(Math.max(Math.abs(distance),.951),distance);
        return axis==Direction.Axis.X?new Vec3(normal,point.y,point.z):new Vec3(point.x,point.y,normal);
    }

    /** 光学上只忽略本次固定平面的普通玻璃，碰撞检查仍使用原玻璃形状。 */
    private static boolean glassLine(ServerLevel level,Vec3 from,Vec3 to,BlockPos anchor,Direction.Axis axis) {
        Vec3 step=to.subtract(from).normalize().scale(.15); Vec3 cursor=from;
        boolean crossed=false;
        for (int i=0;i<100;i++) {
            var hit=level.clip(new net.minecraft.world.level.ClipContext(cursor,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType()==HitResult.Type.MISS) return crossed;
            BlockPos pos=hit.getBlockPos();
            if (!level.getBlockState(pos).is(Blocks.GLASS) || pos.get(axis)!=anchor.get(axis)) return false;
            crossed=true;
            cursor=hit.getLocation().add(step);
            if (cursor.distanceToSqr(to)<.04) return true;
        }
        return false;
    }

    /** 死亡复活和图腾落地共用当前水平正前方出场；只建临时演员，不挪用常驻奶蛙。 */
    public static boolean mock(ServerPlayer p) {
        return mockAtThreeBlocks(p);
    }

    /** 正前方三格只尝试原高度、上方和下方；受阻保留待办，不能扩大水平距离。 */
    private static boolean mockAtThreeBlocks(ServerPlayer p) {
        double preferredDistance=3;
        if(!p.isAlive()||p.isSpectator()||p.hasEffect(Naiwa.ASCENDING)||SCENES.containsKey(p.getUUID()))return false;
        MockCandidates candidates=MOCK_CANDIDATES.get(p.getUUID());
        if(candidates==null||!candidates.origin.equals(p.position())||candidates.yaw!=p.getYRot()||candidates.distance!=preferredDistance) {
            var spots=new java.util.ArrayList<MockSpot>();Vec3 forward=Vec3.directionFromRotation(0,p.getYRot());
            // 顺序仍为每个正前方距离依次尝试原高度、上方、下方；仅在输入变化时重建。
            {
                Vec3 horizontal=p.position().add(forward.scale(preferredDistance));
                for(int dy:new int[]{0,1,-1}) {
                    Vec3 spot=new Vec3(horizontal.x,p.getBlockY()+dy,horizontal.z);
                    spots.add(new MockSpot(spot,BlockPos.containing(spot),new net.minecraft.world.phys.AABB(spot.x-.45,spot.y,spot.z-.45,spot.x+.45,spot.y+2,spot.z+.45)));
                }
            }
            candidates=new MockCandidates(p.position(),p.getYRot(),preferredDistance,spots);MOCK_CANDIDATES.put(p.getUUID(),candidates);
        }
        for(MockSpot candidate:candidates.spots) {
            Vec3 spot=candidate.position;
            // 不沿左右候选圈兜底，不加载新区块；完整身体、实体支撑和眼睛视线均通过后才入场。
            if(!safe(p.serverLevel(),spot,true,candidate.feet,candidate.bounds)||!NaiwaGameplay.clearLine(p.level(),p.getEyePosition(),spot.add(0,1.5,0)))continue;
            NaiwaEntity frog=create(p.serverLevel(),spot);if(frog==null)continue;
            frog.setYRot(net.minecraft.util.Mth.wrapDegrees(p.getYRot()+180));frog.setYHeadRot(frog.getYRot());frog.yBodyRot=frog.getYRot();
            Scene scene=new Scene(p,"mock",frog,null,p.level().getGameTime());SCENES.put(p.getUUID(),scene);MOCK_CANDIDATES.remove(p.getUUID());scene.success(p);return true;
        }
        return false;
    }

    /** 图腾额外等待真实落地；死亡嘲笑则在原版复活后即可按当前朝向入场。 */
    static boolean mockAfterTotemLanding(ServerPlayer p) {
        return NaiwaGameplay.onSolidGround(p)&&mockAtThreeBlocks(p);
    }

    public static void cancel(ServerPlayer p) {
        MOCK_CANDIDATES.remove(p.getUUID());
        Scene scene=SCENES.remove(p.getUUID()); if (scene!=null) scene.close(); DOORS.remove(p.getUUID());
        CHESTS.remove(p.getUUID());CLOSED_CHESTS.remove(p.getUUID());ATTENTION.remove(p.getUUID());
    }

    /** 每一阶段只向成功大笑切换一次；超时或失效只清理，不补发笑气奖励。 */
    private static final class Scene {
        final String kind; final NaiwaEntity frog; final BlockPos anchor; final long start;
        final ItemStack initialHand;
        final String initialAct;
        final Vec3 initialPosition;final boolean initialNoAi;
        long laughing=-1,lastDig,closed=-1,landed=-1;
        BlockPos pendingMinedBlock;net.minecraft.world.level.block.state.BlockState pendingMinedState;boolean mined;
        BlockHitResult mineFace;long mineObserved=-1;
        // 四秒窗口只从第一次进入四格范围开始，后续避让不续期；hopped 控制半秒原地小跳。
        long failed=-1,hopped=-1,escapeStart=-1;
        NaiwaDecoy decoy;Vec3 hopTo;
        Boat boat; Vec3 land,takeoff; Direction.Axis axis; double plane,side;
        long boatIdle=-1,boatChange=-1,boatObserved=-1;
        Scene(ServerPlayer owner,String kind,NaiwaEntity frog,BlockPos anchor,long start) {
            this.kind=kind;this.frog=frog;this.anchor=anchor;this.start=start;
            // 所有场景（含不走 begin 的复活/图腾嘲笑）统一绑定；清理仍只以 SCENES 为准。
            frog.prankOwner=owner.getUUID();
            initialHand=frog.getMainHandItem();initialAct=frog.act();
            initialPosition=frog.position();initialNoAi=frog.isNoAi();
        }
        void success(ServerPlayer p) {
            if (laughing>=0) return;
            frog.setBoatSteering(kind.equals("boat")?4:0);
            laughing=p.level().getGameTime();if(!kind.equals("chest"))frog.setAct("");frog.play("laugh_sequence");
            // 钓鱼保留演出书；望远镜不清空手持物，但当前临时演员本为空手；矿洞等其余场景收起道具。
            if (!kind.equals("fish")&&!kind.equals("scope")) frog.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        }
        boolean watching(ServerPlayer p) {
            Vec3 direction=frog.getEyePosition().subtract(p.getEyePosition());
            // 观察必须同时满足距离、视锥及真实视线；镜内图片标记不替代放下后的观察检查。
            return direction.lengthSqr()<(kind.equals("scope")?16384:144) && p.getLookAngle().dot(direction.normalize())>.5 && p.hasLineOfSight(frog);
        }
        /** 装作挖最近的可见方块，不能追随玩家转头；目标被挖掉后才重新选点。 */
        void faceMiningBlock() {
            if(mineFace==null||frog.level().getBlockState(mineFace.getBlockPos()).getCollisionShape(frog.level(),mineFace.getBlockPos()).isEmpty())mineFace=miningTarget(frog);
            if(mineFace==null)return;
            Vec3 delta=mineFace.getLocation().subtract(frog.getEyePosition());
            if(delta.horizontalDistanceSqr()>1E-6)frog.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            frog.setYHeadRot(frog.getYRot());frog.yBodyRot=frog.getYRot();frog.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance())));
        }
        boolean tick(ServerPlayer p) {
            long now=p.level().getGameTime();
            if (!frog.isAlive() || frog.distanceToSqr(p)>(kind.equals("scope")?16384:400)) return false;
            if(kind.equals("chest")&&!(p.level().getBlockEntity(anchor) instanceof ChestBlockEntity))return false;
            if (laughing>=0) {
                // 奖励归本轮触发者；矿洞及望远镜演员本就较远，自动收集不再要求玩家赶到三格内。
                frog.collectAutomatically(p);
                // 留场覆盖源模型完整三段及一个结算 tick，换长动画时不能提前删演员、返钻石或关闭箱盖。
                return now-laughing<Math.ceil(NaiwaAnimations.length("laugh_sequence")*20)+1;
            }
            if (frog.hurtTime>0) return false;
            if (now-start>(kind.equals("fish")?1800:600) || shield(p) || p.hasEffect(Naiwa.ASCENDING)) return false;
            float yaw=(float)Math.toDegrees(Math.atan2(p.getZ()-frog.getZ(),p.getX()-frog.getX()))-90;
            if(!kind.equals("mine")){frog.setYRot(yaw); frog.setYHeadRot(yaw); frog.yBodyRot=yaw;}
            if(failed>=0) {
                // 失败只有挠头和短距离离开，不播放大笑，也不能采集笑气；真实碰撞挡路时原地结束。
                if(now-failed==10)frog.setAct("scratch");
                if(now-failed>30) {
                    Vec3 step=frog.position().subtract(p.position()).multiply(1,0,1).normalize().scale(.08);
                    if(safe(p.serverLevel(),frog.position().add(step),true))frog.move(net.minecraft.world.entity.MoverType.SELF,step);
                }
                return now-failed<50;
            }
            switch (kind) {
                case "creeper" -> {
                    if(realFuse(p)||frog.distanceToSqr(p)>64)return false;
                    if(watching(p)) {if(landed<0)landed=now;if(now-landed>=10)success(p);}
                    else {landed=-1;if(now-start>=60){failed=now;frog.setAct("scratch");frog.play("idle");}}
                }
                case "diamond" -> {
                    if(decoy==null||!decoy.isAlive()||!safe(p.serverLevel(),land,true))return false;
                    if(decoy.exposed()) {
                        decoy.setInvisible(true);if(!frog.isAddedToLevel()&&!p.serverLevel().addFreshEntity(frog))return false;
                        failed=now;frog.setAct("diamond_caught");frog.play("idle");break;
                    }
                    if(escapeStart>=0&&now-escapeStart>=80) {
                        if(landed<0){decoy.setPos(hopTo);frog.setPos(hopTo);if(!p.serverLevel().addFreshEntity(frog))return false;landed=now;frog.setAct("diamond_point");frog.play("idle");}
                    } else {
                        Vec3 delta=hopTo.subtract(p.position()).multiply(1,0,1);double distance=delta.length();
                        if(distance<4) {
                            Vec3 direction=distance>1e-7?delta.scale(1/distance):takeoff;
                            Vec3 target=hopTo.add(direction.scale(4.05-distance));
                            if(!lurePath(p.serverLevel(),hopTo,target))return false;
                            hopTo=target;land=target;frog.setPos(target);hopped=now;if(escapeStart<0)escapeStart=now;
                        }
                        if(hopped>=0){double t=Math.min(1,(now-hopped)/10.0);decoy.setPos(hopTo.add(0,Math.sin(t*Math.PI)*.25,0));if(t>=1)hopped=-1;}
                    }
                    if(landed>=0 && now-landed>=10 && watching(p)) {decoy.setInvisible(true);decoy.setPos(frog.position());success(p);}
                }
                case "chest" -> {
                    // 复用待机和大笑；探头至少停留两秒，且玩家此时已关界面并看见它才笑，不要求连续注视两秒。
                    if(now-start>=40&&p.containerMenu==p.inventoryMenu&&watching(p))success(p);
                }
                case "scope" -> {
                    // 镜内图片只受真实开镜控制；放下后撤图，再检查是否看见四格处演员。
                    if(p.position().distanceToSqr(land)>9)return false;
                    if(p.isUsingItem()) {if(!p.getUseItem().is(Items.SPYGLASS))return false;}
                    else {frog.setScopeViewer(-1);if(!watching(p))return false;if(landed<0)landed=now;if(now-landed>=10)success(p);}
                }
                case "mirror" -> {
                    if (!p.level().getBlockState(anchor).is(Blocks.GLASS) || (coordinate(p.position(),axis)-plane)*side<=0) return false;
                    Vec3 point=mirrorPosition(p.position(),axis,plane);
                    if (point.distanceToSqr(frog.position())>16 || !safe(p.serverLevel(),point,false)
                            || !glassLine(p.serverLevel(),p.getEyePosition(),point.add(0,1.5,0),anchor,axis)) return false;
                    // 检查沿途碰撞，不能靠每 tick 改坐标越过狭窄墙体。
                    Vec3 delta=point.subtract(frog.position()); int steps=Math.max(1,(int)Math.ceil(delta.length()/.2));
                    for (int i=1;i<=steps;i++) if (!safe(p.serverLevel(),frog.position().add(delta.scale((double)i/steps)),false)) return false;
                    frog.setPos(point); frog.setShiftKeyDown(p.isCrouching()); frog.setXRot(p.getXRot());
                    Vec3 look=p.getLookAngle(); look=axis==Direction.Axis.X?new Vec3(-look.x,look.y,look.z):new Vec3(look.x,look.y,-look.z);
                    float reflectedYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));
                    frog.setYRot(reflectedYaw);frog.setYHeadRot(reflectedYaw);frog.yBodyRot=reflectedYaw;
                    Vec3 toward=frog.getEyePosition().subtract(p.getEyePosition()).normalize();
                    if (p.getLookAngle().dot(toward)<.3) return false;
                    if (now-start>=160) {frog.setShiftKeyDown(false); frog.setNoGravity(false); success(p);}
                }
                case "door" -> {
                    var state=p.level().getBlockState(anchor);
                    if (!state.is(BlockTags.WOODEN_DOORS)) return false;
                    boolean open=state.getValue(DoorBlock.OPEN);
                    // 到期先结算；及时关门仅记录时间，不播短笑。关门三秒内重开才大笑。
                    if(closed<0){if(now-start>=60)success(p);else if(!open)closed=now;}
                    else if(now-closed>=60)return false;
                    else if(open)success(p);
                }
                case "tree" -> {
                    if (!p.level().getBlockState(anchor).is(BlockTags.LOGS)) {
                        if (landed<0) {landed=now;frog.setAct("tree_exposed");}
                        if (now-landed>=15 && watching(p)) success(p);
                    } else {
                        Vec3 from=Vec3.atBottomCenterOf(anchor).subtract(p.position()).multiply(1,0,1).normalize();
                        Vec3 point=Vec3.atBottomCenterOf(anchor).add(from.scale(1.25));
                        if (point.distanceToSqr(frog.position())<4 && safe(p.serverLevel(),point,true)) {
                            // 使用实际移动碰撞，不瞬移穿过树干；无法绕行时保持原地。
                            frog.move(net.minecraft.world.entity.MoverType.SELF,point.subtract(frog.position()).scale(.2));
                        }
                    }
                }
                case "mine" -> {
                    if(pendingMinedBlock!=null) {
                        if(p.level().hasChunkAt(pendingMinedBlock)&&!p.level().getBlockState(pendingMinedBlock).equals(pendingMinedState)) {
                            // 以实际破坏确认时刻重新起算，避免慢速挖掘或延迟确认跳过身后的三次敲击。
                            mined=true;lastDig=now;
                        }
                        pendingMinedBlock=null;pendingMinedState=null;
                    }
                    // 实际挖完方块后，停镐才从身后追加三次敲击；未挖掉方块时不能只因停手就藏镐、大笑。
                    if (p.swinging && p.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) lastDig=now;
                    long pause=now-lastDig;
                    // 间隔十 tick（半秒）；音量由 0.6 提到 1.5，让五至六格外的身后敲击更清楚。
                    if (mined&&(pause==10 || pause==20 || pause==30)) frog.playSound(SoundEvents.STONE_HIT,1.5F,1);
                    // 听声回头后仍装挖两秒，再花一秒藏镐、转向；恢复挖掘或移开视线会重新计时。
                    if(!mined||pause<24||!watching(p))mineObserved=-1;
                    else if(mineObserved<0)mineObserved=now;
                    long observed=mineObserved<0?0:now-mineObserved;
                    frog.setAct(observed<40?"mine":"mine_hide");
                    if(observed<40)faceMiningBlock();
                    else {
                        frog.setYRot(net.minecraft.util.Mth.approachDegrees(frog.getYRot(),yaw,12));frog.setYHeadRot(frog.getYRot());frog.yBodyRot=frog.getYRot();
                        Vec3 delta=p.getEyePosition().subtract(frog.getEyePosition());
                        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
                        frog.setXRot(net.minecraft.util.Mth.approachDegrees(frog.getXRot(),pitch,6));
                        if(observed>=60&&Math.abs(net.minecraft.util.Mth.wrapDegrees(frog.getYRot()-yaw))<10)success(p);
                    }
                }
                case "fish" -> {
                        if (!safe(p.serverLevel(),land,true)) return false;
                        // 四分之三秒出水弧线，只作演出，不把演员接到真实钓鱼管线。
                        double t=Math.min(1,(now-landed)/15.0);
                        // 收竿后固定真实起跳点；不能重新使用已下沉、移除的浮漂位置，否则首帧又会落入池底。
                        Vec3 point=fishPoint(takeoff,land,t);
                        if (!p.level().noCollision(frog,new net.minecraft.world.phys.AABB(point.x-.45,point.y,point.z-.45,point.x+.45,point.y+2,point.z+.45))) return false;
                        frog.setPos(point);
                        if (t>=1 && now-landed>=30 && watching(p)) {frog.setNoGravity(false);success(p);}
                }
                case "boat" -> {
                    if (boat==null || !boat.isAlive() || frog.getVehicle()!=boat) return false;
                    if (p.getVehicle()!=boat) {
                        frog.setBoatSteering(0);boatIdle=-1;boatChange=-1;
                        if (p.getVehicle()!=null || p.distanceToSqr(boat)>64) return false;
                    } else {
                        if(boat.getControllingPassenger()!=p||boat.getPassengers().size()!=2)return false;
                        frog.setYRot(boat.getYRot());frog.setYHeadRot(boat.getYRot());frog.yBodyRot=boat.getYRot();
                        // 使用玩家原始方向输入判断停手，不能按船速判断，否则惯性或接管后的移动会反复取消接管。
                        if(Math.abs(p.xxa)>.01F||Math.abs(p.zza)>.01F||!onWater(boat)) {
                            frog.setBoatSteering(0);boatIdle=-1;boatChange=-1;
                        } else {
                            if(boatIdle<0)boatIdle=now;
                            // 松开半秒后才接手；每两至四秒随机换方向，不改玩家镜头或真实座位。
                            if(now-boatIdle>=10&&now>=boatChange) {
                                int command=1+p.getRandom().nextInt(3);
                                if(command==frog.boatSteering())command=command%3+1;
                                frog.setBoatSteering(command);boatChange=now+40+p.getRandom().nextInt(40);
                            }
                        }
                    }
                    // 船上回头也能抓现行；持续看见半秒后才笑。等待和随机航行阶段绝不预播笑声。
                    if(watching(p)) {if(boatObserved<0)boatObserved=now;if(now-boatObserved>=10)success(p);}
                    else boatObserved=-1;
                }
                default -> {return false;}
            }
            return true;
        }
        void close() {
            Vec3 refund=decoy!=null&&!frog.isAddedToLevel()?decoy.position():frog.position();
            frog.setBoatSteering(0);
            frog.setScopeViewer(-1);
            frog.stopRiding();
            if (frog.isTemporary()) frog.discard();
            else {
                if(kind.equals("scope")&&safe((ServerLevel)frog.level(),initialPosition,true))frog.setPos(initialPosition);
                frog.setNoAi(initialNoAi);frog.setItemInHand(InteractionHand.MAIN_HAND,initialHand);frog.setAct(initialAct);frog.play("auto");
            }
            // 先移除奶蛙，再在消失处生成真实钻石；中止同样交还，结算由暂存实体保证不重复。
            if(decoy!=null)decoy.finishAt(refund);
        }
    }

}
