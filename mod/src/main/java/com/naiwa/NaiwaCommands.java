package com.naiwa;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** 模型验收入口只控制已有奶蛙的动作，不修改玩家、不生成物品或执行图腾保命。 */
public final class NaiwaCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("naiwa").requires(source -> source.hasPermission(2))
                .then(Commands.literal("animation").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("clip", StringArgumentType.word()).suggests((context, builder) -> {
                            var names = new java.util.ArrayList<>(NaiwaAnimations.NAMES); names.add("auto");
                            return SharedSuggestionProvider.suggest(names, builder);
                        }).executes(context -> {
                            String name = StringArgumentType.getString(context, "clip");
                            if (!name.equals("auto") && !NaiwaAnimations.NAMES.contains(name)) {
                                context.getSource().sendFailure(Component.literal("未知动画；可用值：auto, " + String.join(", ", NaiwaAnimations.NAMES)));
                                return 0;
                            }
                            int count = 0;
                            for (var entity : EntityArgument.getEntities(context, "targets")) {
                                if (entity instanceof NaiwaEntity target) { target.play(name); count++; }
                            }
                            int changed = count;
                            context.getSource().sendSuccess(() -> Component.literal("已为 " + changed + " 只奶蛙播放 " + name), true);
                            return count;
                        })))));
    }
}
