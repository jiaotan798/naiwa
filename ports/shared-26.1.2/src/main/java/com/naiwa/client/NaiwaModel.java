package com.naiwa.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** 为新版延迟渲染队列准备独立顶点快照，源网格、骨骼采样及材质顺序与 1.21.1 共用。 */
final class NaiwaModel {
    private final NaiwaMesh mesh;
    private final int[][] materialFaces, headFaces;
    private final RenderType[] types, flightTypes;
    private final float[] positions, normals;
    private final double[] rightHand = new double[3];

    NaiwaModel() {
        this(null);
    }

    /** 两档沿用同一批材质缓冲，避免延迟队列按网格精度拆分实体批次。 */
    NaiwaModel(NaiwaModel fullDetail) {
        String name = fullDetail == null ? "naiwa" : "naiwa_lod";
        try (var source = Minecraft.getInstance().getResourceManager().open(id("models/entity/" + name + ".nwm"))) {
            mesh = new NaiwaMesh(source);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("奶蛙模型加载失败：" + name,error);
        }
        materialFaces = mesh.facesByMaterial(false);
        headFaces = mesh.facesByMaterial(true);
        positions = new float[mesh.vertexCount*3];
        normals = positions.clone();
        if (fullDetail == null) {
            types = new RenderType[mesh.materialCount];
            flightTypes = new RenderType[types.length];
            for (int i=0;i<types.length;i++) {
                var texture = id("textures/entity/naiwa_"+i+".png");
                types[i] = NaiwaRenderLayers.body(texture);
                flightTypes[i] = NaiwaRenderLayers.flight(texture);
            }
        } else {
            if (mesh.materialCount != fullDetail.mesh.materialCount) throw new IllegalStateException("奶蛙两档模型材质数不一致");
            types = fullDetail.types;
            flightTypes = fullDetail.flightTypes;
        }
    }

    private static Identifier id(String path) {return Identifier.fromNamespaceAndPath("naiwa",path);}

    /** 箱内探头与盾牌只提交头部；箱盖用颈部切面，盾头以最高点锚定。 */
    void submit(String clip,double seconds,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,
                int mask,String action,float pitch,boolean holdingBook,float flightReveal) {
        boolean head = mask==1 || mask==4;
        if (head && !holdingBook) mesh.poseHeadAction(clip,seconds,positions,normals,action,pitch);
        else mesh.poseAction(clip,seconds,positions,normals,action,pitch);
        if (holdingBook) mesh.rightHandPosition(positions,rightHand);

        float top = Float.NEGATIVE_INFINITY;
        if (mask==1) for (int vertex : mesh.headVertices) top = Math.max(top,positions[vertex*3+1]);
        float offset = mask==4 ? -26 : mask==1 ? -top : 0;
        var sampledPositions = positions.clone();
        var sampledNormals = normals.clone();
        var faces = head ? headFaces : materialFaces;
        for (int material=0;material<faces.length;material++) {
            if (faces[material].length==0) continue;
            var indices = faces[material];
            var type = flightReveal>=0 ? flightTypes[material] : types[material];
            collector.submitCustomGeometry(pose,type,(transform,vertices) ->
                    emit(vertices,transform,indices,sampledPositions,sampledNormals,offset,light,overlay,flightReveal));
        }
    }

    /** 道具紧跟本次采样的位置提交，随后不保留实体或渲染状态引用。 */
    void translateRightHand(PoseStack pose) {pose.translate(rightHand[0],rightHand[1],rightHand[2]);}

    private void emit(VertexConsumer out,PoseStack.Pose transform,int[] faces,float[] positions,float[] normals,
                      float offset,int light,int overlay,float reveal) {
        var point = new Vector3f();
        var normal = new Vector3f();
        float[] color = reveal>=0 ? new float[3] : null;
        var view = com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix();
        // 队列的普通实体管线按三角形取顶点；飞升材质继续使用四边形。
        int corners = color==null ? 3 : 4;
        for (int face : faces) {
            for (int corner=0;corner<corners;corner++) {
                int local = Math.min(corner,2), p = mesh.corners[face*3+local]*3, u = face*6+local*2;
                float x = positions[p]/16, y = (positions[p+1]+offset)/16, z = positions[p+2]/16;
                var vertex = out.addVertex(transform,x,y,z).setUv(mesh.uv[u],mesh.uv[u+1]);
                if (color == null) {
                    vertex.setColor(-1).setOverlay(overlay).setLight(light).setNormal(transform,normals[p],normals[p+1],normals[p+2]);
                } else {
                    transform.pose().transformPosition(point.set(x,y,z));
                    view.transformPosition(point);
                    transform.normal().transform(normal.set(normals[p],normals[p+1],normals[p+2]));
                    view.transformDirection(normal).normalize();
                    float facing = -normal.dot(point)/Math.max(point.length(),.00001F);
                    float incidence = NaiwaAscensionShapes.incidence(normals[p],normals[p+1],normals[p+2]);
                    NaiwaAscensionShapes.light(incidence,facing,reveal,color,0);
                    vertex.setColor(color[0]/3,color[1]/3,color[2]/3,1);
                }
            }
        }
    }
}
