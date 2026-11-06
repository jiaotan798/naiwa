package com.naiwa.qa;

/** 只读 GPU 实际绘制目标，不能用 Java 对象绑定意图冒充最终 OpenGL 状态。 */
public final class ShaderTargetChecks {
    public static int samples;
    public static final java.util.Map<Integer,java.util.Map<String,Object>> bindings=new java.util.TreeMap<>();
    public static void check(net.minecraft.client.renderer.ShaderInstance shader) {
        int actual=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        MultiplayerCheck.require(actual==net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().frameBufferId,
                "自有着色器实际提交到主画布 actual="+actual);
        samples++;
        MultiplayerCheck.require(org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST)
                &&org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC)==org.lwjgl.opengl.GL11.GL_LEQUAL,"角色及圣光保留世界深度测试");
        MultiplayerCheck.require(org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK)==shader.getName().endsWith("flight_body"),"只有角色写入深度，透明圣光只写颜色");
        if(shader.getName().endsWith("flight_body")) {
            int expected=com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0);
            if(!bindings.containsKey(expected)) {
                int active=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
                int texture=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
                MultiplayerCheck.require(expected==texture,"角色实际纹理绑定与材质一致");
                org.lwjgl.opengl.GL13.glActiveTexture(active);
                int normal=org.lwjgl.opengl.GL20.glGetAttribLocation(shader.getId(),"Normal");
                var row=new java.util.LinkedHashMap<String,Object>();row.put("expectedTexture",expected);row.put("texture",texture);row.put("normalSlot",normal);
                row.put("depthWrite",org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK));
                row.put("depthTest",org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST));
                row.put("depthFunc",org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC));
                if(normal>=0){row.put("stride",org.lwjgl.opengl.GL20.glGetVertexAttribi(normal,org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE));row.put("normalEnabled",org.lwjgl.opengl.GL20.glGetVertexAttribi(normal,org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED));}
                bindings.put(expected,row);
            }
        }
    }
}
