package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;

/** 本体、箱中探头、盾牌头部及玩家变身共用源网格和蒙皮；实例工作数组仅在渲染线程内使用。 */
final class NaiwaModel {
    // 与圣光共用非实体格式；受光在提交前计算，避免光影对实体法线、切线及附加属性的重写。
    static final VertexFormat FLIGHT_FORMAT=DefaultVertexFormat.POSITION_TEX_COLOR;
    private final NaiwaMesh mesh;
    private final float[] positions, normals;
    private final ResourceLocation[] textures;
    private final int[][] materialFaces, headMaterialFaces;
    private final double[] rightHand = new double[3];
    private final Vector3f vector = new Vector3f();
    private final float[] flightLight;
    private final Vector3f viewPoint=new Vector3f(),viewNormal=new Vector3f();
    private final RenderType[] flightTypes;
    private static ShaderInstance flightShader;

    NaiwaModel() {
        try (var source = Minecraft.getInstance().getResourceManager().open(id("models/entity/naiwa.nwm"))) {
            mesh = new NaiwaMesh(source);
        } catch (java.io.IOException error) {throw new IllegalStateException("奶蛙模型加载失败", error);}
        positions = new float[mesh.vertexCount * 3]; normals = positions.clone();
        flightLight=new float[mesh.vertexCount*3];flightTypes=new RenderType[mesh.materialCount];
        textures = new ResourceLocation[mesh.materialCount];
        for (int i=0;i<textures.length;i++) {textures[i]=id("textures/entity/naiwa_"+i+".png");flightTypes[i]=new FlightLayer(textures[i]);}
        materialFaces = mesh.facesByMaterial(false);
        headMaterialFaces = mesh.facesByMaterial(true);
    }

    private static ResourceLocation id(String path) {return new ResourceLocation("naiwa", path);}
    ResourceLocation texture() {return textures[0];}

    /** 本体以脚底为原点，盾牌头部以头顶定位；16 编辑单位为 1 格，静态切面不会随动作跳变。 */
    void draw(String clip, double seconds, PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean headOnly) {
        draw(clip,seconds,pose,buffers,light,overlay,headOnly?1:0,"",0,false,-1);
    }

    /** 持书时在同一次蒙皮中保存手掌坐标，随后道具绘制无需重算全身姿态。 */
    void drawAct(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay,String action,float pitch,boolean holdingBook) {
        draw(clip,seconds,pose,buffers,light,overlay,0,action,pitch,holdingBook,-1);
    }

    /** 复用现有头部面片及待机/大笑采样，颈部放在箱沿下方；不新增探头或扶箱动作。 */
    void drawChestHead(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay,String action,float pitch,boolean holdingBook) {
        draw(clip,seconds,pose,buffers,light,overlay,4,action,pitch,holdingBook,-1);
    }

    /** 世界完成后独立绘制飞升角色，避免光影重写实体材质；纹理和蒙皮仍复用本体资源。 */
    void drawFlight(String clip,double seconds,PoseStack pose,MultiBufferSource.BufferSource buffers,float reveal) {
        draw(clip,seconds,pose,buffers,0,0,0,"",0,false,reveal);
        for(RenderType type:flightTypes)buffers.endBatch(type);
    }

    static void shaders(NaiwaAscensionVisuals.ShaderRegistration registrar)throws java.io.IOException {
        registrar.register(id("flight_body"),FLIGHT_FORMAT,loaded->flightShader=loaded);
    }

    /** 仅紧随本演员 holdingBook=true 的绘制调用；下一演员会覆盖缓存，不跨帧复用。 */
    void translateRightHand(PoseStack pose) {
        pose.translate(rightHand[0],rightHand[1],rightHand[2]);
    }

    private void draw(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay,int mask,String action,float pitch,boolean holdingBook,float flightReveal) {
        boolean headOnly=mask==1||mask==4;
        // 头部演员若被外部赋予书本道具仍需完整手掌定位，保留全身计算作为该情况的兼容路径。
        if(headOnly&&!holdingBook)mesh.poseHeadAction(clip,seconds,positions,normals,action,pitch);
        else mesh.poseAction(clip,seconds,positions,normals,action,pitch);
        // draw 后半段会原位变换工作数组，手掌必须在此处保留模型坐标。
        if(holdingBook) mesh.rightHandPosition(positions,rightHand);
        float yOffset=0;
        if(mask==4) yOffset=-26; // 与 headFaces 的原始颈部切面相同，单位为 Blockbench 编辑单位。
        else if (headOnly) {
            float top=Float.NEGATIVE_INFINITY;
            for(int v:mesh.headVertices)top=Math.max(top,positions[v*3+1]);
            if(!Float.isFinite(top)) throw new IllegalStateException("奶蛙模型缺少盾牌可用头部面片");
            // 以头顶定位，既消除整个人体弯腰的位移，也让张嘴、缩放时的最高点始终留在盾缘以内。
            yOffset=-top;
        }
        var transform=pose.last();
        var view=com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
        for (int index=0;index<(headOnly?mesh.headVertices.length:mesh.vertexCount);index++) {
            int i=(headOnly?mesh.headVertices[index]:index)*3;
            float incidence=flightReveal>=0?NaiwaAscensionShapes.incidence(normals[i],normals[i+1],normals[i+2]):0;
            vector.set(positions[i]/16,(positions[i+1]+yOffset)/16,positions[i+2]/16); transform.pose().transformPosition(vector);
            positions[i]=vector.x; positions[i+1]=vector.y; positions[i+2]=vector.z;
            vector.set(normals[i],normals[i+1],normals[i+2]); transform.normal().transform(vector).normalize();
            normals[i]=vector.x;normals[i+1]=vector.y;normals[i+2]=vector.z;
            if(flightReveal>=0) {
                viewPoint.set(positions[i],positions[i+1],positions[i+2]);view.transformPosition(viewPoint);
                viewNormal.set(vector);view.transformDirection(viewNormal).normalize();
                float facing=-viewNormal.dot(viewPoint)/Math.max(viewPoint.length(),.00001F);
                NaiwaAscensionShapes.light(incidence,facing,flightReveal,flightLight,i);
            }
        }
        for (int material=0;material<textures.length;material++) {
            var consumer=buffers.getBuffer(flightReveal>=0?flightTypes[material]:RenderType.entityCutoutNoCull(textures[material]));
            for (int face : (headOnly ? headMaterialFaces : materialFaces)[material]) {
                // 原版实体缓冲按四边形提交，重复第三点形成退化三角形，保留原 UV 与绕序。
                for(int corner=0;corner<4;corner++) {
                    int local=Math.min(corner,2),p=mesh.corners[face*3+local]*3,u=face*6+local*2;
                    consumer.vertex(positions[p],positions[p+1],positions[p+2]);
                    if(flightReveal>=0)consumer.color(flightLight[p]/3,flightLight[p+1]/3,flightLight[p+2]/3,1);
                    else consumer.color(255,255,255,255);
                    consumer.uv(mesh.uv[u],mesh.uv[u+1]);
                    if(flightReveal<0)consumer.overlayCoords(overlay).uv2(light);
                    if(flightReveal<0)consumer.normal(normals[p],normals[p+1],normals[p+2]);
                    consumer.endVertex();
                }
            }
        }
    }

    /** 独立阶段直接使用自有着色器并写入角色深度，后续雨丝和光尘仍能正确被身体遮挡。 */
    private static final class FlightLayer extends RenderType {
        private static final ShaderStateShard PROGRAM=new ShaderStateShard(()->flightShader);
        private FlightLayer(ResourceLocation texture) {
            this(new TextureStateShard(texture,false,false));
        }
        private FlightLayer(TextureStateShard texture) {
            super("naiwa_flight_body",FLIGHT_FORMAT,VertexFormat.Mode.QUADS,1536,false,false,
                    ()->{PROGRAM.setupRenderState();texture.setupRenderState();LEQUAL_DEPTH_TEST.setupRenderState();NO_CULL.setupRenderState();NO_TRANSPARENCY.setupRenderState();COLOR_DEPTH_WRITE.setupRenderState();},
                    ()->{COLOR_DEPTH_WRITE.clearRenderState();NO_TRANSPARENCY.clearRenderState();NO_CULL.clearRenderState();LEQUAL_DEPTH_TEST.clearRenderState();texture.clearRenderState();PROGRAM.clearRenderState();});
        }
    }

}
