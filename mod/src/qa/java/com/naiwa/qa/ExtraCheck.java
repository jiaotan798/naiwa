package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 八轮只预置场地和原版材料；触发、追诱饵、开关箱及望远镜收放都由真实客户端数据包执行。 */
final class ExtraCheck {
    static final String[] KINDS={"creeper","diamond","chest","scope"};
    static final AABB AREA=new AABB(-16,77,-16,16,94,36);
    static final BlockPos CHEST=new BlockPos(0,81,0);
    static final BlockPos ORE=new BlockPos(0,82,2);
    private int phase,index,ticks,started,diamondCount;
    private boolean done;
    private NaiwaEntity frog;
    private final Map<BlockPos,BlockState> terrain=new HashMap<>();
    ExtraCheck(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,(net.neoforged.neoforge.event.level.BlockDropsEvent e)->{
        // 在成品接管前只读记录真实时运结果，最终按这一数值核对，不再随机重算一遍掉落。
        if(!done&&kind().equals("diamond")&&e.getPos().equals(ORE)&&e.getBreaker() instanceof ServerPlayer p&&p.getGameProfile().getName().equals("NaiwaActor"))diamondCount=e.getDrops().stream().filter(item->item.getItem().is(Items.DIAMOND)).mapToInt(item->item.getItem().getCount()).sum();
    });}
    private String kind(){return KINDS[index/2];}
    private boolean abort(){return index%2==1;}
    private void require(boolean ok,String why){MultiplayerCheck.require(ok,"extra case="+index+" phase="+phase+": "+why);}
    private boolean ack(String role){return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok"));}
    private void stage(int next)throws Exception{phase=next;started=ticks;MultiplayerCheck.write("stage.txt",""+phase);System.out.println("NAIWA_MULTIPLAYER extras case="+index+" kind="+kind()+" abort="+abort()+" stage="+phase);}
    private Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private Object scene(ServerPlayer p)throws Exception{return ((Map<?,?>)fieldStatic("SCENES")).get(p.getUUID());}
    private Object fieldStatic(String name)throws Exception{var f=NaiwaPranks.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private void fixture(ServerPlayer a,ServerPlayer v)throws Exception {
        var level=a.serverLevel();NaiwaPranks.cancel(a);NaiwaPranks.cancel(v);
        frog=null;diamondCount=0;terrain.clear();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=34;z++)for(int y=79;y<=85;y++)level.setBlock(new BlockPos(x,y,z),(y<=80?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        // 拆除上一轮真实箱子会按原版掉出内容，必须在清地形之后清实体，避免旧苹果被下一轮玩家拾取。
        for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,AREA,e->!(e instanceof ServerPlayer)))e.discard();
        for(ServerPlayer p:List.of(a,v)){
            p.stopUsingItem();p.closeContainer();p.stopRiding();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);
            p.getPersistentData().putLong("naiwa.prank_next",Long.MAX_VALUE);
            // 设置专项的同种第二轮保留真实上次记录，不能靠清空历史伪造连续触发通过。
            if(!Boolean.getBoolean("naiwa.qa.settings"))p.getPersistentData().putString("naiwa.prank_last","");
            p.setDeltaMovement(Vec3.ZERO);
        }
        a.connection.teleport(.5,81,kind().equals("chest")?4:.5,kind().equals("chest")?180:0,0);v.connection.teleport(kind().equals("chest")?3:4,81,kind().equals("scope")||kind().equals("chest")?3:-1,90,0);
        a.getInventory().items.set(1,new ItemStack(Items.SPYGLASS));a.getInventory().items.set(2,new ItemStack(Items.GLASS_BOTTLE,3));
        if(kind().equals("diamond")){
            var pickaxe=new ItemStack(Items.IRON_PICKAXE);if(!abort())pickaxe.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),3);
            a.getInventory().items.set(0,pickaxe);level.setBlock(ORE,(abort()?Blocks.DEEPSLATE_DIAMOND_ORE:Blocks.DIAMOND_ORE).defaultBlockState(),2);
        }
        // 跨箱子菜单的增量包可能晚到；新夹具完整同步背包，不能沿用旧菜单的槽位显示。
        a.inventoryMenu.broadcastFullState();v.inventoryMenu.broadcastFullState();
        if(kind().equals("chest")) {level.setBlock(CHEST,Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.SOUTH),3);((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.APPLE,37));}
        for(BlockPos b:BlockPos.betweenClosed(new BlockPos(-8,80,-8),new BlockPos(8,84,25)))terrain.put(b.immutable(),level.getBlockState(b));
        MultiplayerCheck.write("extra-case.txt",index+" "+kind()+" "+abort());stage(2000+index*10+6);
    }
    private void tick(ServerTickEvent.Post event){
        if(done)return;ticks++;
        try {
            var server=event.getServer();ServerPlayer a=server.getPlayerList().getPlayerByName("NaiwaActor"),v=server.getPlayerList().getPlayerByName("NaiwaViewer");
            require(ticks-started<(phase==0?7200:1800),"阶段超时");
            if(a==null||v==null||!Files.exists(MultiplayerCheck.ROOT.resolve("actor-connected.ok"))||!Files.exists(MultiplayerCheck.ROOT.resolve("viewer-connected.ok")))return;
            if(phase==0){server.overworld().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);server.overworld().setDayTime(1000);fixture(a,v);return;}
            int sub=(phase-2000)%10;
            if(sub==3&&(ticks-started)%40==0)MultiplayerCheck.write("extra-progress.txt","case="+index+" actor="+a.position()+" frog="+(frog==null?null:frog.position())+" act="+(frog==null?null:frog.act())+" gas="+a.getInventory().countItem(Naiwa.GAS.get())+" busy="+NaiwaPranks.busy(a)+"\n");
            if(sub==6){
                if(!ack("actor")||!ack("viewer"))return;
                if(kind().equals("scope")&&(!a.isUsingItem()||!a.getUseItem().is(Items.SPYGLASS)))return;
                a.getPersistentData().putLong("naiwa.prank_next",0);stage(2000+index*10);return;
            }
            if(sub==0 && NaiwaPranks.busy(a)) {
                Object scene=scene(a);require(field(scene,"kind").equals(kind()),"真实操作触发指定整蛊，实际="+field(scene,"kind")+" player="+a.position()+" using="+a.isUsingItem()+" hand="+a.getMainHandItem());frog=(NaiwaEntity)field(scene,"frog");
                long delay=a.getPersistentData().getLong("naiwa.prank_next")-(long)field(scene,"start");
                int first=NaiwaConfig.MIN_INTERVAL_SECONDS.get(),second=NaiwaConfig.MAX_INTERVAL_SECONDS.get();
                require(delay>=Math.min(first,second)*20L&&delay<=Math.max(first,second)*20L,"实际冷却使用配置的秒数范围");
                if(Boolean.getBoolean("naiwa.qa.settings"))require(first==1&&second==1&&NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get()&&delay==20,"服务端实际读取一秒间隔与允许连续设置");
            }
            if(!ack("actor")||!ack("viewer"))return;
            if(sub==0){if(frog==null)return;stage(phase+1);}
            else if(sub==1){
                require(NaiwaPranks.busy(a),"双方确认演出时场景仍存在");
                if(kind().equals("diamond"))require(diamondCount>=1&&!frog.isAddedToLevel()&&a.level().getBlockState(ORE).isAir()&&a.getMainHandItem().getDamageValue()==1,"真实挖掉矿石、扣一次耐久后出现诱饵，奶蛙尚未露出");
                if(kind().equals("scope"))require(frog.isTemporary()&&Math.abs(frog.position().subtract(a.position()).horizontalDistance()-4)<.01&&frog.scopeViewer()==a.getId(),"空场开镜生成四格演员及本人图片标记");
                if(kind().equals("chest"))require(frog.act().equals("chest_peek")&&frog.getBbHeight()<1,"箱内探头保留小尺寸，不站箱盖或翻倒");
                stage(phase+1);
            } else if(sub==2){
                if(abort()) {if(NaiwaPranks.busy(a))return;require(!frog.isAlive(),"取消清除临时演员");}
                else {if(!frog.animation().equals("laugh_sequence"))return;require(frog.isAlive(),"成功演员真实存在");}
                stage(phase+1);
            } else if(sub==3){
                if(kind().equals("diamond")&&frog.isAlive())require(a.getInventory().countItem(Items.DIAMOND)==0&&a.serverLevel().getEntitiesOfClass(ItemEntity.class,AREA,e->e.getItem().is(Items.DIAMOND)).isEmpty(),"奶蛙尚未消失时不能交还钻石");
                if(NaiwaPranks.busy(a))return;
                require(a.getInventory().countItem(Naiwa.GAS.get())==(abort()?0:1),"成功实际采集一瓶，失败无奖励");
                int diamonds=a.getInventory().countItem(Items.DIAMOND)+v.getInventory().countItem(Items.DIAMOND)+a.serverLevel().getEntitiesOfClass(ItemEntity.class,AREA,e->e.getItem().is(Items.DIAMOND)).stream().mapToInt(e->e.getItem().getCount()).sum();
                require(diamonds==(kind().equals("diamond")?diamondCount:0),"钻石整蛊退场后原数交还本次实际时运结果，其它整蛊不制造钻石");
                if(kind().equals("diamond"))MultiplayerCheck.write("diamond-loot-"+index+".json",new com.google.gson.Gson().toJson(Map.of("expected",diamondCount,"returned",diamonds,"fortune",!abort(),"abort",abort(),"oreBroken",a.level().getBlockState(ORE).isAir(),"actorRemoved",!frog.isAlive(),"endpoint",List.of(frog.getX(),frog.getY(),frog.getZ()))));
                if(kind().equals("diamond"))for(var drop:a.serverLevel().getEntitiesOfClass(ItemEntity.class,AREA,e->e.getItem().is(Items.DIAMOND)))require(drop.position().subtract(frog.position()).horizontalDistance()<1.5,"钻石落在奶蛙消失处");
                require(a.serverLevel().getEntitiesOfClass(NaiwaDecoy.class,AREA).isEmpty(),"诱饵全部清理");
                require(!frog.isAlive(),"临时演员退出");
                if(kind().equals("chest"))require(((ChestBlockEntity)a.level().getBlockEntity(CHEST)).getItem(0).getCount()==37,"箱内物品未变");
                for(var e:terrain.entrySet())if(!(kind().equals("diamond")&&e.getKey().equals(ORE)))require(a.level().getBlockState(e.getKey()).equals(e.getValue()),"除实际开采的钻石矿外没有地形改写："+e.getKey());
                stage(phase+1);
            } else if(sub==4){
                if(kind().equals("diamond"))require(a.getInventory().countItem(Items.DIAMOND)==diamondCount,"实际拾取全部返还钻石后才完成本轮");
                MultiplayerCheck.write("extra-case-"+index+"-result.txt","PASS "+kind()+" "+(abort()?"abort":"success+collection")+"; two clients, real actions, original diamond loot="+(kind().equals("diamond")?diamondCount:0)+", terrain "+(terrain.size()-(kind().equals("diamond")?1:0))+" blocks unchanged, cleanup\n");
                if(++index<8)fixture(a,v);else {MultiplayerCheck.write("server-result.txt","PASS four new pranks x success/abort; real survival TCP clients, collection, safe decoy and chest contents, temporary scope actor cleanup\n");MultiplayerCheck.write("stage.txt","99");done=true;}
            }
        }catch(Throwable error){done=true;error.printStackTrace();try{MultiplayerCheck.write("server-result.txt","FAIL "+error);MultiplayerCheck.write("stage.txt","-1");}catch(Exception ignored){}}
    }
}
