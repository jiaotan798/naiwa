package com.naiwa.client;

/** 仅用于透视主画面的不透明皮肤；输入坐标已变换到以相机为原点的渲染空间。 */
final class NaiwaFaceCulling {
    /** 0/4 是当前源模型的不透明本体和眼周皮肤；贴图测试阻止它们静默改成透明材质。 */
    static boolean opaque(int material){return material==0||material==4;}

    /** 相机在模型包围盒内时保留双面行为，避免贴身穿入模型后看到空壳。 */
    static boolean outside(float minX,float minY,float minZ,float maxX,float maxY,float maxZ){
        return minX>0||maxX<0||minY>0||maxY<0||minZ>0||maxZ<0;
    }

    /** 叉积与面上位置点积为正时朝离相机；近乎共面和退化三角形保守地保留。 */
    static boolean backFacing(float[] positions,int[] corners,int face){
        int a=corners[face*3]*3,b=corners[face*3+1]*3,c=corners[face*3+2]*3;
        float x=positions[a],y=positions[a+1],z=positions[a+2];
        float abx=positions[b]-x,aby=positions[b+1]-y,abz=positions[b+2]-z;
        float acx=positions[c]-x,acy=positions[c+1]-y,acz=positions[c+2]-z;
        return (aby*acz-abz*acy)*x+(abz*acx-abx*acz)*y+(abx*acy-aby*acx)*z>1E-6F;
    }
}
