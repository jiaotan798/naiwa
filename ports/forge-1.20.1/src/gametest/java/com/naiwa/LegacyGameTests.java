package com.naiwa;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
@net.minecraftforge.gametest.GameTestHolder("naiwa")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class LegacyGameTests {
    @GameTest(templateNamespace="naiwa",template="empty",timeoutTicks=40)
    public static void totemAndRecipe(GameTestHelper helper) {LegacyChecks.totemAndRecipe(helper);}

    @GameTest(templateNamespace="naiwa",template="empty",timeoutTicks=40)
    public static void realShieldBlock(GameTestHelper helper) {LegacyChecks.realShieldBlock(helper);}

    @GameTest(templateNamespace="naiwa",template="empty",timeoutTicks=40)
    public static void gasDamageHooks(GameTestHelper helper) {LegacyChecks.gasDamageHooks(helper);}
}
