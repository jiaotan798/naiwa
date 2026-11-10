package com.naiwa.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 读取 NAIWA001 的大端数值和行主序 3×4 骨矩阵并蒙皮；格式与 bake-model.mjs 同步。 */
public final class NaiwaMesh {
    public final int vertexCount, boneCount, materialCount, faceCount;
    public final float[] positions, normals, uv;
    public final int[][] joints;
    public final float[][] weights;
    public final int[] materials, corners;
    public final Map<String, Clip> clips = new LinkedHashMap<>();
    private final float[] matrix;
    private final float[] actionLeft, actionRight, actionLegs, actionHead;
    final int[] headVertices;

    /** 只接受经过采样的有限数据，损坏资源立即报错，不能静默显示缺骨或无贴图的角色。 */
    public NaiwaMesh(InputStream source) throws IOException {
        try (var in = new DataInputStream(source)) {
            if (!new String(in.readNBytes(8), StandardCharsets.US_ASCII).equals("NAIWA001")) throw new IOException("奶蛙模型版本或文件头不符");
            vertexCount = count(in, 20000); boneCount = count(in, 256); materialCount = count(in, 8);
            faceCount = count(in, 50000); int clipCount = count(in, 64);
            positions = floats(in, vertexCount * 3); normals = floats(in, vertexCount * 3);
            joints = new int[vertexCount][]; weights = new float[vertexCount][];
            for (int v = 0; v < vertexCount; v++) {
                int size = count(in, 4); joints[v] = new int[size]; weights[v] = new float[size];
                float total = 0;
                for (int j = 0; j < size; j++) {
                    joints[v][j] = index(in, boneCount); weights[v][j] = finite(in);
                    if (weights[v][j] <= 0) throw new IOException("无效奶蛙蒙皮权重");
                    total += weights[v][j];
                }
                if (Math.abs(total - 1) > .0001F) throw new IOException("奶蛙蒙皮权重未归一化");
            }
            materials = new int[faceCount]; corners = new int[faceCount * 3]; uv = new float[faceCount * 6];
            for (int f = 0; f < faceCount; f++) {
                materials[f] = index(in, materialCount);
                for (int i = 0; i < 3; i++) corners[f * 3 + i] = index(in, vertexCount);
                for (int i = 0; i < 6; i++) uv[f * 6 + i] = finite(in);
            }
            long samples = 0;
            for (int c = 0; c < clipCount; c++) {
                int nameLength = count(in, 128);
                String name = new String(in.readNBytes(nameLength), StandardCharsets.UTF_8);
                float seconds = finite(in); int loop = index(in, 2); int frames = count(in, 4000);
                if (seconds <= 0 || frames < 2 || !name.matches("[a-z_]+")) throw new IOException("奶蛙动画参数不符");
                samples += (long) frames * boneCount * 12;
                if (samples > 20000000) throw new IOException("奶蛙模型采样量过大");
                var clip = new Clip(name, seconds, loop == 1, frames, floats(in, frames * boneCount * 12));
                if (clips.put(name, clip) != null) throw new IOException("奶蛙动画名称重复");
            }
            if (in.read() != -1) throw new IOException("奶蛙模型尾部含未识别数据");
        }
        matrix = new float[boneCount * 12];
        boolean[] head=headFaces(),selected=new boolean[vertexCount];int selectedCount=0;
        for(int f=0;f<faceCount;f++) if(head[f]) for(int c=0;c<3;c++)selected[corners[f*3+c]]=true;
        for(boolean used:selected)if(used)selectedCount++;
        headVertices=new int[selectedCount];int next=0;
        for(int v=0;v<vertexCount;v++)if(selected[v])headVertices[next++]=v;
        actionLeft=new float[vertexCount];actionRight=new float[vertexCount];
        actionLegs=new float[vertexCount];actionHead=new float[vertexCount];
        // 分区只依赖源骨号和权重，加载后不变；保持原权重累加顺序，避免改变姿态的浮点结果。
        for(int v=0;v<vertexCount;v++) for(int j=0;j<joints[v].length;j++) {
            int bone=joints[v][j];float weight=weights[v][j];
            if(bone>=17&&bone<=18||handBone(bone,false)) actionLeft[v]+=weight;
            if(bone>=21&&bone<=22||handBone(bone,true)) actionRight[v]+=weight;
            // 36/37 是追加手指骨，不能归入腿部；换模型时需重新核对这些分区。
            if(bone>=26&&bone<=35) actionLegs[v]+=weight;
            // 高模在原骨号后追加 38～51 号脸骨；程序俯仰需带动完全绑定到这些骨的眼睑、虹膜、牙齿和下颌。
            if((bone>=4&&bone<=15)||(bone>=38&&bone<=51)) actionHead[v]+=weight;
        }
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int value = in.readInt(); if (value < 1 || value > max) throw new IOException("奶蛙模型数据长度越界"); return value;
    }
    private static int index(DataInputStream in, int size) throws IOException {
        int value = in.readInt(); if (value < 0 || value >= size) throw new IOException("奶蛙模型索引越界"); return value;
    }
    private static float finite(DataInputStream in) throws IOException {
        float value = in.readFloat(); if (!Float.isFinite(value)) throw new IOException("奶蛙模型包含非有限数值"); return value;
    }
    private static float[] floats(DataInputStream in, int length) throws IOException {
        float[] values = new float[length]; for (int i = 0; i < length; i++) values[i] = finite(in); return values;
    }

    /** 按原始颈部切面选出盾牌与箱中探头共用的头部，不能只按头骨权重筛选，否则会漏掉绑定颈骨的后脑。 */
    public boolean[] headFaces() {
        boolean[] selected = new boolean[faceCount];
        for (int f=0;f<faceCount;f++) {
            selected[f] = true;
            // 26 是当前源模型的颈部高度，单位为 Blockbench 编辑单位；换模型时须重新核对切面。
            for (int c=0;c<3;c++) if (positions[corners[f*3+c]*3+1] < 26) selected[f] = false;
        }
        return selected;
    }

    /** 加载时按材质分组，保留各材质内原始面序；头部仍使用同一静态颈部切面。 */
    int[][] facesByMaterial(boolean headOnly) {
        boolean[] head = headOnly ? headFaces() : null;
        int[][] groups = new int[materialCount][];
        int[] counts = new int[materialCount];
        for (int f=0;f<faceCount;f++) if (!headOnly || head[f]) counts[materials[f]]++;
        for (int m=0;m<materialCount;m++) groups[m]=new int[counts[m]];
        java.util.Arrays.fill(counts,0);
        for (int f=0;f<faceCount;f++) if (!headOnly || head[f]) groups[materials[f]][counts[materials[f]]++]=f;
        return groups;
    }

    /** 从尚未转换到渲染坐标的姿态取右手加权中心，结果单位为格，含原有书本向外偏移。 */
    void rightHandPosition(float[] posed, double[] result) {
        float x=0,y=0,z=0,total=0;
        for(int v=0;v<vertexCount;v++) for(int j=0;j<joints[v].length;j++) {
            if(!handBone(joints[v][j],true)) continue;
            float weight=weights[v][j];x+=posed[v*3]*weight;y+=posed[v*3+1]*weight;z+=posed[v*3+2]*weight;total+=weight;
        }
        if(total==0) throw new IllegalStateException("钓鱼道具缺少右手掌权重");
        result[0]=x/total/16;result[1]=y/total/16;result[2]=z/total/16+.08;
    }

    /** 50 Hz 原生矩阵之间插值，每个顶点按源文件保留的 1～4 个骨骼权重变形；输出仍用 Blockbench 单位。 */
    public void pose(String name, double seconds, float[] target, float[] targetNormals) {
        pose(name,seconds,target,targetNormals,null);
    }

    /** 头部路径仅处理切面实际引用的顶点，仍使用完整骨矩阵与原权重，保留颈部驱动的后脑。 */
    private void pose(String name,double seconds,float[] target,float[] targetNormals,int[] selected) {
        Clip clip = clips.get(name);
        if (clip == null) throw new IllegalArgumentException("奶蛙模型缺少动画：" + name);
        double time = clip.loop ? Math.max(0, seconds) % clip.seconds : Math.clamp(seconds, 0, clip.seconds);
        double frame = time / clip.seconds * (clip.frames - 1);
        int first = Math.min((int) frame, clip.frames - 2), stride = matrix.length;
        float alpha = (float) (frame - first);
        for (int i = 0; i < stride; i++) {
            float a = clip.matrices[first * stride + i], b = clip.matrices[(first + 1) * stride + i];
            matrix[i] = a + (b - a) * alpha;
        }
        for (int index=0;index<(selected==null?vertexCount:selected.length);index++) {
            int v=selected==null?index:selected[index];
            int p = v * 3;
            float x = positions[p], y = positions[p+1], z = positions[p+2];
            float a=0,b=0,c=0,d=0,e=0,f=0,g=0,h=0,i=0,tx=0,ty=0,tz=0;
            for (int j = 0; j < joints[v].length; j++) {
                int k = joints[v][j] * 12; float w = weights[v][j];
                a+=matrix[k]*w; b+=matrix[k+1]*w; c+=matrix[k+2]*w; tx+=matrix[k+3]*w;
                d+=matrix[k+4]*w; e+=matrix[k+5]*w; f+=matrix[k+6]*w; ty+=matrix[k+7]*w;
                g+=matrix[k+8]*w; h+=matrix[k+9]*w; i+=matrix[k+10]*w; tz+=matrix[k+11]*w;
            }
            target[p]=a*x+b*y+c*z+tx; target[p+1]=d*x+e*y+f*z+ty; target[p+2]=g*x+h*y+i*z+tz;
            // 表情骨含缩放；法线用混合矩阵的逆转置，不能当普通位置向量处理。
            float nx=normals[p],ny=normals[p+1],nz=normals[p+2];
            float px=(e*i-f*h)*nx+(f*g-d*i)*ny+(d*h-e*g)*nz;
            float py=(c*h-b*i)*nx+(a*i-c*g)*ny+(b*g-a*h)*nz;
            float pz=(b*f-c*e)*nx+(c*d-a*f)*ny+(a*e-b*d)*nz;
            float length=(float)Math.sqrt(px*px+py*py+pz*pz);
            if (length < 1E-8F) {px=nx;py=ny;pz=nz;length=1;}
            targetNormals[p]=px/length;targetNormals[p+1]=py/length;targetNormals[p+2]=pz/length;
        }
    }

    /** 手掌含追加的放松手指骨；前 36 根骨序不变，36/37 分别跟随左右手，换模型需同步核对。 */
    static boolean handBone(int bone,boolean right) {return bone==(right?23:19)||bone==(right?37:36);}

    /** 原生采样之外的整蛊姿势层；复用加载时的骨骼分区，每次只计算一次公共动作参数。 */
    public void poseAction(String name,double seconds,float[] target,float[] targetNormals,String action,float pitch) {
        poseAction(name,seconds,target,targetNormals,action,pitch,null);
    }

    void poseHeadAction(String name,double seconds,float[] target,float[] targetNormals,String action,float pitch) {
        poseAction(name,seconds,target,targetNormals,action,pitch,headVertices);
    }

    private void poseAction(String name,double seconds,float[] target,float[] targetNormals,String action,float pitch,int[] selected) {
        pose(name,seconds,target,targetNormals,selected);
        if (action.isEmpty()) return;
        float arms=switch(action) {
            case "tree","tree_exposed","fish" -> -1.2F;
            case "mine" -> (float)(-1.1+Math.sin(seconds*7)*.55);
            case "mine_hide" -> .8F;
            case "boat" -> (float)(-.8+Math.sin(seconds*4)*.4);
            case "door_giggle" -> -.7F;
            case "diamond_point" -> -1.3F;
            case "scratch" -> -2.6F+(float)Math.sin(seconds*10)*.12F;
            default -> 0;
        };
        boolean rightOnly=action.startsWith("mine")||action.equals("diamond_point")||action.equals("scratch");
        boolean bendLegs=action.equals("fish_land")||action.equals("boat");
        boolean turnHead=action.equals("mirror")||action.startsWith("mine")||action.equals("scope_close");
        float headAngle=(float)Math.toRadians(pitch);
        for(int index=0;index<(selected==null?vertexCount:selected.length);index++) {
            int v=selected==null?index:selected[index];
            float angle=arms*(rightOnly?actionRight[v]:actionLeft[v]+actionRight[v]);
            if(angle!=0) rotateX(target,targetNormals,v,23,0,angle);
            if(bendLegs) rotateX(target,targetNormals,v,12,0,-1.3F*actionLegs[v]);
            // 挖掘跟随方块俯仰；望远镜随玩家视线反向抬头，让整张脸朝镜头；复用已有骨骼。
            if(turnHead) rotateX(target,targetNormals,v,27,0,headAngle*actionHead[v]);
        }
    }

    private static void rotateX(float[] points,float[] normals,int v,float y,float z,float angle) {
        int i=v*3; float c=(float)Math.cos(angle),s=(float)Math.sin(angle),dy=points[i+1]-y,dz=points[i+2]-z;
        points[i+1]=y+dy*c-dz*s; points[i+2]=z+dy*s+dz*c;
        float ny=normals[i+1],nz=normals[i+2];normals[i+1]=ny*c-nz*s;normals[i+2]=ny*s+nz*c;
    }

    public record Clip(String name, float seconds, boolean loop, int frames, float[] matrices) {}
}
