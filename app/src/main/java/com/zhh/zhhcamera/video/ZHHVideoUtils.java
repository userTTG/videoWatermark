package com.zhh.zhhcamera.video;

import android.os.Environment;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @ClassName ZHHVideoUtils
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/8/16 16:25
 * @Version 1.0
 */
public class ZHHVideoUtils {

   public static String writeContent(String path, ByteBuffer byteBuffer, boolean append) {
      byte[] bytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(bytes,0,bytes.length);
      return writeContent(path,bytes,append);
   }

   public static String writeContent(String path,byte[] array,boolean append) {
      char[] HEX_CHAR_TABLE = {
              '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
      };
      StringBuilder sb = new StringBuilder();
      for (byte b : array) {
         sb.append(HEX_CHAR_TABLE[(b & 0xf0) >> 4]);
         sb.append(HEX_CHAR_TABLE[b & 0x0f]);
      }
      FileWriter writer = null;
      try {

         writer = new FileWriter(path, append);
         writer.write(sb.toString());
         writer.write("\n");
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         try {
            if(writer != null){
               writer.close();
            }
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      return sb.toString();
   }
}
