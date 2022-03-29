package com.zhh.zhhcamera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.zhh.zhhcamera.databinding.ActivityMainBinding
import com.zhh.zhhcamera.pictureSelector.GlideEngine
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
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
                    mMainBinding?.surfaceView?.setEGLContextClientVersion(2)
                    mMainBinding?.surfaceView?.setRenderer(result?.get(0)?.let {
                        GLVideoRender(this@MainActivity,
                            it.realPath)
                    })
                }
                override fun onCancel() {

                }
            })
    }
}