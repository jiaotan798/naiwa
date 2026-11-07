package com.naiwa;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** 只在专服 GameTest 构建中装入，由 NeoForge 模组事件注册函数和夹具。 */
@EventBusSubscriber(modid=Naiwa.ID)
public final class ModernGameTests {
    private static final Map<String,Consumer<GameTestHelper>> TESTS = Map.of(
            "totem_and_recipe",ModernChecks::totemAndRecipe,
            "real_shield_block",ModernChecks::realShieldBlock,
            "gas_damage_hooks",ModernChecks::gasDamageHooks);

    private static Identifier id(String path) {return Identifier.fromNamespaceAndPath(Naiwa.ID,path);}

    @SubscribeEvent
    public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION,helper -> TESTS.forEach((name,test) -> helper.register(id(name),test)));
    }

    @SubscribeEvent
    public static void tests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("basic"),new TestEnvironmentDefinition.AllOf(List.of()));
        for (String name : TESTS.keySet()) {
            event.registerTest(id(name),new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION,id(name)),
                    new TestData<>(environment,id("empty"),40,0,true)));
        }
    }
}
