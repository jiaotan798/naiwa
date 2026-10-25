package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** 本体、箱中探头、盾牌头部及玩家变身共用源网格和蒙皮；实例工作数组仅在渲染线程内使用。 */
final class NaiwaModel {
    private final NaiwaMesh mesh;
    private final float[] positions, normals;
    private final ResourceLocation[] textures;
    private final boolean[] headFaces;
    private final boolean[] headVertices;
    private final Vector3f vector = new Vector3f();

    NaiwaModel() {
        try (var source = Minecraft.getInstance().getResourceManager().open(id("models/entity/naiwa.nwm"))) {
            mesh = new NaiwaMesh(source);
        } catch (java.io.IOException error) {throw new IllegalStateException("奶蛙模型加载失败", error);}
        positions = new float[mesh.vertexCount * 3]; normals = positions.clone();
        textures = new ResourceLocation[mesh.materialCount];
        for (int i=0;i<textures.length;i++) textures[i]=id("textures/entity/naiwa_"+i+".png");
        headFaces = mesh.headFaces();
        headVertices = new boolean[mesh.vertexCount];
        for (int f=0;f<mesh.faceCount;f++) {
            if (headFaces[f]) for (int c=0;c<3;c++) headVertices[mesh.corners[f*3+c]]=true;
        }
    }

    private static ResourceLocation id(String path) {return ResourceLocation.fromNamespaceAndPath("naiwa", path);}
    ResourceLocation texture() {return textures[0];}

    /** 本体以脚底为原点，盾牌头部以头顶定位；16 编辑单位为 1 格，静态切面不会随动作跳变。 */
    void draw(String clip, double seconds, PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean headOnly) {
        draw(clip,seconds,pose,buffers,light,overlay,headOnly?1:0,"",0);
    }

    void drawAct(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay,String action,float pitch) {
        draw(clip,seconds,pose,buffers,light,overlay,0,action,pitch);
    }

    /** 复用现有头部面片及待机/大笑采样，颈部放在箱沿下方；不新增探头或扶箱动作。 */
    void drawChestHead(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        draw(clip,seconds,pose,buffers,light,overlay,4,"",0);
    }

    /** 道具按蒙皮后的右手掌及新增放松手指权重定位，出水与捧腹弯腰时也须随手移动。 */
    void translateRightHand(String clip,double seconds,String action,float pitch,PoseStack pose) {
        mesh.poseAction(clip,seconds,positions,normals,action,pitch);
        float x=0,y=0,z=0,total=0;
        for(int v=0;v<mesh.vertexCount;v++) for(int j=0;j<mesh.joints[v].length;j++) {
            if(!NaiwaMesh.handBone(mesh.joints[v][j],true)) continue;
            float weight=mesh.weights[v][j];x+=positions[v*3]*weight;y+=positions[v*3+1]*weight;z+=positions[v*3+2]*weight;total+=weight;
        }
        if(total==0) throw new IllegalStateException("钓鱼道具缺少右手掌权重");
        pose.translate(x/total/16,y/total/16,z/total/16+.08);
    }

    private void draw(String clip,double seconds,PoseStack pose,MultiBufferSource buffers,int light,int overlay,int mask,String action,float pitch) {
        mesh.poseAction(clip,seconds,positions,normals,action,pitch);
        boolean headOnly=mask==1||mask==4;
        float yOffset=0;
        if(mask==4) yOffset=-26; // 与 headFaces 的原始颈部切面相同，单位为 Blockbench 编辑单位。
        else if (headOnly) {
            float top=Float.NEGATIVE_INFINITY;
            for(int v=0;v<mesh.vertexCount;v++) if(headVertices[v]) top=Math.max(top,positions[v*3+1]);
            if(!Float.isFinite(top)) throw new IllegalStateException("奶蛙模型缺少盾牌可用头部面片");
            // 以头顶定位，既消除整个人体弯腰的位移，也让张嘴、缩放时的最高点始终留在盾缘以内。
            yOffset=-top;
        }
        var transform=pose.last();
        for (int i=0;i<positions.length;i+=3) {
            vector.set(positions[i]/16,(positions[i+1]+yOffset)/16,positions[i+2]/16); transform.pose().transformPosition(vector);
            positions[i]=vector.x; positions[i+1]=vector.y; positions[i+2]=vector.z;
            vector.set(normals[i],normals[i+1],normals[i+2]); transform.normal().transform(vector).normalize();
            normals[i]=vector.x;normals[i+1]=vector.y;normals[i+2]=vector.z;
        }
        for (int material=0;material<textures.length;material++) {
            var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(textures[material]));
            for (int face=0;face<mesh.faceCount;face++) if(mesh.materials[face]==material && (!headOnly || headFaces[face])) {
                // 原版实体缓冲按四边形提交，重复第三点形成退化三角形，保留原 UV 与绕序。
                for(int corner=0;corner<4;corner++) {
                    int local=Math.min(corner,2),p=mesh.corners[face*3+local]*3,u=face*6+local*2;
                    consumer.addVertex(positions[p],positions[p+1],positions[p+2]).setColor(255,255,255,255)
                            .setUv(mesh.uv[u],mesh.uv[u+1]).setOverlay(overlay).setLight(light)
                            .setNormal(normals[p],normals[p+1],normals[p+2]);
                }
            }
        }
    }

}
