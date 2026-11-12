package com.naiwa;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
public final class LegacyGameTests {
    @GameTest(template="naiwa:empty",timeoutTicks=40)
    public static void raycastContext(GameTestHelper helper) {LegacyChecks.raycastContext(helper);}

    @GameTest(template="naiwa:empty",timeoutTicks=40)
    public static void totemAndRecipe(GameTestHelper helper) {LegacyChecks.totemAndRecipe(helper);}

    @GameTest(template="naiwa:empty",timeoutTicks=40)
    public static void realShieldBlock(GameTestHelper helper) {LegacyChecks.realShieldBlock(helper);}

    @GameTest(template="naiwa:empty",timeoutTicks=40)
    public static void gasDamageHooks(GameTestHelper helper) {LegacyChecks.gasDamageHooks(helper);}
}
