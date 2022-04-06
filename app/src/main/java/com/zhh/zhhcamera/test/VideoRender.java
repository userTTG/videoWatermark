package com.zhh.zhhcamera.test;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @ProjectName: TheSimpllestplayer
 * @Package: com.yw.thesimpllestplayer.renderview
 * @ClassName: VideoRender
 * @Description: OpenGL渲染器
 * @Author: wei.yang
 * @CreateDate: 2021/11/6 15:38
 * @UpdateUser: 更新者：wei.yang
 * @UpdateDate: 2021/11/6 15:38
 * @UpdateRemark: 更新说明：
 * @Version: 1.0
 */
public class VideoRender implements GLSurfaceView.Renderer {
   private final List<IDrawer> drawers = new ArrayList<>();

   @Override
   public void onSurfaceCreated(GL10 gl, EGLConfig config) {
      //清空当前的所有颜色
      GLES20.glClearColor(0f, 0f, 0f, 0f);
      //开启混合,如果启用，则将计算的片段颜色值与颜色缓冲区中的值混合
      GLES20.glEnable(GLES20.GL_BLEND);
      //GL_SRC_ALPHA：表示使用源颜色的alpha值来作为因子
      //表示用1.0减去源颜色的alpha值来作为因子（1-alpha）
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
      IntBuffer textureBuffer = IntBuffer.allocate(drawers.size());
      //生成纹理ID
      GLES20.glGenTextures(drawers.size(),textureBuffer);
      int[] textureIds = new int[drawers.size()];
      textureBuffer.get(textureIds);
      for (int i = 0; i < textureIds.length; i++) {
         drawers.get(i).setTextureID(textureIds[i]);
      }
   }

   @Override
   public void onSurfaceChanged(GL10 gl, int width, int height) {
      //X，Y————以像素为单位，指定了视口的左下角（在第一象限内，以（0，0）为原点的）位置。
      //width，height————表示这个视口矩形的宽度和高度，根据窗口的实时变化重绘窗口。
      GLES20.glViewport(0, 0, width, height);
      for (IDrawer drawer : drawers) {
         drawer.setWorldSize(width, height);
      }

   }

   @Override
   public void onDrawFrame(GL10 gl) {
      //清除颜色缓冲和深度缓冲
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
      for (int i = 0; i < drawers.size(); i++) {
         drawers.get(i).draw();
      }
   }

   /**
    * 添加渲染器
    *
    * @param drawer
    */
   public void addDrawer(IDrawer drawer) {
      drawers.add(drawer);
   }
}
