package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Items;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 两个真实客户端只读各自收到的实体、背包和效果；交互走原版游戏模式发送数据包。 */
public final class MultiplayerClient {
    private final boolean actor=System.getProperty("naiwa.qa.role").equals("actor");
    private final String role=actor?"actor":"viewer";
    private int last=-2,wait,ticks;
    private boolean done;
    public MultiplayerClient() {NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());}
    private void screenshot(Minecraft mc,int stage) throws Exception {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-"+stage+".png"));}
    }
    private void tick() {
        if (done || ++ticks%2!=0) return;
        var mc=Minecraft.getInstance();
        try {
            if (mc.player==null || mc.level==null || mc.getOverlay()!=null) return;
            MultiplayerCheck.write(role+"-connected.ok",Long.toString(ProcessHandle.current().pid()));
            int stage=Integer.parseInt(Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim());
            if (stage==-1) throw new AssertionError("服务端探针失败");
            if (stage==99) {MultiplayerCheck.write(role+"-result.txt","PASS: real production client received and verified all synchronized states.\n");done=true;mc.stop();return;}
            if (stage!=last) {last=stage;wait=0;}
            // 留出网络和实体插值时间；采集窗口还需等笑声入场 0.6 秒。
            if (++wait<(stage==6?12:5) || stage==0 || Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+stage+".ok"))) return;
            Player a=actor?mc.player:mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("NaiwaActor")).findFirst().orElse(null);
            if(a==null && stage!=18 && stage!=101) return;
            switch(stage) {
                case 1 -> {
                    MultiplayerCheck.require(mc.getSingleplayerServer()==null && mc.level.players().size()==2,"必须连接专用服的两个真实玩家");
                    var info=net.neoforged.fml.ModList.get().getModContainerById("naiwa").orElseThrow().getModInfo();
                    MultiplayerCheck.write(role+"-installation.txt",info.getVersion()+"\n"+info.getOwningFile().getFile().getFilePath()+"\n");
                    if (mc.level.getEntitiesOfClass(NaiwaEntity.class,mc.player.getBoundingBox().inflate(8),f->f.animation().equals("laugh_sequence")).isEmpty()) return;
                }
                case 2 -> {
                    MultiplayerCheck.require(a.getMainHandItem().is(Naiwa.SHIELD.get()),"自己和旁观者收到转化后的盾");
                    if(actor) {mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                }
                case 3,4 -> MultiplayerCheck.require(NaiwaShield.charge(a.getMainHandItem())==stage-2,"盾牌进度同步 "+stage);
                case 5 -> MultiplayerCheck.require(NaiwaShield.charge(a.getMainHandItem())==0 && a.getMainHandItem().has(Naiwa.LAUGH_START.get()),"旁观者收到真实盾牌释放组件");
                case 6 -> {
                    mc.options.keyUse.setDown(false);
                    var frog=mc.level.getEntitiesOfClass(NaiwaEntity.class,mc.player.getBoundingBox().inflate(5)).getFirst();
                    mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);
                }
                case 7 -> {
                    int gas=mc.player.getInventory().countItem(Naiwa.GAS.get()),bottles=mc.player.getInventory().countItem(Items.GLASS_BOTTLE);
                    MultiplayerCheck.require(gas<=1 && bottles==2-gas,"竞争后自己的背包与服务端交易一致");
                }
                case 8 -> {if(actor) mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
                case 9 -> {
                    Husk mob=mc.level.getEntitiesOfClass(Husk.class,mc.player.getBoundingBox().inflate(12)).getFirst();
                    MultiplayerCheck.require(mob.hasEffect(Naiwa.LAUGHING),"投掷后两个客户端均收到怪物大笑");
                }
                case 91 -> {if(actor) mc.gameMode.attack(mc.player,mc.level.getEntitiesOfClass(Husk.class,mc.player.getBoundingBox().inflate(12)).getFirst());}
                case 10 -> {
                    MultiplayerCheck.require(a.hasEffect(Naiwa.LAUGHING),"自己与旁观者收到玩家大笑");
                    if(actor) mc.gameMode.attack(mc.player,mc.level.getEntitiesOfClass(Husk.class,mc.player.getBoundingBox().inflate(12)).getFirst());
                }
                case 11 -> MultiplayerCheck.require(!a.hasEffect(Naiwa.LAUGHING),"玩家大笑正常结束");
                case 101 -> {if(!actor && a!=null) return;}
                case 102 -> MultiplayerCheck.require(a.hasEffect(Naiwa.LAUGHING),"中途重新追踪收到当前大笑及剩余时间");
                case 12,13,14 -> {
                    MultiplayerCheck.require(a.hasEffect(Naiwa.ASCENDING) && a.getOffhandItem().isEmpty(),"变身和图腾消耗同步");
                    if(stage==13) MultiplayerCheck.require(a.getEffect(Naiwa.ASCENDING).getAmplifier()==2 && Math.abs(a.getY()-87)<.15,"旁观者看到六格悬停");
                    if(stage==14) MultiplayerCheck.require(a.getEffect(Naiwa.ASCENDING).getAmplifier()==3,"缓降阶段同步");
                }
                case 15,17 -> MultiplayerCheck.require(!a.hasEffect(Naiwa.ASCENDING) && !a.isNoGravity(),"结束及重连恢复人形和重力");
                case 16 -> {
                    MultiplayerCheck.require(a.hasEffect(Naiwa.ASCENDING),"飞升中真实退出");
                    if(actor) {MultiplayerCheck.write(role+"-"+stage+".ok","PASS");done=true;mc.stop();return;}
                }
                case 18 -> {
                    if(actor) MultiplayerCheck.require(mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER) && !mc.player.hasEffect(Naiwa.ASCENDING) && !mc.player.isNoGravity(),"换维度收到清理状态");
                    else MultiplayerCheck.require(a==null,"不同维度不残留玩家替身");
                }
                case 19 -> {
                    if(actor && !mc.player.isAlive()) {mc.player.respawn();return;}
                    if(actor && mc.player.getHealth()<=0) return;
                }
                case 181 -> {if(!mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;}
                case 20 -> {
                    if(a==null) return;
                    MultiplayerCheck.require(a.isAlive() && !a.hasEffect(Naiwa.ASCENDING),"真实复活恢复玩家");
                    if(mc.level.getEntitiesOfClass(NaiwaEntity.class,a.getBoundingBox().inflate(10),f->f.animation().equals("laugh_sequence")).isEmpty()) return;
                }
            }
            screenshot(mc,stage);MultiplayerCheck.write(role+"-"+stage+".ok","PASS");
        } catch(Throwable failure) {
            done=true;failure.printStackTrace();try {MultiplayerCheck.write(role+"-result.txt","FAIL stage="+last+": "+failure);}catch(Exception ignored) {}
            mc.stop();
        }
    }
}
