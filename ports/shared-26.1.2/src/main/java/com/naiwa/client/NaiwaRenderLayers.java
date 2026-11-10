package com.naiwa.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** 飞升角色写深度，圣光及雨尘只做叠加混合，避免透明片遮住后续世界几何。 */
final class NaiwaRenderLayers {
    // 延续原版实体裁切的 shader、采样器与深度状态，只把网格提交模式改为源三角形。
    private static final RenderPipeline BODY = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(id("pipeline/body_triangles")).withShaderDefine("ALPHA_CUTOUT",0.1F)
            .withShaderDefine("PER_FACE_LIGHTING").withSampler("Sampler1").withCull(false)
            .withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.TRIANGLES).build();
    private static final RenderPipeline FLIGHT = pipeline("flight_body")
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,true)).build();
    private static final RenderPipeline HOLY_LIGHT = pipeline("holy_light")
            .withColorTargetState(new ColorTargetState(new BlendFunction(SourceFactor.SRC_ALPHA,DestFactor.ONE)))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,false)).build();
    static final RenderType BEAM = RenderType.create("naiwa_holy_light",RenderSetup.builder(HOLY_LIGHT)
            .withTexture("Sampler0",id("textures/effect/ascension_beam.png")).sortOnUpload().createRenderSetup());

    private NaiwaRenderLayers() {}

    private static Identifier id(String path) {return Identifier.fromNamespaceAndPath("naiwa",path);}

    private static RenderPipeline.Builder pipeline(String shader) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(id("pipeline/"+shader)).withVertexShader(id("core/"+shader)).withFragmentShader(id("core/"+shader))
                .withSampler("Sampler0").withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS)
                .withCull(false);
    }

    /** 延迟队列的自定义几何只写常规实体缓冲；保留受击叠层和光照贴图。 */
    static RenderType body(Identifier texture) {
        return RenderType.create("naiwa_body_triangles",RenderSetup.builder(BODY).withTexture("Sampler0",texture)
                .useLightmap().useOverlay().affectsCrumbling().createRenderSetup());
    }

    static RenderType flight(Identifier texture) {
        return RenderType.create("naiwa_flight_body",RenderSetup.builder(FLIGHT).withTexture("Sampler0",texture).createRenderSetup());
    }
}
