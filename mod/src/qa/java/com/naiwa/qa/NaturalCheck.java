package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.*;
import net.minecraft.tags.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 正常世界的实机验收：分 tick 搜索原生树、岸线和洞壁，只为门/镜面放置必需的建造物。 */
final class NaturalCheck {
    private final List<NaturalSite> caves=new ArrayList<>(),shores=new ArrayList<>(),cases=new ArrayList<>();
    private NaturalSite flat,tree,site;
    private int ticks,started,phase,index,scan,fishEvents,fishLoot,rodBefore;
    private boolean done,crouch,jump,giggle,serious,refused,blocked;
    private NaiwaEntity frog;
    private Boat boat;
    private Vec3 boatStart;
    private Vec3 initialLand;
    private BlockState original;
    private Item logItem;
    private Object scene;
    private net.minecraft.world.entity.projectile.FishingHook caughtHook;
    private long fishCaughtAt;
    private final Map<BlockPos,BlockState> built=new HashMap<>();
    private final Map<BlockPos,BlockState> terrain=new HashMap<>();
    private final Map<Item,Integer> loot=new HashMap<>(),before=new HashMap<>();
    NaturalCheck() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{
            if(site!=null&&site.kind.equals("fish")&&e.getEntity() instanceof ServerPlayer p&&p.getGameProfile().getName().equals("NaiwaActor")) {
                rodBefore=p.getInventory().items.get(3).getDamageValue();caughtHook=e.getHookEntity();fishCaughtAt=p.level().getGameTime();
                fishEvents++;fishLoot+=e.getDrops().stream().mapToInt(ItemStack::getCount).sum();
                for(ItemStack stack:e.getDrops()) {before.putIfAbsent(stack.getItem(),total(p,stack.getItem()));loot.merge(stack.getItem(),stack.getCount(),Integer::sum);}
            }
        });
    }
    private void require(boolean ok,String why) {MultiplayerCheck.require(ok,"natural case="+index+" phase="+phase+": "+why);}
    private Object field(String name) throws Exception {var f=scene.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(scene);}
    private int total(ServerPlayer p,Item item) {
        return p.serverLevel().players().stream().mapToInt(a->a.getInventory().countItem(item)).sum()+p.level().getEntitiesOfClass(ItemEntity.class,site.area()).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    private boolean safe(ServerLevel level,Vec3 p) {
        BlockPos b=BlockPos.containing(p);
        return level.hasChunkAt(b)&&level.getBlockState(b.below()).isFaceSturdy(level,b.below(),Direction.UP)
            &&level.noCollision(new AABB(p.x-.45,p.y,p.z-.45,p.x+.45,p.y+2,p.z+.45))
            &&!level.containsAnyLiquid(new AABB(p.x-.45,p.y,p.z-.45,p.x+.45,p.y+2,p.z+.45));
    }
    private NaturalSite describe(ServerLevel level,NaturalSite s) {
        s.ground=BuiltInRegistries.BLOCK.getKey(level.getBlockState(s.anchor().below()).getBlock()).toString();
        s.biome=level.getBiome(s.anchor()).unwrapKey().map(k->k.location().toString()).orElse("unknown");
        int h=0;while(h<12&&level.getBlockState(s.anchor().above(h)).getCollisionShape(level,s.anchor().above(h)).isEmpty())h++;s.clearance=h;
        return s;
    }
    private boolean distinct(List<NaturalSite> list,NaturalSite s) {return list.stream().noneMatch(a->a.anchor().distSqr(s.anchor())<1024);}
    /** 每 tick 只生成一个搜索区块，避免一次遍历整片世界触发看门狗；测试搜索可以加载区块，正式玩法不可这样做。 */
    private boolean search(ServerLevel level) throws Exception {
        if(flat!=null&&tree!=null&&caves.size()>=3&&shores.size()>=3) return true;
        require(scan<2304,"搜索范围内没有足够原生场地；不允许用平地/水池替代");
        // 逐圈展开，优先使用出生点附近已生成的世界，再扩到 768 格。
        int n=scan++,r=(int)Math.ceil((Math.sqrt(n+1)-1)/2),side=Math.max(1,2*r),q=n-(2*r-1)*(2*r-1);
        int cx,cz;if(r==0){cx=cz=0;}else if(q<side){cx=r;cz=-r+q;}else if(q<2*side){cx=r-(q-side);cz=r;}else if(q<3*side){cx=-r;cz=r-(q-2*side);}else{cx=-r+(q-3*side);cz=-r;}
        level.getChunk(cx,cz);
        for(int lx=3;lx<=12;lx++) for(int lz=3;lz<=12;lz++) {
            int x=cx*16+lx,z=cz*16+lz,y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos p=new BlockPos(x,y,z);
            if(flat==null&&safe(level,Vec3.atBottomCenterOf(p))) {
                NaturalSite s=new NaturalSite("mirror","surface-buildings",p,0);boolean fit=true;
                for(int dx=-2;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) if(!safe(level,s.point(dx+.5,0,dz+.5)))fit=false;
                if(fit) {flat=describe(level,s);System.out.println("NAIWA_MULTIPLAYER natural flat "+p);}
            }
            // 高度图可能位于树顶；从地面向上找最低一截真正生成的原木。
            if(tree==null) for(int ty=y-12;ty<=y;ty++) {
                BlockPos trunk=new BlockPos(x,ty,z);
                if(!level.getBlockState(trunk).is(BlockTags.LOGS)||level.getBlockState(trunk.below()).is(BlockTags.LOGS))continue;
                for(int rot=0;rot<4;rot++) {
                    NaturalSite s=new NaturalSite("tree","native-tree",trunk,rot);
                    if(safe(level,s.point(.5,0,3))&&safe(level,s.point(.5,0,-.8))&&safe(level,s.point(3,0,-1))) {tree=describe(level,s);System.out.println("NAIWA_MULTIPLAYER natural tree "+trunk);break;}
                }
            }
            if(shores.size()<3&&level.getFluidState(p.below()).is(FluidTags.WATER)) for(int rot=0;rot<4;rot++) {
                NaturalSite s=new NaturalSite("fish","shore-"+shores.size(),p,rot);
                if(!distinct(shores,s))continue;
                Vec3 bankPoint=s.point(.5,0,5);s.bankStep=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(bankPoint.x),(int)Math.floor(bankPoint.z))-y;
                boolean fit=s.bankStep>=0&&s.bankStep<=1&&(!shores.isEmpty()||s.bankStep==0)&&safe(level,s.point(.5,s.bankStep,6))&&safe(level,s.point(.5,s.bankStep,5))&&safe(level,s.point(3,s.bankStep,5));
                // 正向验收场地必须有完整的开阔深水带；仅检查水块会把凸岸下的水或一格宽露天裂隙误选成可钓鱼/放船的河面。
                for(int dx=-1;dx<=1&&fit;dx++)for(int dz=0;dz<=3&&fit;dz++) {
                    if(!level.getFluidState(s.block(dx,-1,dz)).is(FluidTags.WATER)||!level.getFluidState(s.block(dx,-2,dz)).is(FluidTags.WATER))fit=false;
                    Vec3 air=s.point(dx+.5,0,dz+.5);if(!level.noCollision(new AABB(air.x-.49,air.y,air.z-.49,air.x+.49,air.y+3,air.z+.49)))fit=false;
                }
                for(int dx=-1;dx<=1&&fit;dx++)for(int dz=4;dz<=6&&fit;dz++) {
                    Vec3 air=s.point(dx+.5,s.bankStep,dz+.5);if(!level.noCollision(new AABB(air.x-.49,air.y,air.z-.49,air.x+.49,air.y+3,air.z+.49)))fit=false;
                }
                // 允许原生一格台阶和不规则边缘，出水路线必须确实有可走的岸，不补水或铺路。
                if(!safe(level,s.point(.5,s.bankStep,4)))fit=false;
                if(fit) {
                    BlockPos bank=BlockPos.containing(s.point(.5,s.bankStep,5));s.ground=BuiltInRegistries.BLOCK.getKey(level.getBlockState(bank.below()).getBlock()).toString();
                    int depth=0;while(depth<32&&level.getFluidState(p.below(depth+1)).is(FluidTags.WATER))depth++;s.depth=depth;
                    int variation=0;for(int dx=-3;dx<=3;dx++) {Vec3 b=s.point(dx+.5,0,4.5);variation=Math.max(variation,Math.abs(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(b.x),(int)Math.floor(b.z))-y));}
                    s.label=s.ground+"-open-step"+s.bankStep+"-depth"+depth+"-edge"+variation;
                    if(shores.stream().noneMatch(a->a.label.equals(s.label))) {String ground=s.ground;describe(level,s);s.ground=ground;shores.add(s);System.out.println("NAIWA_MULTIPLAYER natural shore "+s.label+" "+p);}
                }
            }
            if(caves.size()<3&&lx%3==0&&lz%3==0) for(int cy=-48;cy<Math.min(y-5,60);cy++) {
                BlockPos wall=new BlockPos(x,cy,z);BlockState state=level.getBlockState(wall);
                if(!state.is(BlockTags.BASE_STONE_OVERWORLD)&&!state.is(BlockTags.IRON_ORES)&&!state.is(BlockTags.COAL_ORES))continue;
                for(int rot=0;rot<4;rot++) {
                    NaturalSite s=new NaturalSite("mine","cave",wall,rot);
                    if(!distinct(caves,s)||level.canSeeSky(BlockPos.containing(s.point(.5,0,3))))continue;
                    if(!safe(level,s.point(.5,0,3))||!safe(level,s.point(-.7,0,1.5))||!safe(level,s.point(3,0,2)))continue;
                    BlockPos feet=BlockPos.containing(s.point(.5,0,3));int h=0;while(h<16&&level.getBlockState(feet.above(h)).getCollisionShape(level,feet.above(h)).isEmpty())h++;
                    s.label=(cy<0?"deepslate":"stone")+(h<=3?"-low":"-open");s.clearance=h;
                    if(caves.stream().noneMatch(a->a.label.equals(s.label))) {describe(level,s);s.clearance=h;caves.add(s);System.out.println("NAIWA_MULTIPLAYER natural cave "+s.label+" "+wall);}
                }
            }
        }
        if(scan%20==0) {MultiplayerCheck.write("search.json",new Gson().toJson(Map.of("chunks",scan,"caves",caves,"shores",shores)));System.out.println("NAIWA_MULTIPLAYER natural searched="+scan+" caves="+caves.size()+" shores="+shores.size());}
        return false;
    }
    private void stage(int value) throws Exception {phase=value;started=ticks;MultiplayerCheck.write("stage.txt",""+phase);System.out.println("NAIWA_MULTIPLAYER natural case="+index+" "+site.kind+" "+site.label+" stage="+phase);}
    private void teleport(ServerPlayer p,Vec3 spot,float yaw) {p.stopRiding();p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(spot.x,spot.y,spot.z,yaw,0);}
    private void put(ServerLevel level,BlockPos p,BlockState state) {built.put(p,level.getBlockState(p));level.setBlock(p,state,3);}
    /** 只恢复上轮人为放置的门和玻璃；原生洞壁、河床和岸线从未改写，原木由客户端正常采集。 */
    private void fixture(ServerPlayer a,ServerPlayer v) throws Exception {
        NaiwaPranks.cancel(a);NaiwaPranks.cancel(v);var level=a.serverLevel();
        for(var e:built.entrySet())level.setBlock(e.getKey(),e.getValue(),3);built.clear();
        if(boat!=null)boat.discard();boat=null;if(a.fishing!=null)a.fishing.discard();
        site=cases.get(index);frog=null;scene=null;caughtHook=null;fishEvents=fishLoot=0;loot.clear();before.clear();crouch=jump=giggle=serious=refused=blocked=false;
        for(int cx=(site.x-20)>>4;cx<=(site.x+20)>>4;cx++)for(int cz=(site.z-20)>>4;cz<=(site.z+20)>>4;cz++)level.getChunk(cx,cz);
        for(var p:List.of(a,v)) {p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.getInventory().clearContent();p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);}
        a.getInventory().items.set(0,new ItemStack(Items.IRON_AXE));a.getInventory().items.set(1,new ItemStack(Items.SHIELD));a.getInventory().items.set(2,new ItemStack(Items.IRON_PICKAXE));a.getInventory().items.set(3,new ItemStack(Items.FISHING_ROD));v.getInventory().items.set(0,new ItemStack(Items.IRON_AXE));
        original=level.getBlockState(site.anchor());logItem=original.getBlock().asItem();rodBefore=0;
        teleport(a,site.point(.5,0,3),site.yaw(180));teleport(v,site.point(3,0,site.kind.equals("mine")?2:-1),site.yaw(90));
        if(site.kind.equals("mirror")) {for(int dx=-2;dx<=2;dx++)for(int dy=0;dy<4;dy++) {BlockPos b=site.block(dx,dy,0);require(level.getBlockState(b).getCollisionShape(level,b).isEmpty(),"建造玻璃前不删除原生障碍");put(level,b,Blocks.GLASS.defaultBlockState());}teleport(v,site.point(3,0,-3),site.yaw(55));}
        if(site.kind.equals("door")) {var lower=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,site.direction()).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER);put(level,site.anchor(),lower);put(level,site.anchor().above(),lower.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));}
        if(site.kind.equals("fish")||site.kind.equals("boat")) {
            teleport(a,site.point(.5,site.bankStep,6),site.yaw(180));teleport(v,site.point(3,site.bankStep,5),site.yaw(130));
            if(site.kind.equals("boat")) {
                boat=EntityType.BOAT.create(level);boat.setYRot(site.yaw(0));boolean placed=false;
                // 凸岸下面也可能有水；放船须检查整船体积，不能把“脚下是水”误当作可放船。
                for(double dz:new double[]{1.5,2.5,.5,-.5,-1.5,-2.5})for(double dx:new double[]{.5,1.5,-.5,2.5,-1.5})if(!placed) {
                    boat.setPos(site.point(dx,-.35,dz));
                    placed=level.getFluidState(BlockPos.containing(boat.position().add(0,-.2,0))).is(FluidTags.WATER)&&level.noCollision(boat,boat.getBoundingBox());
                }
                require(placed,"原生水面可容纳木船");level.addFreshEntity(boat);boatStart=boat.position();teleport(a,site.point(.5,site.bankStep,4),site.yaw(180));
            }
        }
        MultiplayerCheck.write("actor-entity.txt",index+" -1");
        MultiplayerCheck.write("site.json",new Gson().toJson(site));stage(1006+index*10);
    }
    private void tick(ServerTickEvent.Post event) {
        if(done)return;ticks++;
        try {
            var server=event.getServer();var level=server.overworld();var a=server.getPlayerList().getPlayerByName("NaiwaActor");var v=server.getPlayerList().getPlayerByName("NaiwaViewer");
            if(phase==0) {
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(1000);
                // 固定随机方块更新，地形前后比对才不会把原版草蔓延/树叶衰减误归因到整蛊。
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
                if(a!=null)a.setGameMode(GameType.SPECTATOR);if(v!=null)v.setGameMode(GameType.SPECTATOR);
                if(cases.isEmpty()&&System.getProperty("naiwa.qa.naturalSites")!=null) {
                    var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("naiwa.qa.naturalSites")))).getAsJsonObject();
                    require(json.get("seed").getAsLong()==8675309,"复测场地必须来自相同种子");
                    for(var value:json.getAsJsonArray("cases"))cases.add(new Gson().fromJson(value,NaturalSite.class));
                    require(cases.size()==12,"完整复测包含十二个自然场景");
                }
                if(cases.isEmpty()&&!search(level))return;
                if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
                if(cases.isEmpty()) {
                    cases.add(flat);var door=new Gson().fromJson(new Gson().toJson(flat),NaturalSite.class);door.kind="door";cases.add(door);cases.add(tree);cases.addAll(caves);
                    for(var shore:shores) {cases.add(shore);var boatSite=new Gson().fromJson(new Gson().toJson(shore),NaturalSite.class);boatSite.kind="boat";cases.add(boatSite);}
                }
                if(System.getProperty("naiwa.qa.focus")!=null)cases.removeIf(s->!s.kind.equals(System.getProperty("naiwa.qa.focus")));
                String label=System.getProperty("naiwa.qa.focusSite");
                if(label!=null)cases.removeIf(site->!site.label.equals(label));
                require(!cases.isEmpty(),"自然场景过滤后没有可测试的场地");
                MultiplayerCheck.write("natural-sites.json",new Gson().toJson(Map.of("seed",8675309,"scannedChunks",scan,"cases",cases)));fixture(a,v);return;
            }
            require(a!=null&&v!=null,"测试中双方保持连接");require(ticks-started<2500,"阶段超时");
            int sub=(phase-1000)%10;
            if(sub==6) {
                // 新区块的沙砾、流水和树叶会执行原版计划更新；等它们先落定，再记录“整蛊前”基线，不能把生成后的正常物理算成演员改地形。
                if(ticks-started<100)return;
                terrain.clear();for(BlockPos b:BlockPos.betweenClosed(site.anchor().offset(-8,-6,-8),site.anchor().offset(8,6,8)))terrain.put(b.immutable(),level.getBlockState(b));
                if(boat!=null)boatStart=boat.position();
                a.getPersistentData().putLong("naiwa.prank_next",0);a.getPersistentData().putString("naiwa.prank_last","");stage(phase-6);return;
            }
            if(frog!=null&&frog.isAlive()) {crouch|=frog.isShiftKeyDown();jump|=frog.getY()>site.y+.3;giggle|=frog.act().equals("door_giggle");serious|=giggle&&frog.act().equals("door");
                require(level.noCollision(frog,frog.getBoundingBox()),"演员身体不得进入自然地形实心方块 pos="+frog.position());
            }
            if((ticks-started)%100==0)MultiplayerCheck.write("natural-diagnostics.txt","case="+index+" phase="+phase+" site="+new Gson().toJson(site)+" player="+a.position()+" yaw="+a.getYRot()+" busy="+NaiwaPranks.busy(a)+" frog="+(frog==null?"none":frog.position()+" alive="+frog.isAlive()+" act="+frog.act())+"\n");
            if(site.kind.equals("fish")&&sub==0&&(ticks-started)%20==0) {
                String columns="";var hook=a.fishing;
                if(hook!=null)for(double angle:new double[]{0,.6,-.6,1.2,-1.2,Math.PI}) {
                    BlockPos column=BlockPos.containing(hook.position().add(Vec3.directionFromRotation(0,a.getYRot()).yRot((float)angle).scale(2.5)));
                    for(int dy=-2;dy<=4;dy++)if(safe(level,Vec3.atBottomCenterOf(column.above(dy))))columns+=column.above(dy)+" dy="+dy+";";
                }
                MultiplayerCheck.write("fish-start-"+index+".txt","player="+a.position()+" hand="+a.getMainHandItem()+" hook="+(hook==null?null:hook.position()+" water="+hook.isInWater()+" onGround="+hook.onGround())+" spot="+(hook==null?null:NaiwaPranks.findSpot(a,hook.position(),false))+" safeColumns="+columns+"\n");
            }
            if(site.kind.equals("fish")&&scene!=null&&(ticks-started)%10==0) {
                Vec3 land=(Vec3)field("land"),takeoff=(Vec3)field("takeoff");String blocked="";
                if(land!=null&&frog!=null)for(int i=0;i<=30;i++){double t=i/30.0;Vec3 p=(takeoff==null?frog.position():takeoff).lerp(land,t).add(0,Math.sin(t*Math.PI)*1.2,0);if(!level.noCollision(frog,new AABB(p.x-.45,p.y,p.z-.45,p.x+.45,p.y+2,p.z+.45)))blocked+=i+":"+p+";";}
                MultiplayerCheck.write("fish-"+index+"-diagnostics.txt","frog="+frog.position()+" hook="+(a.fishing==null?null:a.fishing.position())+" land="+land+" takeoff="+takeoff+" events="+fishEvents+" line="+a.hasLineOfSight(frog)+" blocked="+blocked+"\n");
            }
            // 等待期客户端尚未确认本阶段；断言必须放在屏障之前，才能覆盖整个自然咬钩等待过程。
            if(sub==0&&site.kind.equals("fish")&&fishEvents==0){
                require(!NaiwaPranks.busy(a)&&level.getEntitiesOfClass(NaiwaEntity.class,site.area(),NaiwaEntity::isTemporary).isEmpty(),"原版等待与咬钩动画期间没有演员");
                require(a.getPersistentData().getLong("naiwa.prank_next")==0,"未确认渔获时不消耗间隔");
            }
            if(!Files.exists(MultiplayerCheck.ROOT.resolve("actor-"+phase+".ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-"+phase+".ok"))||ticks-started<12)return;
            if(sub==0) {
                if(!NaiwaPranks.busy(a)) {
                    // 现在只有真实收竿后才能证明出场被拒绝；先等 tick 末的待处理通知消费完，避免把尚未开始误记为拒绝。
                    if(site.kind.equals("fish")&&fishEvents==1&&caughtHook!=null&&a.fishing==null&&level.getGameTime()-fishCaughtAt>=2){
                        BlockPos surface=caughtHook.blockPosition();for(int i=0;i<3&&level.getFluidState(surface.above()).is(FluidTags.WATER);i++)surface=surface.above();
                        boolean deep=level.getFluidState(surface).is(FluidTags.WATER)&&level.getFluidState(surface.below()).is(FluidTags.WATER);
                        var candidate=Naiwa.ENTITY.get().create(level);candidate.setPos(caughtHook.getX(),surface.getY()-1+.02,caughtHook.getZ());
                        boolean body=level.noCollision(candidate,candidate.getBoundingBox());
                        var landing=NaiwaPranks.class.getDeclaredMethod("fishLanding",ServerPlayer.class,NaiwaEntity.class,Vec3.class,Vec3.class);landing.setAccessible(true);
                        Vec3 point=(Vec3)landing.invoke(null,a,candidate,caughtHook.position(),null);
                        require(!deep||!body||point==null,"有水深、完整身体空间及可达岸点时不能误拒绝出场");
                        require(a.getPersistentData().getLong("naiwa.prank_next")==0&&level.getEntitiesOfClass(NaiwaEntity.class,site.area(),NaiwaEntity::isTemporary).isEmpty(),"安全拒绝不闪现演员、不消耗间隔");
                        require(fishLoot>0&&a.getInventory().items.get(3).getDamageValue()-rodBefore==1,"拒绝附加演出时仍正常结算一次渔获和耐久");
                        for(var e:loot.entrySet())require(total(a,e.getKey())-before.get(e.getKey())==e.getValue(),"拒绝出场不吞渔获或重复掉落");
                        refused=true;blocked=deep&&body;
                        MultiplayerCheck.write("case-"+index+(blocked?"-blocked.txt":"-refusal.txt"),"PASS post-catch refusal; deep="+deep+" body="+body+" landing="+point+" loot="+loot+" cooldown=0\n");stage(phase+4);
                    }
                    return;
                }
                frog=level.getEntitiesOfClass(NaiwaEntity.class,site.area(),NaiwaEntity::isTemporary).getFirst();
                MultiplayerCheck.write("actor-entity.txt",index+" "+frog.getId());
                var f=NaiwaPranks.class.getDeclaredField("SCENES");f.setAccessible(true);scene=((Map<?,?>)f.get(null)).get(a.getUUID());
                require(field("kind").equals(site.kind),"由真实操作触发指定场景");
                initialLand=(Vec3)field("land");MultiplayerCheck.write("case-"+index+"-initial-land.txt",""+initialLand+"\n");
                if(site.kind.equals("fish"))require(fishEvents==1&&a.fishing==null,"只在真实有渔获收竿后接收演员编号");
                require(a.getPersistentData().getLong("naiwa.prank_next")-level.getGameTime()>5900,"保持真实共享冷却");stage(phase+1);
            } else if(sub==1) {
                require(frog.isAlive()&&(site.kind.equals("fish")||!frog.animation().equals("laugh_sequence")),"双方看见实际演出");
                if(site.kind.equals("boat"))require(boat.getPassengers().contains(a)&&boat.getPassengers().contains(frog)&&boat.position().distanceToSqr(boatStart)>.01,"实际划船并保留真实后座");stage(phase+1);
            } else if(sub==2) {
                if(!frog.isAlive()&&site.kind.equals("fish")&&field("land")==null&&field("takeoff")!=null&&fishEvents==1) {
                    // 方案允许所有出水路径受阻时中止；必须重新验证完整候选均不可达，不能把任意提前消失算通过。
                    var search=NaiwaPranks.class.getDeclaredMethod("findSpot",ServerPlayer.class,Vec3.class,boolean.class,NaiwaEntity.class);search.setAccessible(true);
                    var hook=(net.minecraft.world.entity.projectile.FishingHook)field("hook");
                    var path=NaiwaPranks.class.getDeclaredMethod("fishPathClear",ServerLevel.class,NaiwaEntity.class,Vec3.class,Vec3.class);path.setAccessible(true);
                    require(initialLand==null||!safe(level,initialLand)||!(Boolean)path.invoke(null,level,frog,(Vec3)field("takeoff"),initialLand),"漂移前的原岸点也确实不可达，不能只检查新搜索圈");
                    require(search.invoke(null,a,hook.position(),false,frog)==null,"中止时确实没有完整弧线可达的岸点");
                    require(!NaiwaPranks.busy(a)&&fishLoot>0&&a.getInventory().items.get(3).getDamageValue()-rodBefore==1,"受阻中止仍只结算一次正常渔获与耐久");
                    for(var e:loot.entrySet())require(total(a,e.getKey())-before.get(e.getKey())==e.getValue(),"受阻中止不吞渔获或重复掉落");
                    blocked=true;MultiplayerCheck.write("case-"+index+"-blocked.txt","PASS all candidate arcs blocked; safe cancellation; actual catch="+loot+" takeoff="+field("takeoff")+"\n");stage(phase+2);return;
                }
                require(frog.isAlive(),"露馅前演员提前消失；岸点="+field("land")+" takeoff="+field("takeoff"));
                if(!frog.animation().equals("laugh_sequence"))return;
                if(site.kind.equals("mirror"))require(crouch&&jump,"镜面真实蹲下与跳跃同步");
                if(site.kind.equals("door"))require(giggle&&serious,"关门憋笑与第一次重开严肃");
                if(site.kind.equals("tree"))require(total(a,logItem)==1,"天然原木只掉落一份");
                if(site.kind.equals("mine"))require(level.getBlockState(site.anchor()).isAir()&&a.getInventory().items.get(2).getDamageValue()==1,"玩家实际挖完天然洞壁一格，演员不额外破坏方块");
                if(site.kind.equals("fish")) {
                    require(fishEvents==1&&fishLoot>0&&a.getInventory().items.get(3).getDamageValue()-rodBefore==1,"自然咬钩、鱼竿只扣一次耐久");
                    for(var e:loot.entrySet())require(total(a,e.getKey())==before.get(e.getKey()),"成功抢书后本次渔获交付为零");
                    require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"天然岸线大笑时仍拿着发光附魔书");
                }
                if(site.kind.equals("boat"))require(a.onGround()&&!a.isPassenger()&&boat.isAlive(),"实际下船上原生岸线");
                MultiplayerCheck.write("case-"+index+"-reveal.txt","PASS "+new Gson().toJson(site)+" frog="+frog.position()+" land="+field("land")+" takeoff="+field("takeoff")+" loot="+loot+"\n");stage(phase+1);
            } else if(sub==3) {if(NaiwaPranks.busy(a))return;require(!frog.isAlive(),"正常收场没有残留演员");if(boat!=null)require(boat.isAlive()&&boat.getPassengers().isEmpty(),"原船保留且后座清理");stage(phase+1);}
            else if(sub==5) {
                require(refused&&!NaiwaPranks.busy(a),"拒绝生成后原版钓鱼不启动隐藏演员");
                if(fishEvents==0)return;
                require(fishEvents==1&&fishLoot>0&&a.getInventory().items.get(3).getDamageValue()-rodBefore==1,"浅滩实际原版渔获与一次收竿耐久正常");
                for(var e:loot.entrySet())require(total(a,e.getKey())-before.get(e.getKey())==e.getValue(),"浅滩无演员时渔获仍守恒");stage(phase-1);
            } else if(sub==4) {
                if(site.kind.equals("fish")&&!refused)require(total(a,Items.ENCHANTED_BOOK)==before.getOrDefault(Items.ENCHANTED_BOOK,0),"自然场景退场也不掉落道具书");
                int checked=0;for(var e:terrain.entrySet()) {
                    if(built.containsKey(e.getKey())||(site.kind.equals("tree")||site.kind.equals("mine"))&&e.getKey().equals(site.anchor()))continue;
                    BlockState after=level.getBlockState(e.getKey()),expected=e.getValue();
                    // 新区块树叶会用计划 tick 修正离原木距离；只忽略这个原版内部值，仍比较叶块、含水与持久标记。
                    if(expected.is(BlockTags.LEAVES)&&after.is(expected.getBlock())&&expected.hasProperty(LeavesBlock.DISTANCE))after=after.setValue(LeavesBlock.DISTANCE,expected.getValue(LeavesBlock.DISTANCE));
                    require(after.equals(expected),"自然地形被改写 "+e.getKey()+" before="+expected+" after="+after);checked++;
                }
                MultiplayerCheck.write("case-"+index+"-result.txt",refused?"PASS safe underwater collision refusal; actual vanilla catch/durability/loot; no actor or cooldown\n":blocked?"PASS all candidate arcs blocked; safe cancellation; actual catch/durability/loot and cleanup\n":"PASS real two-client natural terrain trigger/reveal/collision/cleanup\n");
                MultiplayerCheck.write("case-"+index+"-terrain.txt","PASS "+checked+" natural blocks unchanged (only placed glass/door and legitimately mined trunk excluded)\n");
                if(++index<cases.size())fixture(a,v);else {MultiplayerCheck.write("server-result.txt","PASS "+cases.size()+" real two-client natural terrain cases; natural shores/caves unmodified"+(System.getProperty("naiwa.qa.focus")==null?"":"; FOCUSED "+System.getProperty("naiwa.qa.focus"))+"\n");stage(99);done=true;}
            }
        }catch(Throwable error) {done=true;error.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+error);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}
    }
}
