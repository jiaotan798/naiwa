package com.naiwa.client;

/** 实体效果在提取阶段复制给新版渲染状态；提交阶段无需回查实体。 */
public interface NaiwaRenderState {
    void naiwa$setEffects(boolean laughing,int stage,int remaining,float partial);
    boolean naiwa$laughing();
    int naiwa$stage();
    int naiwa$remaining();
    float naiwa$partial();
}
