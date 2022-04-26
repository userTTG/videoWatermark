package com.zhh.zhhcamera.test;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @ClassName H264Player
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/4/2 16:32
 * @Version 1.0
 */
public class MP4Player implements Runnable {

    private static final String TAG = "MP4Player";

    // 本地 h264 文件路径
    private String path;
    private Surface surface;
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaCodec;
    private MediaCodec encoder;
    private MediaFormat mediaFormat;
    private Context context;
    private volatile boolean isDecodeFinish = false;
    private long timeOfFrame = 30;

    private ReentrantLock mLock = new ReentrantLock();

    public MP4Player(Context context, String path, Surface surface) {

        this.context = context;
        this.path = path;
        this.surface = surface;

        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
            Log.d(TAG, "getTrackCount: " + mediaExtractor.getTrackCount());
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                //如果是video格式
                if (mime.startsWith("video")) {
                    mediaFormat = format;
                    mediaExtractor.selectTrack(i);
                }
            }

            int rate = mediaFormat.getInteger("frame-rate");
            timeOfFrame = 1000/rate;
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            this.mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            // 视频宽高暂时写死
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            mediaCodec.configure(mediaFormat, surface, null, 0);


            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            //视频信息配置
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mediaFormat.getInteger("width"), mediaFormat.getInteger("height"));
            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,15);//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,400000);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,30);//i帧间隔
            //设置配置信息给mediaCodec
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

        } catch (Exception e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }


    public void play() {
        mediaCodec.start();
        encoder.start();
        new Thread(this::run).start();
        new Thread(() -> encodeH264()).start();
    }
    @Override
    public void run() {
        // 解码 h264
        try {
            decodeMP4();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void decodeMP4() {
        while (!isDecodeFinish) {
            long startTime = System.currentTimeMillis();
            int inputIndex = mediaCodec.dequeueInputBuffer(-1);
            Log.d(TAG, "inputIndex: " + inputIndex);
            if (inputIndex >= 0) {
                ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inputIndex);
                //读取一片或者一帧数据
                int sampSize = mediaExtractor.readSampleData(byteBuffer,0);
                //读取时间戳
                long time = mediaExtractor.getSampleTime();
                Log.d(TAG, "sampSize: " + sampSize + "time: " + time);
                if (sampSize > 0 && time >= 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampSize, time, 0);
                    //读取一帧后必须调用，提取下一帧
                    mediaExtractor.advance();

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                    Log.d(TAG, "outIndex: " + outIndex);
                    if (outIndex >= 0) {
                        ByteBuffer buffer = mediaCodec.getOutputBuffer(outIndex);

                        int enInputIndex = encoder.dequeueInputBuffer(0);
                        if (enInputIndex>-1){
                            ByteBuffer enInputBuffer = encoder.getInputBuffer(enInputIndex);
                            enInputBuffer.clear();
                            enInputBuffer.put(buffer);
                            encoder.queueInputBuffer(enInputIndex, 0, enInputBuffer.limit(), 0, 0);//通知编码器 数据放入
                        }
                        mediaCodec.releaseOutputBuffer(outIndex, true);
                    }
                    //控制帧率在30帧左右
                    try {
                        long handleTime = System.currentTimeMillis() - startTime;
                        long waitTime = timeOfFrame - handleTime;
                        if (waitTime>0){
                            Thread.sleep(waitTime);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else {
                    close();
                }
            }

        }
    }

    private void encodeH264(){
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (!isDecodeFinish){
            mLock.lock();
            try {
                if (encoder != null){
                    //获取可用ByteBuffer下标
                    int index = encoder.dequeueOutputBuffer(bufferInfo, 100000);
                    if(index>=0){
                        ByteBuffer buffer = encoder.getOutputBuffer(index);
                        byte[] outData=new byte[bufferInfo.size];
                        //给outData设置数据
                        buffer.get(outData);
                        //写入文件
                        writeContent(outData);
                        //释放资源
                        encoder.releaseOutputBuffer(index,false);
                    }
                }
            }finally {
                mLock.unlock();
            }
        }
    }

    public   String writeContent(byte[] array) {
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

            writer = new FileWriter(Environment.getExternalStorageDirectory()+"/zhh_mp4.txt", true);
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

    public void close() {
        mLock.lock();
        try {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
                mediaExtractor.release();
                mediaExtractor = null;
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }

        }finally {
            mLock.unlock();
            isDecodeFinish = true;
        }

    }

    public int[] getSize(){
        if (mediaFormat!= null){
            return new int[]{mediaFormat.getInteger("width"), mediaFormat.getInteger("height")};
        }
        return new int[]{0, 0};
    }

}
