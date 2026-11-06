package com.naiwa.client;

/** 无图形检查五秒寿命内的连续大小和透明度边界，防止参数换算使烟雾提前消失或闪现。 */
final class GasParticleChecks {
    static void check() {
        for(boolean face:new boolean[]{false,true}) {
            require(NaiwaGasParticles.opacity(0,face)==0&&NaiwaGasParticles.opacity(100,face)==0,"首尾透明");
            require(Math.abs(NaiwaGasParticles.opacity(2,face)-(face?.8F:.38F))<.00001,"两 tick 完成淡入");
            require(Math.abs(NaiwaGasParticles.opacity(85,face)-(face?.4F:.19F))<.00001,"末三十 tick 线性淡出");
            for(float age=0;age<=100;age+=.25F)require(Float.isFinite(NaiwaGasParticles.size(age,face))&&NaiwaGasParticles.size(age,face)>0
                    &&NaiwaGasParticles.opacity(age,face)>=0&&NaiwaGasParticles.opacity(age,face)<=.8F,"分数帧数值有效");
        }
        require(Math.abs(NaiwaGasParticles.size(0,false)-.14)<.00001&&Math.abs(NaiwaGasParticles.size(24,false)-.42)<.00001,"前1.2秒烟雾长大");
        require(Math.abs(NaiwaGasParticles.size(50,true)-.12)<.00001,"表情中段轻微放大");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
