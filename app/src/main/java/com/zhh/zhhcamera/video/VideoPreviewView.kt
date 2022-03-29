package com.zhh.zhhcamera.video

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 *@ClassName VideoPreviewView
 *@Description TODO
 *@Author zhangh-be
 *@Date 2022/3/18 16:17
 *@Version 1.0
 */
class VideoPreviewView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context, attrs),
   GLSurfaceView.Renderer {

   val mDrawer:VideoDrawer;
   val mMediaPlayer:MediaPlayerWrapper;
   init {
      setEGLContextClientVersion(2)
      setRenderer(this)
      renderMode = RENDERMODE_WHEN_DIRTY
      preserveEGLContextOnPause = false
      cameraDistance = 100F
      mDrawer = VideoDrawer()
      mMediaPlayer = MediaPlayerWrapper()
   }

   override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
      TODO("Not yet implemented")
   }

   override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
      TODO("Not yet implemented")
   }

   override fun onDrawFrame(gl: GL10?) {
      TODO("Not yet implemented")
   }
}