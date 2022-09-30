package com.zhh.zhhcamera.test;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @ClassName VideoUtil
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/9/30 14:11
 * @Version 1.0
 */
public class VideoUtil {
   private static final String TAG = "VideoUtil";

   public static final int COLOR_FormatI420 = 1;
   public static final int COLOR_FormatNV21 = 2;
   public static final boolean VERBOSE = false;

   private static boolean isImageFormatSupported(Image image) {
      int format = image.getFormat();
      switch (format) {
         case ImageFormat.YUV_420_888:
         case ImageFormat.NV21:
         case ImageFormat.YV12:
            return true;
      }
      return false;
   }

   public static byte[] getDataFromImage(Image image, int colorFormat) {
      if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
         throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
      }
      if (!isImageFormatSupported(image)) {
         throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
      }
      Rect crop = image.getCropRect();
      int format = image.getFormat();
      int width = crop.width();
      int height = crop.height();
      Image.Plane[] planes = image.getPlanes();
      byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
      byte[] rowData = new byte[planes[0].getRowStride()];
      if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
      int channelOffset = 0;
      int outputStride = 1;
      for (int i = 0; i < planes.length; i++) {
         switch (i) {
            case 0:
               channelOffset = 0;
               outputStride = 1;
               break;
            case 1:
               if (colorFormat == COLOR_FormatI420) {
                  channelOffset = width * height;
                  outputStride = 1;
               } else if (colorFormat == COLOR_FormatNV21) {
                  channelOffset = width * height + 1;
                  outputStride = 2;
               }
               break;
            case 2:
               if (colorFormat == COLOR_FormatI420) {
                  channelOffset = (int) (width * height * 1.25);
                  outputStride = 1;
               } else if (colorFormat == COLOR_FormatNV21) {
                  channelOffset = width * height;
                  outputStride = 2;
               }
               break;
         }
         ByteBuffer buffer = planes[i].getBuffer();
         int rowStride = planes[i].getRowStride();
         int pixelStride = planes[i].getPixelStride();
         if (VERBOSE) {
            Log.v(TAG, "pixelStride " + pixelStride);
            Log.v(TAG, "rowStride " + rowStride);
            Log.v(TAG, "width " + width);
            Log.v(TAG, "height " + height);
            Log.v(TAG, "buffer size " + buffer.remaining());
         }
         int shift = (i == 0) ? 0 : 1;
         int w = width >> shift;
         int h = height >> shift;
         buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
         for (int row = 0; row < h; row++) {
            int length;
            if (pixelStride == 1 && outputStride == 1) {
               length = w;
               buffer.get(data, channelOffset, length);
               channelOffset += length;
            } else {
               length = (w - 1) * pixelStride + 1;
               buffer.get(rowData, 0, length);
               for (int col = 0; col < w; col++) {
                  data[channelOffset] = rowData[col * pixelStride];
                  channelOffset += outputStride;
               }
            }
            if (row < h - 1) {
               buffer.position(buffer.position() + rowStride - length);
            }
         }
         if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
      }
      return data;
   }

   private static byte[] getDataFromImage2(Image image, int colorFormat) {
      if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
         throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
      }
      if (!isImageFormatSupported(image)) {
         throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
      }
      Rect crop = image.getCropRect();
      int format = image.getFormat();
      int width = crop.width();
      int height = crop.height();
      Image.Plane[] planes = image.getPlanes();
      byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
      byte[] rowData = new byte[planes[0].getRowStride()];
      if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
      int channelOffset = 0;
      int outputStride = 1;
      for (int i = 0; i < planes.length; i++) {
         switch (i) {
            case 0:
               channelOffset = 0;
               outputStride = 1;
               break;
            case 1:
               if (colorFormat == COLOR_FormatI420) {
                  channelOffset = width * height;
                  outputStride = 1;
               } else if (colorFormat == COLOR_FormatNV21) {
                  channelOffset = width * height + 1;
                  outputStride = 2;
               }
               break;
            case 2:
               if (colorFormat == COLOR_FormatI420) {
                  channelOffset = (int) (width * height * 1.25);
                  outputStride = 1;
               } else if (colorFormat == COLOR_FormatNV21) {
                  channelOffset = width * height;
                  outputStride = 2;
               }
               break;
         }
         ByteBuffer buffer = planes[i].getBuffer();
         int rowStride = planes[i].getRowStride();
         int pixelStride = planes[i].getPixelStride();
         if (VERBOSE) {
            Log.v(TAG, "pixelStride " + pixelStride);
            Log.v(TAG, "rowStride " + rowStride);
            Log.v(TAG, "width " + width);
            Log.v(TAG, "height " + height);
            Log.v(TAG, "buffer size " + buffer.remaining());
         }
         int shift = (i == 0) ? 0 : 1;
         int w = width >> shift;
         int h = height >> shift;
         buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
         for (int row = 0; row < h; row++) {
            int length;
            if (pixelStride == 1 && outputStride == 1) {
               length = w;
               buffer.get(data, channelOffset, length);
               channelOffset += length;
            } else {
               length = (w - 1) * pixelStride + 1;
               buffer.get(rowData, 0, length);
               for (int col = 0; col < w; col++) {
                  data[channelOffset] = rowData[col * pixelStride];
                  channelOffset += outputStride;
               }
            }
            if (row < h - 1) {
               buffer.position(buffer.position() + rowStride - length);
            }
         }
         if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
      }
      return data;
   }

   /**
    * I420转nv21
    */
   public static byte[] I420Tonv21(byte[] data, int width, int height) {
      byte[] ret = new byte[data.length];
      int total = width * height;

      ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
      ByteBuffer bufferVU = ByteBuffer.wrap(ret, total, total / 2);

      bufferY.put(data, 0, total);
      for (int i = 0; i < total / 4; i += 1) {
         bufferVU.put(data[i + total + total / 4]);
         bufferVU.put(data[total + i]);
      }

      return ret;
   }

   public static Bitmap getBitmapImageFromYUV(byte[] data, int width, int height) {
      YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      yuvimage.compressToJpeg(new Rect(0, 0, width, height), 80, baos);
      byte[] jdata = baos.toByteArray();
      BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
      bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
      Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
      return bmp;
   }

   public static Bitmap compressToJpeg(Image image) {
      Rect rect = image.getCropRect();
      YuvImage yuvimage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      yuvimage.compressToJpeg(new Rect(0, 0, rect.width(), rect.height()), 80, baos);
      byte[] jdata = baos.toByteArray();
      BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
      bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
      Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
      return bmp;
   }

   public static byte[] rotateYUV420SP(byte[] src, int width, int height) {
      byte[] dst = new byte[src.length];
      int wh = width * height;
      //旋转Y
      int k = 0;
      for (int i = 0; i < width; i++) {
         for (int j = height - 1; j >= 0; j--) {
            dst[k] = src[width * j + i];
            k++;
         }
      }

      int halfWidth = width / 2;
      int halfHeight = height / 2;
      for (int colIndex = 0; colIndex < halfWidth; colIndex++) {
         for (int rowIndex = halfHeight - 1; rowIndex >= 0; rowIndex--) {
            int index = (halfWidth * rowIndex + colIndex) * 2;
            dst[k] = src[wh + index];
            k++;
            dst[k] = src[wh + index + 1];
            k++;
         }
      }
      return dst;
   }

}
