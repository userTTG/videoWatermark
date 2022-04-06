package com.zhh.zhhcamera.test;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @ClassName ZhhGLSurfaceView
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/4/6 14:46
 * @Version 1.0
 */
public class ZhhGLSurfaceView extends GLSurfaceView {

   private VideoDrawer videoDrawer;
   private VideoRender videoRender;

   public ZhhGLSurfaceView(Context context) {
      this(context,null);
   }

   public ZhhGLSurfaceView(Context context, AttributeSet attrs) {
      super(context, attrs);
      initRender();
   }

   private void initRender() {
      setEGLContextClientVersion(2);
      //初始化绘制器
      videoDrawer = new VideoDrawer();
      videoDrawer.setVideoSize(1080, 1920);
      //初始化渲染器
      videoRender = new VideoRender();
      videoRender.addDrawer(videoDrawer);
      setRenderer(videoRender);
   }

   public VideoDrawer getVideoDrawer() {
      return videoDrawer;
   }

   public VideoRender getVideoRender() {
      return videoRender;
   }
}
