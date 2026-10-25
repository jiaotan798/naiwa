package com.naiwa.client;

import com.naiwa.Naiwa;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 飞升期间固定角色背面第三人称；只临时切换，不保存选项，结束或退出恢复原视角。 */
final class NaiwaTotemCamera {
    private static final NaiwaTotemCamera STATE=new NaiwaTotemCamera();
    private Object owner;
    private CameraType previous;

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->{
            var mc=Minecraft.getInstance();CameraType current=mc.options.getCameraType();
            boolean active=mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.player.hasEffect(Naiwa.ASCENDING);
            CameraType desired=STATE.update(mc.player,active,current);
            if(desired!=current)mc.options.setCameraType(desired);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)->{
            var mc=Minecraft.getInstance();mc.options.setCameraType(STATE.update(null,false,mc.options.getCameraType()));
        });
    }

    /** 用玩家实例引用区分重生、换维度和换存档；四个飞升阶段不能覆盖首次保存的视角。 */
    CameraType update(Object player,boolean active,CameraType current) {
        if(owner!=null&&owner!=player) {current=previous;owner=null;previous=null;}
        if(active&&player!=null) {
            if(previous==null) {owner=player;previous=current;}
            return CameraType.THIRD_PERSON_BACK;
        }
        if(previous!=null) {CameraType restored=previous;owner=null;previous=null;return restored;}
        return current;
    }
}
