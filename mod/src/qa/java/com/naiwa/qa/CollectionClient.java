package com.naiwa.qa;

import com.google.gson.Gson;
import com.naiwa.Naiwa;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 不取瓶、不瞄准、不右键；只查看 TCP 背包数据并拍下原版背包界面。 */
final class CollectionClient {
    private final String role=System.getProperty("naiwa.qa.role");
    private int phase,age;
    private boolean done;
    CollectionClient(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());}
    private void tick(){if(done)return;var mc=Minecraft.getInstance();try{
        if(mc.getWindow()==null||mc.getMainRenderTarget()==null)return;
        org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
        if(mc.getMainRenderTarget().width!=1000||mc.getMainRenderTarget().height!=760){mc.getWindow().setWindowed(1000,760);mc.resizeDisplay();return;}
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null||mc.screen!=null&&!(mc.screen instanceof InventoryScreen))return;
        if(!Files.exists(MultiplayerCheck.ROOT.resolve(role+"-connected.ok")))MultiplayerCheck.write(role+"-connected.ok","PASS");
        String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int next=Integer.parseInt(marker);
        MultiplayerCheck.require(next!=-1,"服务端专项失败");
        if(next==99){mc.setScreen(null);MultiplayerCheck.write(role+"-result.txt","PASS received seven automatic collection cases without selecting bottles or use input\n");done=true;mc.stop();return;}if(next==0)return;
        if(next!=phase){phase=next;age=0;mc.setScreen(null);}
        mc.options.keyUse.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUp.setDown(false);mc.options.hideGui=false;
        int trial=phase/10,step=phase%10;boolean owner=role.equals(trial%2==0?"actor":"viewer");age++;
        if(age==4)mc.setScreen(new InventoryScreen(mc.player));
        if(age<25||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-collection-"+phase+".ok")))return;
        int gas=mc.player.getInventory().countItem(Naiwa.GAS.get()),bottles=mc.player.getInventory().countItem(Items.GLASS_BOTTLE);
        if(step>=2){
            int expectedGas=trial==3||trial==6?16:(trial==4||trial==5)&&step==2?0:1;
            int expectedBottles=trial==2?8:trial==1?1:trial==5&&step==2?0:trial==4&&step==2?3:2;
            MultiplayerCheck.require(gas==(owner?expectedGas:0)&&bottles==(owner?expectedBottles:2),"客户端真实背包数量 phase="+phase+" gas="+gas+" bottles="+bottles);
            if(owner&&(trial==0||trial==1||trial==5))MultiplayerCheck.require(mc.player.getMainHandItem().is(Items.IRON_PICKAXE),"自动采集不动手中工具");
        }
        var data=new LinkedHashMap<String,Object>();data.put("phase",phase);data.put("role",role);data.put("owner",owner);data.put("gas",gas);data.put("bottles",bottles);data.put("mainHand",mc.player.getMainHandItem().toString());data.put("offHand",mc.player.getOffhandItem().toString());data.put("useInput",mc.options.keyUse.isDown());
        MultiplayerCheck.write(role+"-collection-"+phase+".json",new Gson().toJson(data));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){MultiplayerCheck.require(image.getWidth()==1000&&image.getHeight()==760,"有效画布");image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-collection-"+phase+".png"));}
        MultiplayerCheck.write(role+"-collection-"+phase+".ok","PASS");
    }catch(Throwable failure){done=true;failure.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+failure);}catch(Exception ignored){}mc.stop();}}
}
