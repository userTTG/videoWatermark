package com.zhh.zhhcamera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.zhh.zhhcamera.databinding.ActivityMainBinding
import com.zhh.zhhcamera.pictureSelector.GlideEngine
import com.zhh.zhhcamera.test.MP4Player
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    var mMainBinding:ActivityMainBinding? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mMainBinding = DataBindingUtil.setContentView(this,R.layout.activity_main)
        mMainBinding?.tvSelectPicture?.setOnClickListener { v: View? ->
            selectPicture();
        }
    }

    fun selectPicture(){
        PictureSelector.create(this).openGallery(SelectMimeType.TYPE_VIDEO)
            .setImageEngine(GlideEngine.createGlideEngine())
            .setMaxSelectNum(1)
            .forResult(object : OnResultCallbackListener<LocalMedia>{
                override fun onResult(result: ArrayList<LocalMedia>?) {
//                    mMainBinding?.surfaceView?.setEGLContextClientVersion(2)
//                    mMainBinding?.surfaceView?.setRenderer(result?.get(0)?.let {
//                        GLVideoRender(this@MainActivity,
//                            it.realPath)
//                    })
                    mMainBinding?.surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            mMainBinding?.surfaceView?.holder?.removeCallback(this)
                            Log.e(TAG, "surfaceCreated")
                            val mp4Player = MP4Player(this@MainActivity,
                                result?.get(0)?.realPath ,
                                mMainBinding?.surfaceView?.holder?.surface)
                            val size = mp4Player.size;
                            if (size.get(0) !=0 && size.get(1)!=0){
                                val layoutParam = mMainBinding?.surfaceView?.layoutParams
                                layoutParam?.height = size[1] * mMainBinding?.surfaceView?.width!! / size[0]
                                mMainBinding?.surfaceView?.layoutParams = layoutParam
                            }
                            mp4Player.play()
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            Log.e(TAG, "surfaceChanged: "+width+","+height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.e(TAG, "surfaceDestroyed: " )
                        }
                    })



                }
                override fun onCancel() {

                }
            })
    }
}