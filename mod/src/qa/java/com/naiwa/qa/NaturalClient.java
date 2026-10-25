package com.naiwa.qa;

import net.minecraft.world.item.Items;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** 两个正式客户端按场地坐标操作；阶段文件只控制屏障，所有实体、碰撞和渔获均经真实 TCP 同步。 */
final class NaturalClient {
    private final String role=System.getProperty("naiwa.qa.role");
    private final boolean actor=role.equals("actor");
    private int ticks,last=-2,age,action,breakAt,reelAt;
    private int frogId=-1;
    private int badFrames,fishStep;
    private NaturalSite site;
    private Vec3 treeView;
    private boolean done;
    private String lastState="";
    private final Set<String> recorded=new HashSet<>();
    private EntityDataAccessor<Boolean> biting;
    NaturalClient() {NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());}
    private void require(boolean ok,String why) {MultiplayerCheck.require(ok,role+" natural phase="+last+": "+why);}
    private void release(Minecraft mc) {mc.options.keyUp.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyShift.setDown(false);mc.options.keyJump.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);}
    private void screenshot(Minecraft mc,String name) throws Exception {require(mc.getMainRenderTarget().width>=900&&mc.getMainRenderTarget().height>=650,"拒绝将 1×1 占位缓冲作为实机截图");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-"+name+".png"));}}
    /** 窗口复用或缩小时可能留下占位主缓冲；只按 GLFW 实际画布重建显示，等下一帧真正绘制后再进行验收。 */
    private boolean viewport(Minecraft mc) throws Exception {
        long handle=mc.getWindow().getWindow();int[] width={0},height={0};GLFW.glfwGetFramebufferSize(handle,width,height);
        if(width[0]>=900&&height[0]>=650&&mc.getWindow().getWidth()>=900&&mc.getWindow().getHeight()>=650&&mc.getMainRenderTarget().width>=900&&mc.getMainRenderTarget().height>=650&&!mc.noRender){badFrames=0;return true;}
        require(++badFrames<200,"无法恢复真实绘制窗口 native="+width[0]+"x"+height[0]);
        if(badFrames==1)MultiplayerCheck.write(role+"-window-before.txt","native="+width[0]+"x"+height[0]+" window="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+" main="+mc.getMainRenderTarget().width+"x"+mc.getMainRenderTarget().height+" noRender="+mc.noRender+"\n");
        if(width[0]<900||height[0]<650){GLFW.glfwShowWindow(handle);GLFW.glfwRestoreWindow(handle);mc.getWindow().setWindowed(1000,760);GLFW.glfwSetWindowSize(handle,1000,760);GLFW.glfwGetFramebufferSize(handle,width,height);}
        if(width[0]>=900&&height[0]>=650){mc.getWindow().setWidth(width[0]);mc.getWindow().setHeight(height[0]);mc.noRender=false;mc.resizeDisplay();MultiplayerCheck.write(role+"-window-after.txt","PASS native="+width[0]+"x"+height[0]+" main="+mc.getMainRenderTarget().width+"x"+mc.getMainRenderTarget().height+"\n");}
        return false;
    }
    private void look(Minecraft mc,Vec3 point) {Vec3 d=point.subtract(mc.player.getEyePosition());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));}
    private void slot(Minecraft mc,int index) {if(mc.player.getInventory().selected!=index){mc.player.getInventory().selected=index;mc.getConnection().send(new ServerboundSetCarriedItemPacket(index));}}
    private void door(Minecraft mc,NaiwaEntity frog) {
        look(mc,Vec3.atCenterOf(site.anchor()));mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(site.anchor()),site.direction().getOpposite(),site.anchor(),false));
        if(frog!=null)look(mc,frog.getEyePosition());
    }
    private void dig(Minecraft mc,ServerboundPlayerActionPacket.Action action) {mc.getConnection().send(new ServerboundPlayerActionPacket(action,site.anchor(),site.direction().getOpposite(),0));}
    @SuppressWarnings("unchecked")
    private boolean biting(FishingHook hook) throws Exception {if(biting==null){var f=FishingHook.class.getDeclaredField("DATA_BITING");f.setAccessible(true);biting=(EntityDataAccessor<Boolean>)f.get(null);}return hook.getEntityData().get(biting);}
    /** 通过游泳、前进和原版跳跃走上真实岸线，不发送传送或补建踏脚块。 */
    private boolean walk(Minecraft mc,Vec3 point) {
        if(mc.player.position().subtract(point).horizontalDistance()<.4){mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);return mc.player.onGround();}
        look(mc,new Vec3(point.x,mc.player.getEyeY(),point.z));mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(mc.player.isInWater()||mc.player.horizontalCollision);return false;
    }
    private void tick() {
        if(done||++ticks%2!=0)return;var mc=Minecraft.getInstance();
        try {
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null||mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)return;
            if(!viewport(mc))return;
            MultiplayerCheck.write(role+"-connected.ok",""+ProcessHandle.current().pid());
            String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty())return;int phase=Integer.parseInt(marker);
            if(phase==-1)throw new AssertionError("服务端自然地形断言失败");
            if(phase==99){MultiplayerCheck.write(role+"-result.txt","PASS actual client natural terrain actions, synchronized poses and cleanup\n");release(mc);done=true;mc.stop();return;}
            if(phase==0)return;
            if(phase!=last){release(mc);site=NaturalSite.read();last=phase;age=action=fishStep=0;lastState="";treeView=null;
                String[] identity=Files.readString(MultiplayerCheck.ROOT.resolve("actor-entity.txt")).trim().split(" ");
                require(identity.length==2&&Integer.parseInt(identity[0])==(phase-1000)/10,"演员编号属于当前场次");frogId=Integer.parseInt(identity[1]);}
            age++;int sub=(phase-1000)%10;
            if(age==20)screenshot(mc,"settled-"+phase);
            if(sub==6)return;
            if(actor&&site.kind.equals("mirror")&&sub<2)look(mc,site.point(.5,mc.player.getEyeY()-site.y,.5));
            if(actor&&site.kind.equals("mine")&&sub<2)mc.player.swing(InteractionHand.MAIN_HAND);
            // 自然世界可能有常驻奶蛙；编号只标识检查目标，动作和移除状态仍取自实际 TCP 同步的实体。
            NaiwaEntity frog=mc.level.getEntity(frogId) instanceof NaiwaEntity n?n:null;var frogs=frog==null?List.<NaiwaEntity>of():List.of(frog);
            // 高岸使出水前后的视角变化更大；收竿操作确认后仍持续追看实际同步的演员，直至完成露馅。
            if(frog!=null&&site.kind.equals("fish")&&sub>=2)look(mc,frog.getEyePosition());
            // 凸岸会截住抛物线；按实际浮漂落地状态收回，走到岸沿再正常抛竿，绝不挪动浮漂或削去土块。
            if(actor&&site.kind.equals("fish")&&sub==0&&age>=20&&action<6&&fishStep<3) {
                if(action%2==0&&mc.player.fishing!=null&&mc.player.fishing.onGround()) {slot(mc,3);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action++;}
                else if(action%2==1&&mc.player.fishing==null&&walk(mc,site.point(.5,site.bankStep,4))) {
                    slot(mc,3);mc.player.setYRot(site.yaw(180));mc.player.setXRot(action==1?65:action==3?45:30);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action++;
                }
            }
            if(frog!=null) {
                String state=frog.animation().equals("laugh_sequence")?"laugh_sequence":frog.act(),key=phase+"-"+state;
                if(state.equals(lastState)&&!state.isEmpty()&&recorded.add(key)){screenshot(mc,"state-"+key);Files.writeString(MultiplayerCheck.ROOT.resolve(role+"-states.txt"),key+" "+frog.position()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}lastState=state;
            }
            if(age<5||Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+phase+".ok")))return;
            require(mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.getSingleplayerServer()==null,"生存模式、真实专用服连接");
            boolean ready=false;
            if(sub==0) {
                if(actor) switch(site.kind) {
                    case "mirror" -> {slot(mc,0);look(mc,site.point(.5,1.62,.5));}
                    case "door" -> {slot(mc,0);door(mc,null);}
                    case "tree" -> {slot(mc,0);look(mc,Vec3.atCenterOf(site.anchor()));dig(mc,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);mc.player.swing(InteractionHand.MAIN_HAND);dig(mc,ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);}
                    case "mine" -> {slot(mc,2);look(mc,Vec3.atCenterOf(site.anchor()));mc.gameMode.startDestroyBlock(site.anchor(),site.direction().getOpposite());mc.player.swing(InteractionHand.MAIN_HAND);}
                    case "fish" -> {
                        slot(mc,3);
                        if(fishStep==0){mc.player.setYRot(site.yaw(180));mc.player.setXRot(30);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);fishStep=1;return;}
                        if(fishStep==1&&mc.player.fishing!=null&&biting(mc.player.fishing)){fishStep=2;reelAt=age+3;}
                        if(fishStep==2&&age>=reelAt){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);fishStep=3;}
                        if(fishStep<3||mc.player.fishing!=null)return;
                    }
                    case "boat" -> {
                        slot(mc,0);var boats=mc.level.getEntitiesOfClass(Boat.class,site.area());if(boats.isEmpty())return;Boat target=boats.getFirst();
                        if(!mc.player.isPassenger()) {
                            // 自然凹岸可能只能在稍外侧放船；通过普通走路/游泳接近，再等待真正乘船同步，不能远距点击后直接确认。
                            if(mc.player.distanceToSqr(target)>6.25){walk(mc,target.position());return;}
                            release(mc);look(mc,target.position());mc.gameMode.interact(mc.player,target,InteractionHand.MAIN_HAND);return;
                        }
                    }
                }
                ready=true;
            } else if(sub==1) {
                if(frog!=null&&site.kind.equals("fish"))require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"天然岸线双方同步出水附魔书");
                if(frog==null)return;require(site.kind.equals("fish")?frog.act().equals("fish_land")||frog.animation().equals("laugh_sequence"):frog.act().equals(site.kind),"双方收到真实 "+site.kind+" 动作，实际="+frog.act());look(mc,frog.getEyePosition());
                if(actor&&site.kind.equals("boat")&&age<24){mc.player.setYRot(site.yaw(0));mc.player.setXRot(0);mc.options.keyUp.setDown(true);return;}ready=true;
            } else if(sub==2) {
                if(frog==null)return;
                if(!actor){look(mc,frog.getEyePosition());ready=true;}
                else switch(site.kind) {
                    case "mirror" -> {look(mc,frog.getEyePosition());mc.options.keyShift.setDown(age<16);mc.options.keyRight.setDown(age>=7&&age<16);mc.options.keyJump.setDown(age>=18&&age<21);ready=age>=32;}
                    case "door" -> {if(age==5||age==9||age==13||age==17)door(mc,frog);else look(mc,frog.getEyePosition());ready=age>=22;}
                    case "tree" -> {
                        look(mc,Vec3.atCenterOf(site.anchor()));
                        if(action==0){float speed=mc.level.getBlockState(site.anchor()).getDestroyProgress(mc.player,mc.level,site.anchor());require(speed>0,"原版斧头可采集天然原木");breakAt=age+(int)Math.ceil((Math.ceil(1/speed)+4)/2);dig(mc,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);action=1;}
                        mc.player.swing(InteractionHand.MAIN_HAND);if(action==1&&age>=breakAt){dig(mc,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);action=2;}
                        // 天然相邻树干也会挡住侧面；先选客户端实际无遮挡的站位，再通过普通移动绕过去。
                        if(mc.level.getBlockState(site.anchor()).isAir()) {
                            if(mc.player.hasLineOfSight(frog)){release(mc);look(mc,frog.getEyePosition());ready=true;}
                            else {
                                if(treeView==null)for(double x:new double[]{-1.5,2.5,-2.5,3.5})for(int dy:new int[]{0,1,-1}) {
                                    Vec3 p=site.point(x,dy,2);var b=net.minecraft.core.BlockPos.containing(p);
                                    if(mc.level.getBlockState(b.below()).isFaceSturdy(mc.level,b.below(),Direction.UP)&&mc.level.noCollision(new AABB(p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.8,p.z+.3))&&NaiwaGameplay.clearLine(mc.level,p.add(0,mc.player.getEyeHeight(),0),frog.getEyePosition())) {treeView=p;break;}
                                }
                                require(treeView!=null,"天然树林存在可绕行观察点");walk(mc,treeView);
                            }
                        }
                    }
                    case "mine" -> {
                        if(!mc.level.getBlockState(site.anchor()).isAir()){look(mc,Vec3.atCenterOf(site.anchor()));mc.gameMode.continueDestroyBlock(site.anchor(),site.direction().getOpposite());mc.player.swing(InteractionHand.MAIN_HAND);return;}
                        mc.gameMode.stopDestroyBlock();look(mc,frog.getEyePosition());ready=age>=25;
                    }
                    case "fish" -> {look(mc,frog.getEyePosition());require(mc.player.fishing==null,"演员出现时原版收竿已经结束");ready=true;}
                    case "boat" -> {if(mc.player.isPassenger()){mc.options.keyShift.setDown(true);return;}mc.options.keyShift.setDown(false);if(walk(mc,site.point(.5,site.bankStep,6))){look(mc,frog.getEyePosition());ready=true;}}
                }
            } else if(sub==5) {
                require(frogs.isEmpty(),"浅滩身体空间不足时双方均没有穿入河床的演员");
                if(!actor){look(mc,site.point(.5,0,.5));ready=true;}
                else {if(mc.player.fishing!=null)look(mc,mc.player.fishing.position());if(action==0&&mc.player.fishing!=null&&biting(mc.player.fishing)){action=1;reelAt=age+3;}if(action==1&&age>=reelAt){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=2;}ready=action==2;}
            } else if(sub==3){if(frog==null)return;require(frog.animation().equals("laugh_sequence"),"双方收到露馅大笑");if(site.kind.equals("fish"))require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"双方大笑时仍同步附魔书");look(mc,frog.getEyePosition());ready=true;}
            else if(sub==4){require(frogs.isEmpty(),"双方实际移除演员");ready=true;}
            if(ready){release(mc);screenshot(mc,"stage-"+phase);MultiplayerCheck.write(role+"-"+phase+".ok","PASS\n");}
        } catch(Throwable error){done=true;release(mc);error.printStackTrace();try{MultiplayerCheck.write(role+"-result.txt","FAIL "+error);}catch(Exception ignored){}mc.stop();}
    }
}
