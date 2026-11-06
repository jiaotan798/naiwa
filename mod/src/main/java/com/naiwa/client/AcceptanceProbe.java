package com.naiwa.client;

import com.mojang.logging.LogUtils;
import com.naiwa.NaiwaEntity;
import com.naiwa.Naiwa;
import com.naiwa.NaiwaShield;
import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 仅独立验收实例启用：创建专用世界，调用实际交互并记录渲染截图，成品排除此类。 */
public final class AcceptanceProbe {
    private static boolean opening;
    private static boolean setupRequested;
    private static volatile boolean setupReady;
    private static int ticks;
    private static String screenshot;
    private static int bodySounds, shieldSounds;
    private static volatile java.util.UUID actorId;
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AcceptanceProbe::tick);
        NeoForge.EVENT_BUS.addListener(AcceptanceProbe::render);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity e) -> {
            if(e.getEntity() instanceof NaiwaEntity frog && e.getSound()!=null && e.getSound().value()==Naiwa.LAUGH.get())
                LogUtils.getLogger().info("NAIWA_SERVER_SOUND id={} position={} animation={} seconds={}",frog.getId(),frog.position(),frog.animation(),frog.animationSeconds(0));
        });
    }
    private static void command(String text) {Minecraft.getInstance().player.connection.sendCommand(text);}
    private static NaiwaEntity entity() {
        var level = Minecraft.getInstance().level;
        if (level != null) for (var entity : level.entitiesForRendering()) if (entity instanceof NaiwaEntity target && target.getUUID().equals(actorId)) return target;
        return null;
    }
    private static void animation(String name) {if(entity()!=null)command("naiwa animation " + entity().getUUID() + " " + name);}

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!opening && mc.screen instanceof AccessibilityOnboardingScreen) {
            mc.options.onboardingAccessibilityFinished(); mc.setScreen(new TitleScreen());
        }
        if (!opening && mc.screen instanceof TitleScreen) {
            opening = true;
            // 声音管理器在模组构造后才创建；到主菜单再监测实际播放，不能在注册探针时读取。
            mc.getSoundManager().addListener((sound,definition,range) -> {
                if(!sound.getLocation().getNamespace().equals("naiwa")) return;
                if(sound.getLocation().getPath().equals("laugh")) bodySounds++;
                if(sound.getLocation().getPath().equals("shield_laugh")) shieldSounds++;
                if(sound.getPitch()!=1) throw new IllegalStateException("奶蛙原声发生变速");
                LogUtils.getLogger().info("NAIWA_ACCEPTANCE_SOUND {} pitch={}",sound.getLocation(),sound.getPitch());
            });
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.options.showSubtitles().set(true);
            mc.options.keyUse.setDown(false); mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(4);
            var settings = new LevelSettings("Naiwa model check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            // 设置专项只需要真实世界配置，不做自然地形测试；独立平坦世界避免暂停界面时仍有生成请求，退出卸载反复等待。
            boolean configOnly=Boolean.getBoolean("naiwa.configOnly");
            mc.createWorldOpenFlows().createFreshLevel(configOnly?"NaiwaConfigCheck-"+System.currentTimeMillis():"NaiwaModelCheck", settings, new WorldOptions(741L, false, false),
                    configOnly?access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions():WorldPresets::createNormalWorldDimensions, mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (!setupRequested) {
            setupRequested = true;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                // 原世界出生点可能远离原点；先实际加载测试区块，再铺平台，避免命令因区块未加载而失败。
                var level = server.overworld();
                for (int x=-1;x<=0;x++) for (int z=-1;z<=0;z++) level.getChunk(x,z);
                var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
                // 高空平台避开原地形遮挡，用真实天空光检查角色贴图和法线。
                for (String text : new String[]{"gamerule doDaylightCycle false", "gamerule doMobSpawning false", "gamerule sendCommandFeedback false",
                        "gamerule announceAdvancements false", "time set noon", "weather clear",
                        "fill -10 200 -10 10 200 10 minecraft:white_concrete", "fill -10 201 -10 10 209 10 minecraft:air",
                        "kill @e[type=naiwa:naiwa]"})
                    server.getCommands().performPrefixedCommand(source,text);
                var actor=Naiwa.ENTITY.get().create(level);actor.setPos(.5,201,.5);actor.setNoAi(true);actor.setPersistenceRequired();
                level.addFreshEntity(actor);actorId=actor.getUUID();
                var player = server.getPlayerList().getPlayers().getFirst();
                // 专用验收存档可重复使用，必须清除上次保存的生存模式、物品和使用状态，恢复相同交互距离。
                player.stopUsingItem(); player.getInventory().clearContent(); player.setGameMode(GameType.CREATIVE);
                player.setHealth(20); player.getFoodData().setFoodLevel(20);
                player.teleportTo(level,.5,201,5.5,180,7); player.getAbilities().flying=true; player.onUpdateAbilities();
                setupReady = true;
            });
            return;
        }
        if (!setupReady || entity() == null) return;
        if (Boolean.getBoolean("naiwa.configOnly")) {configScreenCheck();return;}
        if (Boolean.getBoolean("naiwa.fullOnly") && ticks<1250) ticks=1250;
        ticks++;
        if (ticks == 5) {
            mc.options.hideGui = true; mc.options.fov().set(60); mc.options.renderDistance().set(4);
        }
        if (ticks == 20) mc.getSingleplayerServer().execute(()->{
            // 区块的实体加载晚于方块加载；专用验收存档重跑时，等待加载完成再移除旧演员。
            var level=mc.getSingleplayerServer().overworld();
            for(var existing:level.getAllEntities()) if(existing instanceof NaiwaEntity && !existing.getUUID().equals(actorId)) existing.discard();
        });
        if (ticks == 45) screenshot = "01-idle-front";
        if (ticks == 60) command("tp @s 5 201 0 90 7");
        if (ticks == 80) screenshot = "02-idle-side";
        if (ticks == 100) command("tp @s 0 201 -5 0 7");
        if (ticks == 120) screenshot = "03-idle-back";
        if (ticks == 140) command("tp @s 0 201 5 180 7");
        if (ticks == 160 && entity() != null) mc.gameMode.interact(mc.player, entity(), InteractionHand.MAIN_HAND);
        if (ticks == 195) {
            if (entity() == null || !entity().animation().equals("laugh_sequence")) throw new IllegalStateException("奶蛙空手交互未启动完整大笑");
            screenshot = "04-laugh";
        }
        if (ticks == 230) screenshot = "05-laugh-motion";
        if (ticks == 355) {
            if (entity() == null || !entity().animation().equals("auto")) throw new IllegalStateException("奶蛙实际交互未恢复自动动作");
            screenshot = "06-laugh-restored";
        }
        if (ticks == 360) animation("ascend_enter");
        if (ticks == 370) screenshot = "07-ascend-enter";
        if (ticks == 385) animation("ascend_hover");
        if (ticks == 415) screenshot = "08-ascend-hover";
        if (ticks == 435) {animation("ascend_exit");}
        if (ticks == 440) screenshot = "09-ascend-exit";
        if (ticks == 460) animation("walk");
        if (ticks == 470) screenshot = "10-walk";
        if (ticks == 490) animation("run");
        if (ticks == 500) screenshot = "11-run";
        if (ticks == 520) animation("jump");
        if (ticks == 530) screenshot = "12-jump";
        if (ticks == 560) animation("wave");
        if (ticks == 580) screenshot = "13-wave";
        if (ticks == 610) {
            animation("auto"); mc.options.hideGui=false;
            mc.getSingleplayerServer().execute(() -> {
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var shield=new ItemStack(Items.SHIELD); shield.set(DataComponents.BASE_COLOR,DyeColor.GREEN);
                shield.set(DataComponents.BANNER_PATTERNS,new BannerPatternLayers.Builder().add(
                        p.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(BannerPatterns.STRIPE_CENTER),DyeColor.YELLOW).build());
                p.setItemInHand(InteractionHand.OFF_HAND,shield); p.setGameMode(GameType.SURVIVAL);
                p.getAdvancements().award(p.getServer().getAdvancements().get(ResourceLocation.withDefaultNamespace("story/deflect_arrow")),"deflected_projectile");
            });
        }
        if (ticks == 645) screenshot="14-conversion-laugh";
        if (ticks == 810) {
            if (!mc.player.getOffhandItem().is(Naiwa.SHIELD.get())) throw new IllegalStateException("真实客户端未收到转化后的奶蛙盾");
            screenshot="15-shield-first-idle";
        }
        if (ticks == 825) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (ticks == 840) screenshot="16-shield-third-idle-pattern";
        if (ticks == 850) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);
        }
        if (ticks == 865 || ticks == 885 || ticks == 905) shieldHit();
        if (ticks == 875) {
            if (NaiwaShield.charge(mc.player.getOffhandItem())!=1) throw new IllegalStateException("真实客户端首次格挡进度没有同步");
            screenshot="17-shield-charge-one";
        }
        if (ticks == 895) screenshot="18-shield-charge-two";
        if (ticks == 910) screenshot="19-shield-full-feedback";
        if (ticks == 925) screenshot="20-shield-first-laugh";
        if (ticks == 950) {mc.options.keyUse.setDown(false); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
        if (ticks == 960) {mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);}
        if (ticks == 975 || ticks == 995 || ticks == 1015) shieldHit();
        if (ticks == 1035) screenshot="21-shield-third-laugh-pattern";
        if (ticks == 1070) mc.options.keyUse.setDown(false);
        if (ticks == 1075) screenshot="22-shield-restored";
        if (ticks == 1080) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(() -> {
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.setItemInHand(InteractionHand.MAIN_HAND,p.getOffhandItem()); p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            });
        }
        if (ticks == 1090) {mc.options.keyUse.setDown(true); mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        if (ticks == 1105 || ticks == 1125 || ticks == 1145) shieldHit();
        if (ticks == 1165) screenshot="23-shield-mainhand-laugh";
        if (ticks == 1195) mc.options.keyUse.setDown(false);
        if (ticks == 1210) {
            if (NaiwaShield.charge(mc.player.getMainHandItem())!=0 || mc.player.getMainHandItem().has(Naiwa.LAUGH_START.get()))
                throw new IllegalStateException("真实客户端大笑后未清理临时状态");
            mc.setScreen(new InventoryScreen(mc.player));
        }
        if (ticks == 1225) screenshot="24-shield-inventory-skin";
        if (ticks == 1230) mc.setScreen(null);
        if (ticks == 1250) {
            if(bodySounds!=2 || shieldSounds!=3) throw new IllegalStateException("实际声音引擎播放次数不符：本体="+bodySounds+"，盾牌="+shieldSounds);
            LogUtils.getLogger().info("NAIWA_ACCEPTANCE 完成：模型全部动作、盾牌真实转化、独立皮肤、朝外探头、原声、格挡进度与恢复");
        }
        if (ticks>1250) FullGameplayProbe.tick(ticks-1250);
    }

    private static int configStage,configAge,configOldMin,configOldMax;
    private static boolean configOldRepeat;
    private static volatile boolean configServerVerified;
    private static net.neoforged.fml.config.ModConfig configFile;

    /** 原生选项列表把控件放在条目中，递归读取实际子控件，不依赖屏幕坐标或反射内部字段。 */
    private static java.util.List<net.minecraft.client.gui.components.AbstractWidget> configWidgets(net.minecraft.client.gui.components.events.GuiEventListener root) {
        var widgets=new java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
        if(root instanceof net.minecraft.client.gui.components.AbstractWidget widget)widgets.add(widget);
        if(root instanceof net.minecraft.client.gui.components.events.ContainerEventHandler parent)for(var child:parent.children())widgets.addAll(configWidgets(child));
        return widgets;
    }
    private static net.minecraft.client.gui.components.EditBox configBox(String key) {
        String name=net.minecraft.network.chat.Component.translatable("naiwa.configuration."+key).getString();
        return configWidgets(Minecraft.getInstance().screen).stream().filter(w->w instanceof net.minecraft.client.gui.components.EditBox&&w.getMessage().getString().equals(name))
                .map(w->(net.minecraft.client.gui.components.EditBox)w).findFirst().orElseThrow();
    }
    private static void configToggle() {
        configWidgets(Minecraft.getInstance().screen).stream().filter(w->w instanceof net.minecraft.client.gui.components.CycleButton<?>).map(w->(net.minecraft.client.gui.components.CycleButton<?>)w).findFirst().orElseThrow().onPress();
    }
    private static void openConfig() {
        var mc=Minecraft.getInstance();var container=net.neoforged.fml.ModList.get().getModContainerById(Naiwa.ID).orElseThrow();
        mc.setScreen(net.neoforged.neoforge.client.gui.IConfigScreenFactory.getForMod(container.getModInfo()).orElseThrow().createScreen(container,null));
    }

    /** 在真实单人世界里编辑并保存设置；只操作原生输入框/按钮，不直接改配置值冒充界面测试。 */
    private static void configScreenCheck() {
        var mc=Minecraft.getInstance();if(++configAge>1200)throw new IllegalStateException("模组设置界面检查超时："+configStage);
        if(mc.getMainRenderTarget().width<900||mc.getMainRenderTarget().height<650) {
            long handle=mc.getWindow().getWindow();org.lwjgl.glfw.GLFW.glfwShowWindow(handle);org.lwjgl.glfw.GLFW.glfwRestoreWindow(handle);mc.getWindow().setWindowed(1000,760);
            mc.resizeDisplay();return;
        }
        try {
            if(configStage==0) {
                if(configAge<100)return;
                configOldMin=com.naiwa.NaiwaConfig.MIN_INTERVAL_SECONDS.get();configOldMax=com.naiwa.NaiwaConfig.MAX_INTERVAL_SECONDS.get();configOldRepeat=com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get();
                configFile=net.neoforged.fml.config.ModConfigs.getConfigSet(net.neoforged.fml.config.ModConfig.Type.SERVER).stream().filter(c->c.getModId().equals(Naiwa.ID)).findFirst().orElseThrow();
                openConfig();configStage=1;configAge=0;return;
            }
            if(configStage==1||configStage==4) {
                if(!(mc.screen instanceof net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen)) {
                    configWidgets(mc.screen).stream().filter(w->w instanceof net.minecraft.client.gui.components.Button&&w.active&&w.getMessage().getString().contains("整蛊设置"))
                            .map(w->(net.minecraft.client.gui.components.Button)w).findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);return;
                }
                if(configAge<10)return;
                if(configStage==1) {
                    screenshot="config-before";configStage=7;
                } else {
                    if(!configBox("prankIntervalMinSeconds").getValue().equals("7")||!configBox("prankIntervalMaxSeconds").getValue().equals("7")||!com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get())throw new IllegalStateException("重开界面未保留设置");
                    screenshot="config-reopened";configStage=5;
                }
                configAge=0;return;
            }
            if(configStage==7&&configAge>=15) {
                configBox("prankIntervalMinSeconds").setValue("7");configBox("prankIntervalMaxSeconds").setValue("7");if(!com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get())configToggle();
                configStage=2;configAge=0;return;
            }
            if(configStage==2&&configAge==5)screenshot="config-edited";
            if(configStage==2&&configAge>=15) {
                mc.screen.onClose();mc.setScreen(null);
                mc.getSingleplayerServer().execute(()->{configServerVerified=com.naiwa.NaiwaConfig.MIN_INTERVAL_SECONDS.get()==7&&com.naiwa.NaiwaConfig.MAX_INTERVAL_SECONDS.get()==7&&com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get()&&com.naiwa.NaiwaConfig.nextIntervalTicks(mc.getSingleplayerServer().overworld().random)==140;});
                configStage=3;configAge=0;return;
            }
            if(configStage==3&&configServerVerified&&configAge>=20) {
                String saved=java.nio.file.Files.readString(configFile.getFullPath());
                if(!saved.contains("prankIntervalMinSeconds = 7")||!saved.contains("prankIntervalMaxSeconds = 7")||!saved.contains("allowConsecutiveSamePrank = true"))throw new IllegalStateException("设置未真实保存到 TOML");
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("config-edited.toml"),saved);openConfig();configStage=4;configAge=0;return;
            }
            if(configStage==5&&configAge>=15) {
                configBox("prankIntervalMinSeconds").setValue(""+configOldMin);configBox("prankIntervalMaxSeconds").setValue(""+configOldMax);if(com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get()!=configOldRepeat)configToggle();
                mc.screen.onClose();mc.setScreen(null);configStage=6;configAge=0;return;
            }
            if(configStage==6&&configAge>=20) {
                if(com.naiwa.NaiwaConfig.MIN_INTERVAL_SECONDS.get()!=configOldMin||com.naiwa.NaiwaConfig.MAX_INTERVAL_SECONDS.get()!=configOldMax||com.naiwa.NaiwaConfig.ALLOW_CONSECUTIVE_SAME_PRANK.get()!=configOldRepeat)throw new IllegalStateException("测试设置未恢复");
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("config-acceptance.txt"),"PASS registered native config screen; real edit/save/reopen, server thread fixed 7 seconds + repeat, TOML persisted, original settings restored; path="+configFile.getFullPath());
                LogUtils.getLogger().info("NAIWA_CONFIG_ACCEPTANCE PASS {}",configFile.getFullPath());mc.stop();
            }
        } catch(java.io.IOException failure) {throw new IllegalStateException("模组设置文件检查失败",failure);}
    }

    /** 用真实服务端箭矢伤害驱动格挡，客户端仅操作举盾，不直接写进度或探头状态。 */
    private static void shieldHit() {
        var mc=Minecraft.getInstance(); var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
        server.execute(() -> {
            var p=server.getPlayerList().getPlayer(id); var arrow=EntityType.ARROW.create(p.level());
            arrow.setPos(p.position().add(p.getLookAngle().scale(2))); p.invulnerableTime=0;
            p.hurt(p.damageSources().arrow(arrow,null),4);
        });
    }
    private static void render(RenderFrameEvent.Post event) {
        if (screenshot == null) return;
        var mc = Minecraft.getInstance(); String name = screenshot; screenshot = null;
        var target = entity();
        if (target != null) LogUtils.getLogger().info("NAIWA_ACCEPTANCE {} sky={} block={} packedLight={} position={}", name,
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,target.blockPosition()),
                mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,target.blockPosition()),
                mc.getEntityRenderDispatcher().getRenderer(target).getPackedLightCoords(target,0), target.position());
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LogUtils.getLogger().info("NAIWA_ACCEPTANCE {}", message.getString()));
    }
}
