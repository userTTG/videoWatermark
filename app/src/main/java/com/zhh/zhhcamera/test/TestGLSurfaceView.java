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
      mTestRender = new TestRender();
      mTestRender.setVideoSize(1080,1920);
      setRenderer(mTestRender);
   }

   public TestRender getTestRender() {
      return mTestRender;
   }
}
