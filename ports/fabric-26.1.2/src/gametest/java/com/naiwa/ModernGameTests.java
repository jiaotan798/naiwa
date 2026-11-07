package com.naiwa;

import net.minecraft.gametest.framework.GameTestHelper;

public final class ModernGameTests {
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="naiwa:empty",environment="naiwa-tests:basic",maxTicks=40)
    public void totemAndRecipe(GameTestHelper helper) {ModernChecks.totemAndRecipe(helper);}

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="naiwa:empty",environment="naiwa-tests:basic",maxTicks=40)
    public void realShieldBlock(GameTestHelper helper) {ModernChecks.realShieldBlock(helper);}

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure="naiwa:empty",environment="naiwa-tests:basic",maxTicks=40)
    public void gasDamageHooks(GameTestHelper helper) {ModernChecks.gasDamageHooks(helper);}
}
