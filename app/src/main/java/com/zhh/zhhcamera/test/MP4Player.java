package com.zhh.zhhcamera.test;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    private MediaFormat mediaFormat;
    private Context context;
    private boolean isDecodeFinish = false;
    private long timeOfFrame = 30;

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
        } catch (IOException e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }


    public void play() {
        mediaCodec.start();
        new Thread(this::run).start();
    }

    @Override
    public void run() {
        // 解码 h264
        decodeMP4();
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

    public void close() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaExtractor.release();
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
