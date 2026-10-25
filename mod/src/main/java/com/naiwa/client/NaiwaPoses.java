package com.naiwa.client;

import com.naiwa.Naiwa;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

/** 人形骨骼捧腹层每帧在原版动作之后覆盖，下一帧由原版重新计算，不留下姿态。 */
public final class NaiwaPoses {
    private NaiwaPoses() {}
    public static void laugh(HumanoidModel<?> model,LivingEntity entity,float time) {
        if (!entity.hasEffect(Naiwa.LAUGHING)) return;
        float shake=(float)Math.sin(time*1.8)*.06F;
        model.body.xRot=.3F+shake; model.head.xRot=.25F+shake;
        model.leftArm.xRot=-.55F; model.leftArm.yRot=-.55F; model.leftArm.zRot=.25F;
        model.rightArm.xRot=-.55F; model.rightArm.yRot=.55F; model.rightArm.zRot=-.25F;
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> player) {
            player.leftSleeve.copyFrom(model.leftArm); player.rightSleeve.copyFrom(model.rightArm); player.jacket.copyFrom(model.body);
        }
    }
}
