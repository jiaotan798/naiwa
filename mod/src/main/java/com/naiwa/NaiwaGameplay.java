package com.naiwa;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.stats.Stats;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** 笑气携带、图腾飞升及落地后嘲笑。演出待办不落盘，未完成的免摔保护单独保存。 */
public final class NaiwaGameplay {
    public static final TagKey<EntityType<?>> GAS_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, id("gas_immune"));
    // 只保留旧减速标记的清理入口；当前飞升不再施加移动速度修正。
    private static final ResourceLocation SLOW = id("ascension_speed");
    private static final String SAFE_FALL = "naiwa.safe_fall";
    private static final String OLD_GRAVITY = "naiwa.original_gravity";
    private static final Map<UUID, Long> LEAKS = new HashMap<>();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    // 单位 tick（20 tick/秒）；悬停六秒，攻击保护三十秒，覆盖完整演出与落地恢复。
    static final int HOVER_TICKS=120, TOTEM_PROTECTION_TICKS=600;
    private static final Map<UUID, Long> MOB_PROTECTION = new HashMap<>();
    // -1 表示仍在等真正落地；落地后记世界 tick，前方受阻最多重试十秒，避免长期占用整蛊调度。
    private static final Map<UUID, Long> LANDING_MOCKS = new HashMap<>();
    private NaiwaGameplay() {}
    private static ResourceLocation id(String value) {return ResourceLocation.fromNamespaceAndPath(Naiwa.ID, value);}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(NaiwaGameplay::tick);
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {if (e.getEntity().hasEffect(Naiwa.LAUGHING)) e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getEntity().hasEffect(Naiwa.LAUGHING) && attackingItem(e.getItemStack())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            // 既阻止新的追击，也兜住已经挥出的近战及以生物为来源的箭矢、魔法或爆炸；不改全局无敌标志。
            if(e.getEntity() instanceof ServerPlayer p&&protectedFromMobs(p)
                    &&(e.getSource().getEntity() instanceof Mob||e.getSource().getDirectEntity() instanceof Mob))e.setCanceled(true);
            if (e.getSource().getEntity() instanceof LivingEntity source && e.getSource().getDirectEntity()==source && source.hasEffect(Naiwa.LAUGHING)) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(LivingChangeTargetEvent e)->{
            if(e.getEntity() instanceof Mob&&e.getNewAboutToBeSetTarget() instanceof ServerPlayer p&&protectedFromMobs(p))
                // 取消事件会保留旧目标；改为 null 才能覆盖旧目标也恰好是受保护玩家的情况。
                e.setNewAboutToBeSetTarget(null);
        });
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> {
            // 只在实际扣血后解除怪物控制；护甲完全吸收或被取消的攻击不解除。
            if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player) && e.getNewDamage() > 0) e.getEntity().removeEffect(Naiwa.LAUGHING);
        });
        NeoForge.EVENT_BUS.addListener((LivingFallEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && (FLIGHTS.containsKey(p.getUUID()) || p.getPersistentData().getBoolean(SAFE_FALL))) {
                e.setCanceled(true);
                if (!FLIGHTS.containsKey(p.getUUID())) p.getPersistentData().remove(SAFE_FALL);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> cleanup((ServerPlayer)e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> cleanup((ServerPlayer)e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> cleanup((ServerPlayer)e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            if (!e.isWasDeath() && e.getOriginal().getPersistentData().getBoolean(SAFE_FALL)) e.getEntity().getPersistentData().putBoolean(SAFE_FALL,true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            for (ServerPlayer p:e.getServer().getPlayerList().getPlayers()) cleanup(p);
            FLIGHTS.clear(); LEAKS.clear(); LANDING_MOCKS.clear(); MOB_PROTECTION.clear();
        });
    }

    /** 可移动的大笑不改玩家移动能力；怪物 AI 由 MobMixin 暂停，效果由原版正常计时并同步。 */
    public static void laugh(LivingEntity entity, int ticks) {
        if (entity.hasEffect(Naiwa.ASCENDING) || entity.hasEffect(Naiwa.LAUGHING)) return;
        entity.addEffect(new MobEffectInstance(Naiwa.LAUGHING, ticks, 0, false, true, true));
        if (attackingItem(entity.getUseItem())) entity.stopUsingItem();
        entity.level().playSound(null, entity, Naiwa.SHIELD_LAUGH.get(), net.minecraft.sounds.SoundSource.NEUTRAL,.7F,1);
    }

    /** 允许举盾、进食与普通交互；暂停武器蓄力及攻击性投掷，已离手投掷物照常结算。 */
    private static boolean attackingItem(ItemStack stack) {
        var item=stack.getItem();
        return item instanceof net.minecraft.world.item.ProjectileWeaponItem || item instanceof net.minecraft.world.item.ProjectileItem
                || item instanceof net.minecraft.world.item.TridentItem;
    }

    /** 使用真实碰撞视线，玻璃同样遮挡；镜像光学例外由整蛊模块单独处理。 */
    public static boolean clearLine(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()==HitResult.Type.MISS;
    }

    /** 仅主背包、快捷栏和副手计入携带；不遍历容器，瓶数不改变漏气频率。 */
    static boolean carrying(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) if (stack.is(Naiwa.GAS.get())) return true;
        return player.getOffhandItem().is(Naiwa.GAS.get());
    }

    /** 飞升恢复后继续等待实体地面；模型超时结束、入水或载具接触均不能提前播放落地嘲笑。 */
    private static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) {cleanup(p); continue;}
            Long protection=MOB_PROTECTION.get(p.getUUID());
            if(protection!=null&&p.level().getGameTime()>=protection)MOB_PROTECTION.remove(p.getUUID());
            // 目标变更事件拦截新仇恨；每秒补查一次，清除已有的普通目标及 Brain 攻击记忆。
            if(protectedFromMobs(p)&&p.level().getGameTime()%20==0)clearMobTargets(p);
            Flight flight = FLIGHTS.get(p.getUUID());
            if (flight != null) {
                LEAKS.remove(p.getUUID());
                if (!p.hasEffect(Naiwa.ASCENDING) || !flight.tick(p)) finish(p,flight);
                else continue;
            }
            // 等待正前方净空不能延长免摔资格；首次落地或入水仍按原规则独立消耗。
            if (p.onGround() || p.isInWater()) p.getPersistentData().remove(SAFE_FALL);
            if (waitingForTotemLanding(p)) {
                long now=p.level().getGameTime(),landed=LANDING_MOCKS.get(p.getUUID());
                if (onSolidGround(p)) {
                    if(landed<0) {landed=now;LANDING_MOCKS.put(p.getUUID(),now);}
                    // 只消费成功的一次出场；受阻时使用下一 tick 的实际位置和朝向重新找正前方。
                    if(NaiwaPranks.mockAfterTotemLanding(p))LANDING_MOCKS.remove(p.getUUID());
                }
                if(landed>=0&&now-landed>=200)LANDING_MOCKS.remove(p.getUUID());
                LEAKS.remove(p.getUUID());
                if(waitingForTotemLanding(p))continue;
            }
            if (!carrying(p)) {LEAKS.remove(p.getUUID()); continue;}
            long now=p.level().getGameTime();
            long next=LEAKS.computeIfAbsent(p.getUUID(), key -> now+1200+p.getRandom().nextInt(2401));
            if (now>=next) {
                laugh(p,50);
                LEAKS.put(p.getUUID(),now+1200+p.getRandom().nextInt(2401));
            }
        }
    }

    /** 落地待办期间给图腾出场留位，不能被另一种整蛊抢占；超时或中断会释放。 */
    static boolean waitingForTotemLanding(ServerPlayer p) {return LANDING_MOCKS.containsKey(p.getUUID());}

    /** 不把水面、船体或客户端单独上报的 onGround 当作真实实体地面。 */
    static boolean onSolidGround(ServerPlayer p) {
        return p.onGround()&&!p.isPassenger()&&!p.isInWater()
                &&p.level().getBlockCollisions(p,p.getBoundingBox().move(0,-.1,0)).iterator().hasNext();
    }

    /** 飞升阶段始终保护，正常结束后保留触发起三十秒的余量；退出、死亡和换维度主动清理。 */
    static boolean protectedFromMobs(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&(FLIGHTS.containsKey(p.getUUID())||p.hasEffect(Naiwa.ASCENDING)
                ||p.level().getGameTime()<MOB_PROTECTION.getOrDefault(p.getUUID(),Long.MIN_VALUE));
    }

    /** 仅清除指向本玩家的攻击，不暂停其他生物 AI；六十四格覆盖附近已在追击的普通及远程生物。 */
    private static void clearMobTargets(ServerPlayer p) {
        for(Mob mob:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(64),Mob::isAlive)) {
            boolean target=mob.getTarget()==p,remembered=mob.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET,p);
            if(target)mob.setTarget(null);
            if(remembered)mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if(target||remembered) {mob.getNavigation().stop();mob.setAggressive(false);}
        }
    }

    /** 在原版图腾检查的入口调用，按原版主副手顺序、伤害豁免及取消事件处理。 */
    public static boolean useTotem(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer p) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack=p.getItemInHand(hand);
            if (stack.is(Items.TOTEM_OF_UNDYING)) return false;
            if (!stack.is(Naiwa.TOTEM.get()) || !CommonHooks.onLivingUseTotem(p,source,stack,hand)) continue;
            ItemStack used=stack.copy(); stack.shrink(1);
            p.awardStat(Stats.ITEM_USED.get(Naiwa.TOTEM.get())); CriteriaTriggers.USED_TOTEM.trigger(p,used);
            p.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            p.setHealth(1); p.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
            // 生命恢复和防火各六十秒，伤害吸收三十秒，不再先于完整飞升演出结束。
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,1200,1));
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,600,1));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,1200,0));
            p.level().broadcastEntityEvent(p,(byte)35);
            startFlight(p); return true;
        }
        return false;
    }

    /** 保命已成功后才启动一次变身并排队落地演出；替换旧状态前先恢复旧重力、清除旧待办。 */
    static void startFlight(ServerPlayer p) {
        cleanup(p); NaiwaPranks.cancel(p);
        p.removeEffect(Naiwa.LAUGHING);
        Flight flight=new Flight(p); // 离座可能改变脚部高度，六格目标必须先按触发位置记录。
        p.stopRiding();
        p.getPersistentData().putBoolean(SAFE_FALL,true);
        FLIGHTS.put(p.getUUID(),flight);
        MOB_PROTECTION.put(p.getUUID(),p.level().getGameTime()+TOTEM_PROTECTION_TICKS);
        LANDING_MOCKS.put(p.getUUID(),-1L);
        p.getPersistentData().putBoolean(OLD_GRAVITY,flight.gravity);
        p.setNoGravity(true); p.setDeltaMovement(Vec3.ZERO); p.fallDistance=0;
        phase(p,0,10);
        // 图腾只击退一次，不附加伤害；复用盾牌的同盟、宠物和 PvP 过滤。
        NaiwaShield.pushAround(p);
        clearMobTargets(p);
    }

    private static void phase(ServerPlayer p,int stage,int ticks) {
        // 阶段等级递增，直接更新可避免移除/重加包之间短暂无效果，造成视角和图腾屏幕动画重复切换。
        p.addEffect(new MobEffectInstance(Naiwa.ASCENDING,ticks+5,stage,false,false,false));
    }

    /** 飞升计时结束只恢复玩家，仍保留落地待办；半空结束不等于已经落地。 */
    private static void finish(ServerPlayer p,Flight flight) {
        FLIGHTS.remove(p.getUUID()); p.setNoGravity(flight.gravity); p.removeEffect(Naiwa.ASCENDING);
        p.getPersistentData().remove(OLD_GRAVITY);
        p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW); p.fallDistance=0;
        if (p.onGround() || p.isInWater()) p.getPersistentData().remove(SAFE_FALL);
    }

    /** 退出、换维度、死亡或主动中断取消落地演出；尚未落地的一次免摔资格仍按原规则保留。 */
    public static void cleanup(ServerPlayer p) {
        MOB_PROTECTION.remove(p.getUUID());
        LANDING_MOCKS.remove(p.getUUID());
        Flight old=FLIGHTS.get(p.getUUID());
        if (old!=null) finish(p,old);
        // 崩溃后 Map 已丢失，但原版会保存 NoGravity；用单独标记恢复进入效果之前的值。
        if (p.getPersistentData().contains(OLD_GRAVITY)) {
            p.setNoGravity(p.getPersistentData().getBoolean(OLD_GRAVITY)); p.getPersistentData().remove(OLD_GRAVITY);
        }
        p.removeEffect(Naiwa.LAUGHING); p.removeEffect(Naiwa.ASCENDING);
        p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW); LEAKS.remove(p.getUUID());
        if (!p.isAlive()) p.getPersistentData().remove(SAFE_FALL);
    }

    /** 阶段单位为 tick；只移动碰撞允许的距离，不穿顶，不在障碍移除后补升。 */
    static final class Flight {
        final double target;
        final boolean gravity;
        int stage, ticks;
        // 到顶或碰顶时锁定悬停高度；操作或外力不能在悬停阶段继续累计抬升。
        double hoverY;
        Flight(ServerPlayer p) {target=p.getY()+6; gravity=p.isNoGravity();}
        boolean tick(ServerPlayer p) {
            p.fallDistance=0;
            if (p.isInWater()) return false;
            if (stage==0) {
                if (++ticks>=10) {stage=1; ticks=0; phase(p,1,40);}
                return true;
            }
            // 二次缓出在两秒内上升六格；按目标高度差推进，不能把浮动误积成额外高度。
            double remaining=1-(ticks+1)/40.0;
            double dy=stage==1 ? target-6*remaining*remaining-p.getY() : stage==3 ? -.1 : hoverY-p.getY();
            double before=p.getY();
            if (dy!=0) {
                p.move(MoverType.SELF,new Vec3(0,dy,0));
                // 仅发送裁定高度，不触发原版传送确认；水平位置仍由玩家正常移动包更新。
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new NaiwaFlightHeight(p.getY()));
            }
            p.setDeltaMovement(new Vec3(p.getDeltaMovement().x,0,p.getDeltaMovement().z));
            if (stage==1 && (p.getY()>=target-.001 || p.getY()-before<dy-.001 || ++ticks>=40)) {
                stage=2; ticks=0; hoverY=p.getY(); phase(p,2,HOVER_TICKS);
            } else if (stage==2 && ++ticks>=HOVER_TICKS) {
                stage=3; ticks=0; phase(p,3,60); p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW);
            } else if (stage==3 && (p.onGround() || ++ticks>=60)) return false;
            return true;
        }
    }
}
