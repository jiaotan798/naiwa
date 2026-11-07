package com.naiwa.client;

import net.minecraft.client.model.HumanoidModel;
/** 人形骨骼捧腹层每帧在原版动作之后覆盖，下一帧由原版重新计算，不留下姿态。 */
public final class NaiwaPoses {
    private NaiwaPoses() {}
    public static void laugh(HumanoidModel<?> model,NaiwaRenderState state,float time) {
        if (!state.naiwa$laughing()) return;
        float shake=(float)Math.sin(time*1.8)*.06F;
        model.body.xRot=.3F+shake; model.head.xRot=.25F+shake;
        model.leftArm.xRot=-.55F; model.leftArm.yRot=-.55F; model.leftArm.zRot=.25F;
        model.rightArm.xRot=-.55F; model.rightArm.yRot=.55F; model.rightArm.zRot=-.25F;
        // 帽子、袖子与外套在新版是对应骨骼的子节点，自动继承旋转。
    }
}
