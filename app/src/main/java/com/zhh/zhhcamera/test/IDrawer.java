package com.zhh.zhhcamera.test;

import android.graphics.SurfaceTexture;

/**
 * @ClassName IDrawer
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/4/6 14:52
 * @Version 1.0
 */
interface IDrawer {
   public void setVideoSize(int videoWidth, int videoHeight);

   public void setWorldSize(int worldWidth, int worldHeight);

   public void setAlpha(float alpha);

   public void draw();

   public void setTextureID(int textureID);

   public void release();
}
