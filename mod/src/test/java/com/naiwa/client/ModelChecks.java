package com.naiwa.client;

import com.google.gson.JsonParser;
import com.naiwa.NaiwaAnimations;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** 与原生编辑器实际蒙皮对照，并检查单次/循环播放和损坏资源；不代替游戏截图。 */
public final class ModelChecks {
    public static void main(String[] args) throws Exception {
        totemCameraChecks();
        AscensionVisualChecks.check();
        GasParticleChecks.check();
        byte[] bytes = Files.readAllBytes(Path.of("../assets/runtime/assets/naiwa/models/entity/naiwa.nwm"));
        var model = new NaiwaMesh(new ByteArrayInputStream(bytes));
        assert model.vertexCount == 3580 && model.boneCount == 38 && model.faceCount == 6997 && model.materialCount == 6;
        var referenced=new boolean[model.vertexCount];for(int vertex:model.corners)referenced[vertex]=true;
        int unused=0;for(boolean used:referenced)if(!used)unused++;
        assert unused==31 : "精修模型的无面顶点须保留，不能重排原生姿态对照的顶点索引";
        // 精修脸部使用六种材质，包含透明的旧眨眼遮片；不能只打包原来的两张贴图。
        int[][] sizes={{2048,2048},{512,256},{512,512},{1024,512},{2048,2048},{8,8}};
        for(int material=0;material<sizes.length;material++) {
            var image=ImageIO.read(Path.of("../assets/runtime/assets/naiwa/textures/entity/naiwa_"+material+".png").toFile());
            assert image.getWidth()==sizes[material][0]&&image.getHeight()==sizes[material][1] : "精修脸部贴图尺寸不符："+material;
            if(material==5)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)assert(image.getRGB(x,y)>>>24)==0 : "旧眨眼遮片必须透明，不能露出黑片";
        }
        assert model.clips.keySet().equals(new java.util.LinkedHashSet<>(NaiwaAnimations.NAMES));
        renderPreparationChecks(model);
        actionPrecomputationChecks(model);
        // 后脑由颈骨带动，必须和脸一并保留；仅按头骨权重选面时，后界达不到 -4。
        var head = model.headFaces();
        int headCount=0; float rear=Float.POSITIVE_INFINITY, front=Float.NEGATIVE_INFINITY;
        for(int f=0;f<model.faceCount;f++) if(head[f]) {
            headCount++;
            for(int c=0;c<3;c++) {
                float z=model.positions[model.corners[f*3+c]*3+2];
                rear=Math.min(rear,z); front=Math.max(front,z);
            }
        }
        // 圆脸源文件的嘴部前界约 4.261；连同颈部切面及后脑核对，防止误打包上一版面部。
        assert headCount==1654 && rear < -4 && front > 4.25 && front < 4.28 : "盾牌头部缺少后脑或圆脸面部";
        float[] positions = model.positions.clone(), normals = model.normals.clone();
        float[] other = positions.clone(), otherNormals = normals.clone();
        for (var clip : model.clips.values()) {
            assert Math.abs(clip.seconds() - NaiwaAnimations.length(clip.name())) < 1E-6;
            assert clip.loop() == NaiwaAnimations.loops(clip.name());
            model.pose(clip.name(), clip.seconds() * .4, positions, normals);
            for (int i=0;i<positions.length;i+=3) {
                assert Float.isFinite(positions[i]) && Float.isFinite(positions[i+1]) && Float.isFinite(positions[i+2]);
                assert Math.abs(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2]-1) < .0001;
            }
            double first = clip.loop() ? 0 : clip.seconds();
            model.pose(clip.name(), first, positions, normals);
            model.pose(clip.name(), clip.seconds() * 2.0, other, otherNormals);
            assert distance(positions, other) < .0001 : "单次/循环动画末端处理不符：" + clip.name();
        }
        var checks = JsonParser.parseString(Files.readString(Path.of("../assets/runtime/native-pose-checks.json"))).getAsJsonObject();
        fingerActionChecks(model);
        for(String action:new String[]{"mirror","door_giggle","tree","tree_exposed","mine","mine_hide","fish","fish_land","boat","creeper_wait","scratch","diamond_point","diamond_caught","chest_peek","scope_close"}) {
            model.poseAction("idle",.5,positions,normals,action,30);
            for(int i=0;i<positions.length;i+=3) {
                assert Float.isFinite(positions[i]) && Float.isFinite(positions[i+1]) && Float.isFinite(positions[i+2]);
                assert Math.abs(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2]-1)<.0001 : action;
            }
        }
        model.poseAction("idle",.5,positions,normals,"mine",0);model.poseAction("idle",.5,other,otherNormals,"mine",60);
        assert distance(positions,other)>0 : "装挖时头部须按最近方块的俯仰变化，不能只在服务器记录转头";
        var indices = checks.getAsJsonArray("indices");
        model.pose("idle",.5,positions,normals);model.poseAction("idle",.5,other,otherNormals,"chest_peek",0);
        assert distance(positions,other)==0 : "箱内探头只复用待机采样，不得新增扶箱或翻倒姿势";
        double largest = 0;
        for (var element : checks.getAsJsonArray("rows")) {
            var row = element.getAsJsonObject();
            model.pose(row.get("clip").getAsString(), row.get("time").getAsDouble(), positions, normals);
            var points = row.getAsJsonArray("points");
            for (int i=0;i<indices.size();i++) for (int axis=0;axis<3;axis++) {
                double expected = points.get(i).getAsJsonArray().get(axis).getAsDouble();
                largest = Math.max(largest, Math.abs(positions[indices.get(i).getAsInt()*3+axis] - expected));
            }
        }
        // 编辑器原权重保留五位小数，运行时归一化及 float 存储会产生微小误差。
        assert largest < .002 : "游戏蒙皮与原生编辑器不同，最大误差 BB 单位：" + largest;
        assert NaiwaAnimations.resolve("laugh_sequence", .59).name().equals("laugh_enter");
        assert NaiwaAnimations.resolve("laugh_sequence", .6).name().equals("laugh");
        assert NaiwaAnimations.resolve("laugh_sequence", 8.849).name().equals("laugh") : "新大笑不能在旧版边界提前收场";
        assert NaiwaAnimations.resolve("laugh_sequence", 8.85).name().equals("laugh_exit");
        assert Math.abs(NaiwaAnimations.length("laugh_sequence")-9.65)<1E-9;
        assert Math.abs(NaiwaAnimations.resolve("laugh_sequence", 9.65).seconds()-.8)<1E-9;
        byte[] corrupt = bytes.clone(); corrupt[8] = 127;
        rejects(corrupt); rejects(Arrays.copyOf(bytes, 40));
        shieldSkin();
        System.out.println("11 段动画、33 组原生姿态对照、播放边界和损坏资源检查通过；最大蒙皮误差 BB 单位：" + largest);
    }

    /** 用优化前的逐顶点算法作独立基准，逐元素检查位置与法线，不只检查有限数或少量特征点。 */
    private static void actionPrecomputationChecks(NaiwaMesh model) {
        String[] actions={"","mirror","door_giggle","tree","tree_exposed","mine","mine_hide","fish","fish_land","boat","creeper_wait","scratch","diamond_point","diamond_caught","chest_peek","scope_close","unknown","mine_unknown"};
        float[] actual=new float[model.vertexCount*3],actualNormals=actual.clone();
        float[] expected=actual.clone(),expectedNormals=actual.clone();
        float[] head=actual.clone(),headNormals=actual.clone();
        Arrays.fill(head,-999);Arrays.fill(headNormals,-999);
        boolean[] selected=new boolean[model.vertexCount];int previous=-1;
        for(int vertex:model.headVertices) {assert vertex>previous;selected[vertex]=true;previous=vertex;}
        assert model.headVertices.length>0&&model.headVertices.length<model.vertexCount;
        int cases=0;
        for(var clip:model.clips.values()) for(String action:actions)
            for(double time:new double[]{-.1,0,clip.seconds()*.37,clip.seconds(),clip.seconds()+.5})
                for(float pitch:new float[]{-90,0,67}) {
                    model.pose(clip.name(),time,expected,expectedNormals);
                    legacyAction(model,time,expected,expectedNormals,action,pitch);
                    model.poseAction(clip.name(),time,actual,actualNormals,action,pitch);
                    assert Arrays.equals(actual,expected)&&Arrays.equals(actualNormals,expectedNormals)
                            : "姿势预计算不能改变位置或法线："+clip.name()+"/"+action+"/"+time+"/"+pitch;
                    model.poseHeadAction(clip.name(),time,head,headNormals,action,pitch);
                    for(int i=0;i<head.length;i++)assert selected[i/3]
                            ?head[i]==actual[i]&&headNormals[i]==actualNormals[i]
                            :head[i]==-999&&headNormals[i]==-999 : "头部必须与全身结果一致且不写入其他顶点";
                    cases++;
                }
        System.out.println("姿势层预计算与旧算法 "+cases+" 组全顶点位置及法线精确对照通过");
        System.out.println("头部路径 "+model.headVertices.length+"/"+model.vertexCount+" 顶点，"+cases+" 组位置、法线及未使用顶点检查通过");
    }

    /** 固定保留第二批优化前的运算顺序与骨号规则，不能改成调用预计算数组，否则失去对照意义。 */
    private static void legacyAction(NaiwaMesh model,double seconds,float[] target,float[] normals,String action,float pitch) {
        if(action.isEmpty())return;
        for(int v=0;v<model.vertexCount;v++) {
            float left=0,right=0,legs=0,head=0;
            for(int j=0;j<model.joints[v].length;j++) {
                int bone=model.joints[v][j];float weight=model.weights[v][j];
                if(bone>=17&&bone<=18||bone==19||bone==36)left+=weight;
                if(bone>=21&&bone<=22||bone==23||bone==37)right+=weight;
                if(bone>=26&&bone<=35)legs+=weight;
                if(bone>=4&&bone<=15)head+=weight;
            }
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
            float angle=arms*(action.startsWith("mine")||action.equals("diamond_point")||action.equals("scratch")?right:left+right);
            if(angle!=0)legacyRotateX(target,normals,v,23,angle);
            if(action.equals("fish_land")||action.equals("boat"))legacyRotateX(target,normals,v,12,-1.3F*legs);
            if(action.equals("mirror")||action.startsWith("mine")||action.equals("scope_close"))legacyRotateX(target,normals,v,27,(float)Math.toRadians(pitch)*head);
        }
    }

    private static void legacyRotateX(float[] points,float[] normals,int v,float y,float angle) {
        int i=v*3;float c=(float)Math.cos(angle),s=(float)Math.sin(angle),dy=points[i+1]-y,dz=points[i+2]-0;
        points[i+1]=y+dy*c-dz*s;points[i+2]=0+dy*s+dz*c;
        float ny=normals[i+1],nz=normals[i+2];normals[i+1]=ny*c-nz*s;normals[i+2]=ny*s+nz*c;
    }

    /** 与旧筛选及手掌计算逐项对照，防止优化漏面、改序或取到渲染变换后的坐标。 */
    private static void renderPreparationChecks(NaiwaMesh model) {
        boolean[] head=model.headFaces();
        for(boolean headOnly:new boolean[]{false,true}) {
            int[][] groups=model.facesByMaterial(headOnly);
            int total=0;
            for(int m=0;m<model.materialCount;m++) {
                int index=0;
                for(int f=0;f<model.faceCount;f++) if(model.materials[f]==m&&(!headOnly||head[f])) {
                    assert index<groups[m].length && groups[m][index++]==f : "材质分组必须保留原面序";
                }
                assert index==groups[m].length : "分组不能多出面片";
                total+=index;
            }
            assert total==(headOnly?1654:model.faceCount) : "全身及头部面片不能遗漏";
        }
        float[] posed=new float[model.vertexCount*3],normals=posed.clone();
        double[] hand=new double[3];
        for(String clip:new String[]{"idle","laugh","jump"}) for(String action:new String[]{"fish","fish_land",""}) {
            model.poseAction(clip,.5,posed,normals,action,0);
            float x=0,y=0,z=0,total=0;
            for(int v=0;v<model.vertexCount;v++) for(int j=0;j<model.joints[v].length;j++) {
                int bone=model.joints[v][j];if(bone!=23&&bone!=37)continue;
                float weight=model.weights[v][j];x+=posed[v*3]*weight;y+=posed[v*3+1]*weight;z+=posed[v*3+2]*weight;total+=weight;
            }
            model.rightHandPosition(posed,hand);
            assert hand[0]==x/total/16 && hand[1]==y/total/16 && hand[2]==z/total/16+.08 : "手掌位置必须与原计算一致";
        }
    }

    /** 对实际图像检查 UV 岛透明边界及所有面的绕序，防止生成贴图换版后出现缺面或错采样。 */
    private static void shieldSkin() throws Exception {
        Path texture=Path.of("src/main/resources/assets/naiwa/textures/entity/naiwa_shield.png");
        var image=ImageIO.read(texture.toFile());
        assert image.getWidth()==NaiwaShieldSkin.WIDTH && image.getHeight()==NaiwaShieldSkin.HEIGHT;
        assert Files.mismatch(texture,Path.of("../assets/shield/naiwa-shield-v1.png"))==-1 : "运行贴图与选定原图不同";
        assert NaiwaShieldSkin.FACES.size()==12;
        var front=NaiwaShieldSkin.FACES.getFirst();
        assert front.nz()==1 : "盾面必须与原奶蛙头共用朝外 +Z";
        for(var face:NaiwaShieldSkin.FACES) {
            float[] p=face.xyz(), uv=face.uv();
            float ax=p[3]-p[0], ay=p[4]-p[1], az=p[5]-p[2];
            float bx=p[6]-p[0], by=p[7]-p[1], bz=p[8]-p[2];
            assert (ay*bz-az*by)*face.nx()+(az*bx-ax*bz)*face.ny()+(ax*by-ay*bx)*face.nz()>0 : "盾面绕序与法线不一致";
            for(int x:new int[]{(int)uv[0],(int)(uv[0]+uv[2])/2,(int)uv[2]})
                for(int y:new int[]{(int)uv[1],(int)(uv[1]+uv[3])/2,(int)uv[3]})
                    assert (image.getRGB(x,y)>>>24)>200 : "盾面 UV 采入透明区："+x+","+y;
        }
        var sounds=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/naiwa/sounds.json"))).getAsJsonObject();
        for(String name:new String[]{"laugh","shield_laugh"}) {
            String resource=sounds.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString();
            byte[] audio=Files.readAllBytes(Path.of("src/main/resources/assets/naiwa/sounds/"+resource.substring(6)+".ogg"));
            assert new String(audio,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("OggS") : "笑声必须是可用 OGG 资源";
        }
        System.out.println("独立盾贴图、UV 不透明区域、朝外法线、全部面绕序及两种笑声音源检查通过");
    }

    /** 无图形环境检查实际视角状态机，覆盖持续阶段、手动切换、重生及退出的恢复。 */
    private static void totemCameraChecks() {
        for(var initial:net.minecraft.client.CameraType.values()) {
            var camera=new NaiwaTotemCamera();Object player=new Object(),next=new Object();
            assert camera.update(player,false,initial)==initial;
            assert camera.update(player,true,initial)==net.minecraft.client.CameraType.THIRD_PERSON_BACK : "触发后须为角色背面视角";
            for(int stage=0;stage<4;stage++)assert camera.update(player,true,net.minecraft.client.CameraType.FIRST_PERSON)==net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            assert camera.update(player,false,net.minecraft.client.CameraType.THIRD_PERSON_BACK)==initial : "结束恢复原视角，不能被中途切换覆盖";
            assert camera.update(player,false,net.minecraft.client.CameraType.THIRD_PERSON_FRONT)==net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
            camera.update(player,true,initial);
            assert camera.update(null,false,net.minecraft.client.CameraType.THIRD_PERSON_BACK)==initial : "退出恢复";
            camera.update(player,true,initial);
            assert camera.update(next,false,net.minecraft.client.CameraType.THIRD_PERSON_BACK)==initial : "新玩家实例不继承临时视角";
            camera.update(player,true,initial);camera.update(next,true,net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            assert camera.update(next,false,net.minecraft.client.CameraType.THIRD_PERSON_BACK)==initial : "连续实例切换仍保留进入前视角";
        }
        System.out.println("图腾背面视角及结束、重生、退出恢复状态检查通过");
    }

    private static double distance(float[] a, float[] b) {
        double max=0;for(int i=0;i<a.length;i++)max=Math.max(max,Math.abs(a[i]-b[i]));return max;
    }

    /** 新增手指须随手臂整蛊，不能因骨号位于腿骨后而被划船屈腿动作拖走。 */
    private static void fingerActionChecks(NaiwaMesh model) {
        float[] base=model.positions.clone(),normals=model.normals.clone(),posed=base.clone(),posedNormals=normals.clone();
        for(boolean right:new boolean[]{false,true}) {
            int finger=right?37:36,vertex=-1;
            assert NaiwaMesh.handBone(finger,right)&&!NaiwaMesh.handBone(finger,!right);
            for(int v=0;v<model.vertexCount;v++)if(model.joints[v].length==1&&model.joints[v][0]==finger){vertex=v;break;}
            assert vertex>=0 : "新模型应保留手指独占权重点";
            model.pose("idle",.5,base,normals);model.poseAction("idle",.5,posed,posedNormals,"fish_land",0);
            for(int axis=0;axis<3;axis++)assert Math.abs(base[vertex*3+axis]-posed[vertex*3+axis])<.0001 : "手指不能参与屈腿";
            model.poseAction("idle",.5,posed,posedNormals,"tree",0);
            assert Math.abs(base[vertex*3+1]-posed[vertex*3+1])+Math.abs(base[vertex*3+2]-posed[vertex*3+2])>1 : "手指须随整蛊手臂旋转";
        }
        System.out.println("左右手指归属、整蛊随臂与屈腿隔离检查通过");
    }
    private static void rejects(byte[] bytes) throws Exception {
        try {new NaiwaMesh(new ByteArrayInputStream(bytes));throw new AssertionError("损坏模型未被拒绝");}
        catch (IOException expected) {}
    }
}
