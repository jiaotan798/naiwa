package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;

/** 独立盾面与把手沿用原版尺寸，UV 对应生成原图；不放入方块图集，避免原图的奇数宽度降低全局 mipmap。 */
final class NaiwaShieldSkin {
    static final int WIDTH = 1269, HEIGHT = 1240;
    // 原版会自动扫描 textures/item；独立绑定仍须避开该目录，才能不进入方块图集。
    private static final ResourceLocation TEXTURE = new ResourceLocation("naiwa", "textures/entity/naiwa_shield.png");
    static final List<Face> FACES = createFaces();

    private static List<Face> createFaces() {
        var faces = new ArrayList<Face>();
        // 生成图保留原文件，不强行套原版 UV；坐标单位为源图像素，取不透明矩形内部以避开边缘透明噪点。
        float[] front={12,44,278,510}, back={296,44,563,510}, edge={586,44,622,298};
        float[] handle={816,129,860,230}, handleSide={690,66,904,106};
        box(faces,-6,-11,1,6,11,2,front,back,edge);
        box(faces,-1,-3,-5,1,3,1,handle,handle,handleSide);
        return List.copyOf(faces);
    }

    /** 正面统一是局部 +Z，与奶蛙原网格朝向一致；坐标保持每 16 编辑单位一格。 */
    private static void box(List<Face> faces,float x0,float y0,float z0,float x1,float y1,float z1,float[] front,float[] back,float[] edge) {
        faces.add(new Face(new float[]{x0,y1,z1,x0,y0,z1,x1,y0,z1,x1,y1,z1},front,0,0,1));
        faces.add(new Face(new float[]{x1,y1,z0,x1,y0,z0,x0,y0,z0,x0,y1,z0},back,0,0,-1));
        faces.add(new Face(new float[]{x1,y1,z1,x1,y0,z1,x1,y0,z0,x1,y1,z0},edge,1,0,0));
        faces.add(new Face(new float[]{x0,y1,z0,x0,y0,z0,x0,y0,z1,x0,y1,z1},edge,-1,0,0));
        faces.add(new Face(new float[]{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0},edge,0,1,0));
        faces.add(new Face(new float[]{x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1},edge,0,-1,0));
    }

    /** 使用实体纹理缓冲保留光照及附魔闪光；普通盾和世界方块图集均不受影响。 */
    static void draw(boolean foil,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var consumer=ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(TEXTURE),true,foil);
        for(Face face:FACES) for(int corner=0;corner<4;corner++) {
            int p=corner*3;
            float u=face.uv[corner<2?0:2]/WIDTH, v=face.uv[corner==0||corner==3?1:3]/HEIGHT;
            consumer.vertex(pose.last().pose(),face.xyz[p]/16,face.xyz[p+1]/16,face.xyz[p+2]/16)
                    .color(255,255,255,255).uv(u,v).overlayCoords(overlay).uv2(light)
                    .normal(pose.last().normal(),face.nx,face.ny,face.nz).endVertex();
        }
    }

    record Face(float[] xyz,float[] uv,float nx,float ny,float nz) {}
}
