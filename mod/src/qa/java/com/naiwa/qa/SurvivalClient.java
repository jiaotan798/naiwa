package com.naiwa.qa;

import com.naiwa.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** 用真实按键、交互包和背包点击走生存流程；不直接修改位置、奖励、效果或钓鱼计时。 */
final class SurvivalClient {
    private final boolean actor=System.getProperty("naiwa.qa.role").equals("actor");
    private final String role=actor?"actor":"viewer";
    private int ticks,last=-2,age,action,craft,breakAt,reelAt,badFrames;
    private boolean done,hover,wasCrouching,wasJumping;
    private String lastAct="";
    private final Set<String> recorded=new HashSet<>();
    private EntityDataAccessor<Boolean> biting;
    SurvivalClient() {NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());}
    private void require(boolean ok,String reason) {MultiplayerCheck.require(ok,role+" stage="+last+": "+reason);}
    private boolean acknowledged() {return Files.exists(MultiplayerCheck.ROOT.resolve(role+"-"+last+".ok"));}
    private void screenshot(Minecraft mc,String name) throws Exception {
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {image.writeToFile(MultiplayerCheck.ROOT.resolve(role+"-"+name+".png"));}
    }
    /** 只有真实绘制窗口恢复后才操作或截图，避免双窗口复用留下 1×1 占位画布。 */
    private boolean viewport(Minecraft mc) {
        long handle=mc.getWindow().getWindow();int[] w={0},h={0};GLFW.glfwGetFramebufferSize(handle,w,h);
        if(w[0]>=900&&h[0]>=650&&mc.getMainRenderTarget().width>=900&&mc.getMainRenderTarget().height>=650&&!mc.noRender){badFrames=0;return true;}
        require(++badFrames<200,"无法恢复真实窗口");GLFW.glfwShowWindow(handle);GLFW.glfwRestoreWindow(handle);mc.getWindow().setWindowed(1000,760);GLFW.glfwSetWindowSize(handle,1000,760);GLFW.glfwGetFramebufferSize(handle,w,h);
        if(w[0]>=900&&h[0]>=650){mc.getWindow().setWidth(w[0]);mc.getWindow().setHeight(h[0]);mc.noRender=false;mc.resizeDisplay();}return false;
    }
    private void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyShift.setDown(false);
        mc.options.keyJump.setDown(false);mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);
    }
    private void look(Minecraft mc,Vec3 point) {
        Vec3 d=point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
    }
    /** 只通过前进及入水跳跃靠近目标，服务器仍按普通移动包和地形碰撞结算。 */
    private boolean walk(Minecraft mc,Vec3 point) {
        if(mc.player.position().subtract(point).horizontalDistance()<.35) {mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);return true;}
        look(mc,new Vec3(point.x,mc.player.getEyeY(),point.z));mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(mc.player.isInWater());return false;
    }
    private void slot(Minecraft mc,int selected) {
        if(mc.player.getInventory().selected!=selected) {
            mc.player.getInventory().selected=selected;mc.getConnection().send(new ServerboundSetCarriedItemPacket(selected));
        }
    }
    private int inventorySlot(Minecraft mc,Item item) {
        for(int i=0;i<36;i++) if(mc.player.getInventory().items.get(i).is(item)) return i<9?36+i:i;
        throw new AssertionError("背包缺少 "+item);
    }
    private void click(Minecraft mc,int slot,int button,ClickType type) {mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);}
    private void door(Minecraft mc,NaiwaEntity frog) {
        look(mc,Vec3.atCenterOf(SurvivalCheck.ANCHOR));
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(SurvivalCheck.ANCHOR),Direction.SOUTH,SurvivalCheck.ANCHOR,false));
        if(frog!=null) look(mc,frog.getEyePosition());
    }
    private void digPacket(Minecraft mc,ServerboundPlayerActionPacket.Action action) {
        mc.getConnection().send(new ServerboundPlayerActionPacket(action,SurvivalCheck.ANCHOR,Direction.SOUTH,0));
    }
    /** 读取原版已同步的咬钩标记，只观察，不改 nibble、等待时间或渔获事件。 */
    @SuppressWarnings("unchecked")
    private boolean biting(FishingHook hook) throws Exception {
        if(biting==null) {var field=FishingHook.class.getDeclaredField("DATA_BITING");field.setAccessible(true);biting=(EntityDataAccessor<Boolean>)field.get(null);}
        return hook.getEntityData().get(biting);
    }
    private void tick() {
        if(done||++ticks%2!=0) return;var mc=Minecraft.getInstance();
        try {
            if(mc.player==null||mc.level==null||mc.getOverlay()!=null) return;
            // 玩家对象早于地形载入画面结束；此时的转头会被登录传送覆盖，不能当作已经执行的操作。
            if(mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return;
            if(!viewport(mc))return;
            MultiplayerCheck.write(role+"-connected.ok",Long.toString(ProcessHandle.current().pid()));
            String marker=Files.readString(MultiplayerCheck.ROOT.resolve("stage.txt")).trim();if(marker.isEmpty()) return;
            int phase=Integer.parseInt(marker);
            if(phase==-1) throw new AssertionError("服务端断言失败");
            if(phase==99) {MultiplayerCheck.write(role+"-result.txt",System.getProperty("naiwa.qa.focus")==null?"PASS: six pranks observed through actual TCP entity data, survival actions and item loop, natural leakage, crafting, combat totem and death/respawn.\n":"PASS focused prank: "+System.getProperty("naiwa.qa.focus")+"\n");release(mc);done=true;mc.stop();return;}
            if(phase!=last) {release(mc);last=phase;age=action=0;lastAct="";}
            age++;
            // 登录和夹具传送的确认可能晚于首帧转头；持续保持镜面视线，直到进入实际的露馅或中止步骤。
            if(actor&&(phase==200||phase==201||phase==210||phase==211)) look(mc,new Vec3(mc.player.getX(),mc.player.getEyeY(),.5));
            // 矿洞保持真实挥镐包；双窗口焦点不应替客户端决定是否松开鼠标。
            if(actor&&(phase==500||phase==501||phase==510||phase==511)) mc.player.swing(InteractionHand.MAIN_HAND);
            // temporary 是服务端存档标记，不是同步字段；本验收场地已清空常驻本体，客户端按实际可见实体查找。
            var frogs=mc.level.getEntitiesOfClass(NaiwaEntity.class,SurvivalCheck.ARENA);
            NaiwaEntity frog=frogs.isEmpty()?null:frogs.getFirst();
            var player=actor?mc.player:mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("NaiwaActor")).findFirst().orElse(null);
            if(!actor&&player!=null&&(phase==903||phase>=907&&phase<=912)) look(mc,player.getEyePosition());
            if(frog!=null) {
                String state=frog.animation().equals("laugh_sequence")?"laugh_sequence":frog.act();
                String key=phase+"-"+state;
                if(state.equals(lastAct)&&!state.isEmpty()&&recorded.add(key)) {screenshot(mc,"state-"+key);Files.writeString(MultiplayerCheck.ROOT.resolve(role+"-states.txt"),key+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
                lastAct=state;wasCrouching|=frog.isShiftKeyDown();wasJumping|=frog.getY()>81.3;
            }
            if(player!=null&&player.hasEffect(Naiwa.ASCENDING)&&player.getEffect(Naiwa.ASCENDING).getAmplifier()==2&&!hover) {
                hover=true;if(!actor) look(mc,player.getEyePosition());screenshot(mc,"combat-hover");
                require(Math.abs(player.getY()-87)<.2,"实际战斗图腾悬停六格");
            }
            if(age<5||phase==0||acknowledged()) return;
            require(mc.gameMode.getPlayerMode()==GameType.SURVIVAL&&mc.getSingleplayerServer()==null,"始终为正式专用服的生存玩家");
            boolean ready=false;
            if(phase>=200&&phase<800) {
                int kind=(phase-200)/100,step=phase%100,sub=step%10;boolean abort=step>=10;
                String expected=SurvivalCheck.KINDS[kind];
                if(sub==0) {
                    if(actor) {
                        switch(expected) {
                            case "mirror" -> {slot(mc,0);look(mc,new Vec3(.5,82.62,.5));}
                            case "door" -> {slot(mc,0);door(mc,null);}
                            case "tree","mine" -> {slot(mc,kind==2?0:2);look(mc,Vec3.atCenterOf(SurvivalCheck.ANCHOR));mc.gameMode.startDestroyBlock(SurvivalCheck.ANCHOR,Direction.SOUTH);mc.player.swing(InteractionHand.MAIN_HAND);}
                            case "fish" -> {
                                slot(mc,3);
                                if(action==0){mc.player.setYRot(180);mc.player.setXRot(20);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=1;return;}
                                // 收竿前只能观察原版浮漂；出现任何演员都应立即让本轮失败，不能等演员后才开始等咬钩。
                                if(action<3)require(frogs.isEmpty(),"等待与原版咬钩动画期间不能提前看见奶蛙");
                                if(action==1&&mc.player.fishing!=null){
                                    if(abort&&age>=15){require(!biting(mc.player.fishing),"提前收竿用例尚未咬钩");mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=3;}
                                    else if(!abort&&biting(mc.player.fishing)){action=2;reelAt=age+3;}
                                }
                                if(action==2&&age>=reelAt){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=3;}
                                if(action<3||mc.player.fishing!=null)return;
                            }
                            case "boat" -> {
                                slot(mc,0);var boats=mc.level.getEntitiesOfClass(Boat.class,SurvivalCheck.ARENA);if(boats.isEmpty()) return;
                                look(mc,boats.getFirst().position());mc.gameMode.interact(mc.player,boats.getFirst(),InteractionHand.MAIN_HAND);
                            }
                        }
                    }
                    ready=true;
                } else if(sub==1) {
                    if(frog==null) return;
                    if(kind==4)require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"双方收到出水演员真实主手附魔书和光效");
                    require(kind==4?frog.act().equals("fish_land")||frog.animation().equals("laugh_sequence"):frog.act().equals(expected),"双方收到实际触发的 "+expected+" 动作，实际="+frog.act());
                    if(actor&&kind==3) {look(mc,Vec3.atCenterOf(SurvivalCheck.ANCHOR));mc.player.swing(InteractionHand.MAIN_HAND);}
                    else look(mc,frog.getEyePosition());
                    if(actor&&kind==5&&age<18) {mc.player.setYRot(180);mc.player.setXRot(0);mc.options.keyUp.setDown(true);return;}
                    ready=true;
                } else if(sub==2&&abort) {
                    if(kind==0&&actor) {mc.player.setYRot(0);mc.player.setXRot(0);ready=true;}
                    if(kind==1&&actor) ready=walk(mc,new Vec3(.5,81,23));
                    if(kind==2&&!actor) {if(frog!=null) {look(mc,frog.getEyePosition());mc.gameMode.attack(mc.player,frog);}ready=true;}
                    if(kind==3&&actor) {mc.gameMode.stopDestroyBlock();slot(mc,1);ready=true;}
                    if(kind==4&&actor) {slot(mc,3);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);ready=true;}
                    if(kind==5&&!actor) {
                        var boats=mc.level.getEntitiesOfClass(Boat.class,SurvivalCheck.ARENA);if(!boats.isEmpty()) {look(mc,boats.getFirst().position());mc.gameMode.attack(mc.player,boats.getFirst());}ready=true;
                    }
                    if((kind==2||kind==5)?actor:!actor) ready=true;
                } else if(sub==2) {
                    if(frog==null) return;
                    if(!actor) {look(mc,frog.getEyePosition());ready=true;}
                    else switch(expected) {
                        case "mirror" -> {
                            look(mc,frog.getEyePosition());mc.options.keyShift.setDown(age<16);mc.options.keyRight.setDown(age>=7&&age<16);
                            mc.options.keyJump.setDown(age>=18&&age<21);ready=age>=32;
                        }
                        case "door" -> {
                            if(age==5||age==9||age==13||age==17) door(mc,frog);
                            else look(mc,frog.getEyePosition());ready=age>=22;
                        }
                        case "tree" -> {
                            look(mc,Vec3.atCenterOf(SurvivalCheck.ANCHOR));
                            if(action==0) {
                                // 使用原版工具破坏速度等待足额 tick，再发正常 STOP 包；不本地删除方块，也不因窗口失焦反复取消挖掘。
                                float progress=mc.level.getBlockState(SurvivalCheck.ANCHOR).getDestroyProgress(mc.player,mc.level,SurvivalCheck.ANCHOR);
                                require(progress>0,"原版斧头可以采集原木");breakAt=age+(int)Math.ceil((Math.ceil(1/progress)+4)/2);
                                digPacket(mc,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);action=1;
                            }
                            mc.player.swing(InteractionHand.MAIN_HAND);
                            if(action==1&&age>=breakAt) {digPacket(mc,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);action=2;}
                            if(mc.level.getBlockState(SurvivalCheck.ANCHOR).isAir()) {look(mc,frog.getEyePosition());ready=true;}
                        }
                        case "mine" -> {
                            if(!mc.level.getBlockState(SurvivalCheck.ANCHOR).isAir()) {look(mc,Vec3.atCenterOf(SurvivalCheck.ANCHOR));mc.gameMode.continueDestroyBlock(SurvivalCheck.ANCHOR,Direction.SOUTH);mc.player.swing(InteractionHand.MAIN_HAND);return;}
                            mc.gameMode.stopDestroyBlock();look(mc,frog.getEyePosition());ready=age>=25;
                        }
                        case "fish" -> {
                            look(mc,frog.getEyePosition());
                            require(mc.player.fishing==null,"出场后已经完成原版收竿，不能再额外点一次鱼竿");ready=true;
                        }
                        case "boat" -> {
                            if(mc.player.isPassenger()) {mc.options.keyShift.setDown(true);return;}
                            mc.options.keyShift.setDown(false);
                            if(walk(mc,new Vec3(4,81,2.5))) {look(mc,frog.getEyePosition());ready=mc.player.onGround();}
                        }
                    }
                } else if(sub==3&&!abort) {
                    if(frog==null) return;
                    require(frog.animation().equals("laugh_sequence"),"露馅后双方看到完整大笑");look(mc,frog.getEyePosition());
                    if(kind==4)require(frog.getMainHandItem().is(Items.ENCHANTED_BOOK)&&frog.getMainHandItem().hasFoil(),"双方大笑阶段仍持有附魔书");
                    if(actor&&kind>=1&&kind<=3) {
                        slot(mc,4);
                        // 单场诊断从空背包开始，没有前面门和树场景的累计奖励。
                        int expectedCount=System.getProperty("naiwa.qa.focus")==null?kind:1;
                        if(mc.player.getInventory().countItem(Naiwa.GAS.get())<expectedCount) {
                            if(mc.player.distanceToSqr(frog)>6.5) {Vec3 delta=mc.player.position().subtract(frog.position()).multiply(1,0,1).normalize();walk(mc,frog.position().add(delta.scale(2.2)));return;}
                            release(mc);look(mc,frog.getEyePosition());mc.gameMode.interact(mc.player,frog,InteractionHand.MAIN_HAND);return;
                        }
                        require(mc.player.getInventory().countItem(Naiwa.GAS.get())==expectedCount,"客户端实际收到一瓶采集奖励");
                    }
                    if(kind==0) require(wasCrouching&&wasJumping,"客户端收到镜像蹲下和起跳");
                    // 抢书场景保留两秒稳定视角，供截图核对实际手持位置，而不是只核对网络中的物品编号。
                    ready=kind!=4||age>=20;
                    if(kind==4&&age==20)screenshot(mc,"book-laugh");
                } else if(sub==4||sub==3&&abort) {require(frogs.isEmpty(),"结束或中止后双方都不残留临时演员");ready=true;}
            } else {
                switch(phase) {
                    case 900 -> {
                        if(actor) {
                            int menuSlot=inventorySlot(mc,Naiwa.GAS.get());require(menuSlot>=36,"采集笑气位于快捷栏");slot(mc,menuSlot-36);
                            var target=mc.level.getEntitiesOfClass(Husk.class,SurvivalCheck.ARENA).getFirst();look(mc,target.getEyePosition());mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                        }ready=true;
                    }
                    case 901 -> {var mob=mc.level.getEntitiesOfClass(Husk.class,SurvivalCheck.ARENA).getFirst();require(mob.hasEffect(Naiwa.LAUGHING),"采集来的瓶子实际控场并同步");ready=true;}
                    case 902 -> ready=true;
                    case 903 -> {
                        require(player!=null&&player.hasEffect(Naiwa.LAUGHING),"等待真实随机漏气后双方收到捧腹效果");
                        if(actor) {
                            if(action==0) {
                                var target=mc.level.getEntitiesOfClass(Husk.class,SurvivalCheck.ARENA).getFirst();mc.gameMode.attack(mc.player,target);
                                slot(mc,8);mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);action=1;
                            }
                            ready=mc.player.getInventory().countItem(Items.APPLE)==3;
                        }else {look(mc,player.getEyePosition());ready=true;}
                    }
                    case 904 -> {if(player==null||player.hasEffect(Naiwa.LAUGHING)) return;ready=true;}
                    case 905 -> {
                        if(!actor) {ready=true;break;}
                        // 原版 2×2 生存合成：一份笑气、一份原版图腾；通过容器点击消耗并领取结果。
                        if(craft==0) {
                            mc.setScreen(new InventoryScreen(mc.player));int gas=inventorySlot(mc,Naiwa.GAS.get()),totem=inventorySlot(mc,Items.TOTEM_OF_UNDYING);
                            click(mc,gas,0,ClickType.PICKUP);click(mc,1,1,ClickType.PICKUP);click(mc,gas,0,ClickType.PICKUP);
                            click(mc,totem,0,ClickType.PICKUP);click(mc,2,0,ClickType.PICKUP);craft=1;return;
                        }
                        if(craft==1) {if(!mc.player.containerMenu.getSlot(0).getItem().is(Naiwa.TOTEM.get())) return;click(mc,0,0,ClickType.QUICK_MOVE);craft=2;return;}
                        if(craft==2) {if(mc.player.getInventory().countItem(Naiwa.TOTEM.get())!=1) return;mc.player.closeContainer();mc.setScreen(null);craft=3;return;}
                        require(mc.player.getInventory().countItem(Items.GLASS_BOTTLE)==14&&mc.player.getInventory().countItem(Naiwa.GAS.get())==1,"背包实际返一空瓶并消耗一份材料");ready=true;
                    }
                    case 906 -> {
                        if(actor) {
                            if(action==0) {mc.setScreen(new InventoryScreen(mc.player));click(mc,inventorySlot(mc,Naiwa.TOTEM.get()),40,ClickType.SWAP);action=1;return;}
                            if(!mc.player.getOffhandItem().is(Naiwa.TOTEM.get())) return;mc.player.closeContainer();mc.setScreen(null);
                        }ready=true;
                    }
                    case 907 -> {
                        // 效果包与背包广播不在同一事务；等待双方状态到齐，不能把一帧旧物品当作复制。
                        if(player==null||!player.hasEffect(Naiwa.ASCENDING)||!player.getOffhandItem().isEmpty()) return;
                        require(player.isAlive(),"实际怪物致命攻击触发合成图腾");ready=true;
                    }
                    case 908 -> {if(!actor&&player!=null) look(mc,player.getEyePosition());ready=true;}
                    case 909 -> {require(player!=null&&!player.hasEffect(Naiwa.ASCENDING)&&!player.isNoGravity()&&hover,"实际战斗飞升结束恢复");ready=true;}
                    case 910 -> ready=true;
                    case 911 -> {if(actor&&!mc.player.isAlive()) {mc.player.respawn();return;}ready=!actor||mc.player.isAlive();}
                    case 912 -> {if(player==null||frog==null) return;require(player.isAlive()&&frog.animation().equals("laugh_sequence"),"实际战斗死亡重生后的复活点嘲笑");look(mc,frog.getEyePosition());ready=true;}
                }
            }
            if(ready) {release(mc);screenshot(mc,"phase-"+phase);MultiplayerCheck.write(role+"-"+phase+".ok","PASS");}
        }catch(Throwable failure) {
            failure.printStackTrace();done=true;try{MultiplayerCheck.write(role+"-result.txt","FAIL stage="+last+": "+failure);}catch(Exception ignored){}
            release(mc);mc.stop();
        }
    }
}
