package com.zhh.zhhcamera.test;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * @ClassName TestGLSurfaceVIew
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/4/7 16:53
 * @Version 1.0
 */
public class TestGLSurfaceView  extends GLSurfaceView {

   private VideoDrawer videoDrawer;
   private VideoRender videoRender;
   private TestRender mTestRender;

   public TestGLSurfaceView(Context context) {
      this(context,null);
   }

   public TestGLSurfaceView(Context context, AttributeSet attrs) {
      super(context, attrs);
      initRender();
   }

   private void initRender() {

      setEGLContextClientVersion(2);
//      //初始化绘制器
//      videoDrawer = new VideoDrawer();
//      videoDrawer.setVideoSize(1080, 1920);
//      //初始化渲染器
//      videoRender = new VideoRender();
//      videoRender.addDrawer(videoDrawer);
//      mTestRender = new TestRender();

      mTestRender = new TestRender();
      mTestRender.setVideoSize(1080,1920);
      setRenderer(mTestRender);
   }

   public VideoDrawer getVideoDrawer() {
      return videoDrawer;
   }

   public VideoRender getVideoRender() {
      return videoRender;
   }

   public TestRender getTestRender() {
      return mTestRender;
   }
}
