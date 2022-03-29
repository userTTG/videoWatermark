package com.zhh.zhhcamera

import android.R
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
 *@ClassName GLVideoRender
 *@Description TODO
 *@Author zhangh-be
 *@Date 2022/3/29 15:56
 *@Version 1.0
 */
class GLVideoRender(val context: Context, var video: String) : GLSurfaceView.Renderer,SurfaceTexture.OnFrameAvailableListener,MediaPlayer.OnVideoSizeChangedListener {

   var aPositionLocation:Int = 0;
   var programId:Int = 0;
   private val vertexBuffer: FloatBuffer;
   private val vertexData = floatArrayOf(
      1f, -1f, 0f,
      -1f, -1f, 0f,
      1f, 1f, 0f,
      -1f, 1f, 0f
   )

   private val projectionMatrix = FloatArray(16)
   private var uMatrixLocation = 0

   private val textureVertexData = floatArrayOf(
      1f, 0f,
      0f, 0f,
      1f, 1f,
      0f, 1f
   )
   private val textureVertexBuffer: FloatBuffer
   private var uTextureSamplerLocation = 0
   private var aTextureCoordLocation = 0
   private var textureId = 0

   private var surfaceTexture: SurfaceTexture? = null
   private var mediaPlayer: MediaPlayer
   private val mSTMatrix = FloatArray(16)
   private var uSTMMatrixHandle = 0

   private var updateSurface = false
   private var screenWidth = 0;
   private  var screenHeight:Int = 0

   init {
      vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
         .order(ByteOrder.nativeOrder())
         .asFloatBuffer()
         .put(vertexData)
      vertexBuffer.position(0)

      textureVertexBuffer = ByteBuffer.allocateDirect(textureVertexData.size * 4)
         .order(ByteOrder.nativeOrder())
         .asFloatBuffer()
         .put(textureVertexData)
      textureVertexBuffer.position(0)
      mediaPlayer = MediaPlayer()
      initMediaPlayer()
   }

   override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
      programId = GLES20.glCreateProgram();
      aPositionLocation = GLES20.glGetAttribLocation(programId, "aPosition")

      uMatrixLocation = GLES20.glGetUniformLocation(programId, "uMatrix")
      uSTMMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
      uTextureSamplerLocation = GLES20.glGetUniformLocation(programId, "sTexture")
      aTextureCoordLocation = GLES20.glGetAttribLocation(programId, "aTexCoord")


      val textures = IntArray(1)
      GLES20.glGenTextures(1, textures, 0)

      textureId = textures[0]
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
//      ShaderUtils.checkGlError("glBindTexture mTextureID")
      /*GLES11Ext.GL_TEXTURE_EXTERNAL_OES的用处？
      之前提到视频解码的输出格式是YUV的（YUV420p，应该是），那么这个扩展纹理的作用就是实现YUV格式到RGB的自动转化，
      我们就不需要再为此写YUV转RGB的代码了*/
      /*GLES11Ext.GL_TEXTURE_EXTERNAL_OES的用处？
      之前提到视频解码的输出格式是YUV的（YUV420p，应该是），那么这个扩展纹理的作用就是实现YUV格式到RGB的自动转化，
      我们就不需要再为此写YUV转RGB的代码了*/GLES20.glTexParameterf(
         GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
         GLES20.GL_NEAREST.toFloat()
      )
      GLES20.glTexParameterf(
         GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
         GLES20.GL_LINEAR.toFloat()
      )

      surfaceTexture = SurfaceTexture(textureId)
      surfaceTexture?.setOnFrameAvailableListener(this) //监听是否有新的一帧数据到来


      val surface = Surface(surfaceTexture)
      mediaPlayer.setSurface(surface)
   }

   override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
      screenWidth = width
      screenHeight = height
      mediaPlayer.prepare()
      mediaPlayer.setOnPreparedListener { mediaPlayer -> mediaPlayer.start() }
   }

   override fun onDrawFrame(gl: GL10) {
      GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
      synchronized(this) {
         if (updateSurface) {
            surfaceTexture!!.updateTexImage() //获取新数据
            surfaceTexture!!.getTransformMatrix(mSTMatrix) //让新的纹理和纹理坐标系能够正确的对应,mSTMatrix的定义是和projectionMatrix完全一样的。
            updateSurface = false
         }
      }
      GLES20.glUseProgram(programId)
      GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0)
      GLES20.glUniformMatrix4fv(uSTMMatrixHandle, 1, false, mSTMatrix, 0)

      vertexBuffer.position(0)
      GLES20.glEnableVertexAttribArray(aPositionLocation)
      GLES20.glVertexAttribPointer(
         aPositionLocation, 3, GLES20.GL_FLOAT, false,
         12, vertexBuffer
      )

      textureVertexBuffer.position(0)
      GLES20.glEnableVertexAttribArray(aTextureCoordLocation)
      GLES20.glVertexAttribPointer(
         aTextureCoordLocation,
         2,
         GLES20.GL_FLOAT,
         false,
         8,
         textureVertexBuffer
      )

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

      GLES20.glUniform1i(uTextureSamplerLocation, 0)
      GLES20.glViewport(0, 0, screenWidth, screenHeight)
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
   }

   override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
      updateSurface = true;
   }

   override fun onVideoSizeChanged(mp: MediaPlayer?, width: Int, height: Int) {
      updateProjection(width, height);
   }

   fun getMediaPlayer(): MediaPlayer {
      return mediaPlayer
   }

   private fun updateProjection(videoWidth: Int, videoHeight: Int) {
      val screenRatio = screenWidth.toFloat() / screenHeight
      val videoRatio = videoWidth.toFloat() / videoHeight
      if (videoRatio > screenRatio) {
         Matrix.orthoM(
            projectionMatrix,
            0,
            -1f,
            1f,
            -videoRatio / screenRatio,
            videoRatio / screenRatio,
            -1f,
            1f
         )
      } else Matrix.orthoM(
         projectionMatrix,
         0,
         -screenRatio / videoRatio,
         screenRatio / videoRatio,
         -1f,
         1f,
         -1f,
         1f
      )
   }


   private fun initMediaPlayer() {
      try {
//         val afd = context.assets.openFd("big_buck_bunny.mp4")
         mediaPlayer.setDataSource(video)
         //            String path = "http://192.168.1.254:8192";
//            mediaPlayer.setDataSource(path);
//            mediaPlayer.setDataSource(TextureViewMediaActivity.videoPath);
      } catch (e: IOException) {
         e.printStackTrace()
      }
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
      mediaPlayer.setLooping(true)
      mediaPlayer.setOnVideoSizeChangedListener(this)
   }
}