package com.naiwa.qa;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.naiwa.Naiwa;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 探针临时切第一人称，确认飞升不画手或持物，结束后恢复原版；不改变正式版背面视角。 */
final class AscensionHands {
    private int shot=-1;
    private final int[] events=new int[2],cancelled=new int[2];
    AscensionHands(){NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,true,(RenderHandEvent e)->{
        if(shot<0)return;int hand=e.getHand()==InteractionHand.MAIN_HAND?0:1;
        events[hand]++;if(e.isCanceled())cancelled[hand]++;
    });}

    /** 原六秒悬停内检查空手、双手持物及左惯用手，取图后恢复背面与右惯用手。 */
    boolean tick(Minecraft mc,int trial,int age,Map<String,Object> shader)throws Exception{
        int next=age>=10&&age<=25?0:age>=40&&age<=54?1:age>=55&&age<=68?2:-1;
        return window(mc,trial,age,next,next==0?20:next==1?50:65,shader);
    }

    /** 上升、缓降及真实落地后的窗口独立检查，不依靠悬停截图推断整个流程。 */
    boolean stage(Minecraft mc,int trial,int step,int age,Map<String,Object> shader)throws Exception{
        int next=step==2&&age>=12&&age<=40?4:step==4&&age>=10&&age<=35?5:step==5&&age>=10&&age<=35?3:-1;
        return window(mc,trial,age,next,next==4?28:next==5?18:20,shader);
    }

    /** 0/1/2 为三种悬停组合，3 为结束恢复，4/5 为上升和缓降；窗口内攒够实际绘制帧才结算。 */
    private boolean window(Minecraft mc,int trial,int age,int next,int earliest,Map<String,Object> shader)throws Exception{
        if(next<0){shot=-1;return false;}
        String name=System.getProperty("naiwa.qa.role")+"-hands-"+trial+"-"+next;
        if(java.nio.file.Files.exists(MultiplayerCheck.ROOT.resolve(name+".ok"))){shot=-1;return false;}
        if(next!=shot){shot=next;Arrays.fill(events,0);Arrays.fill(cancelled,0);}
        boolean hidden=next!=3;HumanoidArm dominant=next==2?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        if(mc.options.mainHand().get()!=dominant){mc.options.mainHand().set(dominant);mc.options.broadcastOptions();}
        // 必须开着 GUI 检验取消事件，不能靠 F1 把原版手部一并隐藏来冒充规则生效。
        mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setXRot(0);
        // 入场建区块可能让多个 tick 挤在一帧中，不能把经过八个 tick 当成已画过八帧；仍受原阶段时限约束。
        if(age<earliest||events[0]<=2||next!=0&&next!=4&&events[1]<=2)return true;
        MultiplayerCheck.require(mc.player.hasEffect(Naiwa.ASCENDING)==hidden&&mc.player.getMainArm()==dominant,"真实飞升状态及惯用手");
        MultiplayerCheck.require(events[0]>2&&cancelled[0]==(hidden?events[0]:0),"主手飞升取消、结束恢复 events="+events[0]+" cancelled="+cancelled[0]);
        if(next==0||next==4)MultiplayerCheck.require(mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty(),"空手和上升窗口没有夹具物品");
        else{
            MultiplayerCheck.require(mc.player.getMainHandItem().is(Items.IRON_SWORD)&&mc.player.getOffhandItem().is(Items.TORCH),"主副手物品仍在真实背包中");
            MultiplayerCheck.require(events[1]>2&&cancelled[1]==(hidden?events[1]:0),"副手飞升取消、结束恢复");
        }
        var data=new LinkedHashMap<String,Object>();data.put("trial",trial);data.put("case",next);data.put("hidden",hidden);data.put("camera",mc.options.getCameraType().name());
        data.put("stage",hidden?mc.player.getEffect(Naiwa.ASCENDING).getAmplifier():-1);data.put("dominant",dominant.name());
        data.put("mainHand",mc.player.getMainHandItem().toString());data.put("offHand",mc.player.getOffhandItem().toString());
        data.put("events",events.clone());data.put("cancelled",cancelled.clone());data.put("shader",shader);data.put("callback",callback(mc,hidden));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){MultiplayerCheck.require(image.getWidth()==1000&&image.getHeight()==760,"有效第一人称帧缓冲");image.writeToFile(MultiplayerCheck.ROOT.resolve(name+".png"));}
        MultiplayerCheck.write(name+".json",new Gson().toJson(data));MultiplayerCheck.write(name+".ok","PASS");
        mc.options.hideGui=true;mc.options.setCameraType(hidden?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        if(dominant==HumanoidArm.LEFT){mc.options.mainHand().set(HumanoidArm.RIGHT);mc.options.broadcastOptions();}
        shot=-1;return true;
    }

    /** 额外调用正在运行的成品回调，缓冲探针拒绝任何网格提交，防止“取消原版但另画奶蛙手”再次通过。 */
    private Map<String,Object> callback(Minecraft mc,boolean hidden)throws Exception{
        var method=Class.forName("com.naiwa.client.NaiwaPlayerRenderer").getDeclaredMethod("hand",RenderHandEvent.class);method.setAccessible(true);
        int[] requests={0};
        for(var hand:InteractionHand.values()){
            var event=new RenderHandEvent(hand,new PoseStack(),type->{requests[0]++;throw new AssertionError("第一人称回调不得额外提交网格");},net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,0,0,0,0,mc.player.getItemInHand(hand));
            method.invoke(null,event);MultiplayerCheck.require(event.isCanceled()==hidden,"成品回调只在飞升期取消绘制");
        }
        return Map.of("cancelled",hidden,"bufferRequests",requests[0]);
    }
}
