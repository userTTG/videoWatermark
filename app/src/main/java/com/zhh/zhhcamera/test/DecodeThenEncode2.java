package com.zhh.zhhcamera.test;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.ToastUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @ClassName DecodeThenEncode
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/8/11 17:42
 * @Version 1.0
 */
public class DecodeThenEncode2 {

    private static final String TAG = "DecodeThenEncode";

    private final int TIMEOUT_US = 0;

    // 本地 h264 文件路径
    private MediaExtractor mediaExtractor;
    private MediaCodec decoder;
    private MediaCodec encoder;

    private MediaMuxer mMediaMuxer;
    private long timeOfFrame = 30;
    private int videoTrackIndex;

    private MediaFormat mediaFormat;
    int videoWidth;
    int videoHeight;
    int videoRotation;

    char[] lock = {0};

    LinkedList<ByteBuffer> decodeOutQueue = new LinkedList<>();
    LinkedList<MediaCodec.BufferInfo> decodeOutInfoQueue = new LinkedList<>();

    long startTime;

    public DecodeThenEncode2() {

    }

    public void setPath(String path){
        new Thread(()->{
            initCodec(path);
            if (decoder == null){
                ToastUtils.showShort("未知错");
                return;
            }
            decoder.start();
            encoder.start();
            mMediaMuxer.start();
            ToastUtils.showShort("开始");
            decodeThenEncode();
        }).start();

    }

//    public void setPath(String path){
//
//        startTime = System.currentTimeMillis();
//        extraVideoInfo(path);
//        if (mediaFormat != null){
//            ToastUtils.showShort("开始");
//            new Thread(()->{
//                decode();
//            }).start();
//            new Thread(()->{
//                encode();
//            }).start();
//        }
//    }

    private void extraVideoInfo(String path){
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
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

        MediaMetadataRetriever retr = new MediaMetadataRetriever();
        retr.setDataSource(path);
        String width = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        videoWidth = Integer.parseInt(width);
        videoHeight = Integer.parseInt(height);
        videoRotation = Integer.parseInt(rotation);
    }

    private void decode(){

        try {
            this.decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            // 视频宽高暂时写死
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

            //视频信息配置
            MediaFormat format;

            if (videoRotation == 0 || videoRotation == 180){
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
            }else {
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoHeight, videoWidth);
            }

            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,videoWidth * videoHeight * 6);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);//i帧间隔

            HandlerThread codecHandlerThread = new HandlerThread("decode");
            codecHandlerThread.start();

            decoder.setCallback(new MediaCodec.Callback() {

                boolean decodeInputDone = false;

                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int decodeInputIndex) {
                    if (!decodeInputDone){
                        Log.e(TAG, "1-1 === "+Thread.currentThread().getName()+" decoder.decodeInputIndex: "+decodeInputIndex);
                        ByteBuffer byteBuffer = decoder.getInputBuffer(decodeInputIndex);
                        byteBuffer.clear();
                        int sampleDataSize = mediaExtractor.readSampleData(byteBuffer,0);
                        if (sampleDataSize>0){
                            decoder.queueInputBuffer(decodeInputIndex,0,sampleDataSize,mediaExtractor.getSampleTime(),0);
                            mediaExtractor.advance();
                        }else {
                            decoder.queueInputBuffer(decodeInputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decodeInputDone = true;
                        }
                    }
                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int decodeOutputIndex, @NonNull MediaCodec.BufferInfo info) {
                    Log.e(TAG, "1-2 === "+Thread.currentThread().getName()+" decoder.decodeOutputIndex: "+decodeOutputIndex);

                    while (decodeOutInfoQueue.size() >= 60){
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    ByteBuffer decodeOutBuffer = ByteBuffer.allocateDirect(info.size);
                    decodeOutBuffer.position(0);
                    ByteBuffer byteBuffer = decoder.getOutputBuffer(decodeOutputIndex);
                    decodeOutBuffer.put(byteBuffer);
                    decodeOutQueue.add(decodeOutBuffer);
                    decodeOutInfoQueue.add(info);

                    decoder.releaseOutputBuffer(decodeOutputIndex,false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.e(TAG, "1-3 === decode complete "+Thread.currentThread().getName());
                        decoder.stop();
                        decoder.release();
                    }

                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    e.printStackTrace();
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

                }
            },new Handler(codecHandlerThread.getLooper()));

            decoder.configure(mediaFormat, null, null, 0);
            decoder.start();

        } catch (Exception e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }

    private void encode(){

        try {

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            //视频信息配置
            MediaFormat format;

            if (videoRotation == 0 || videoRotation == 180){
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
            }else {
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoHeight, videoWidth);
            }

            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,videoWidth * videoHeight * 6);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);//i帧间隔


            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/zhh.mp4",
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoTrackIndex = mMediaMuxer.addTrack(mediaFormat);

            HandlerThread codecHandlerThread = new HandlerThread("encode");
            codecHandlerThread.start();

            encoder.setCallback(new MediaCodec.Callback() {
                boolean encodeInputDone = false;
                boolean encodeOutDone = false;
                @Override
                public void onInputBufferAvailable(@NonNull MediaCodec codec, int encodeInputIndex) {
                    Log.e(TAG, "2-1 === "+Thread.currentThread().getName()+" encoder.encodeInputIndex: "+encodeInputIndex);
                    if (encodeInputDone){
                        return;
                    }

                    while (decodeOutInfoQueue.size() == 0){
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }


                    ByteBuffer decodeOutBuffer = decodeOutQueue.poll();
                    MediaCodec.BufferInfo info = decodeOutInfoQueue.poll();
                    ByteBuffer encodeBuffer = encoder.getInputBuffer(encodeInputIndex);
                    encodeBuffer.clear();
                    encodeBuffer.position(0);
                    encodeBuffer.limit(info.size);
                    encodeBuffer.put(decodeOutBuffer);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoder.queueInputBuffer(encodeInputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        encodeInputDone = true;
                    }else {
                        encoder.queueInputBuffer(encodeInputIndex,0,info.size,info.presentationTimeUs,0);
                    }

                }

                @Override
                public void onOutputBufferAvailable(@NonNull MediaCodec codec, int encodeOutIndex, @NonNull MediaCodec.BufferInfo outputInfo) {

                    Log.e(TAG, "2-2 === "+Thread.currentThread().getName()+" encoder.encodeOutIndex: "+encodeOutIndex);

                    encodeOutDone = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    ByteBuffer encodeOutBuffer = encoder.getOutputBuffer(encodeOutIndex);
                    if (outputInfo.size != 0){
                        encodeOutBuffer.position(outputInfo.offset);
                        encodeOutBuffer.limit(outputInfo.offset + outputInfo.size);
                        mMediaMuxer.writeSampleData(videoTrackIndex,encodeOutBuffer,outputInfo);
                    }
                    encoder.releaseOutputBuffer(encodeOutIndex,false);
                    if (encodeOutDone){
                        if (encoder != null) {
                            encoder.stop();
                            encoder.release();
                            encoder = null;
                        }
                        if (mMediaMuxer != null){
                            mMediaMuxer.stop();
                            mMediaMuxer.release();
                        }
                        if (mediaExtractor != null){
                            mediaExtractor.release();
                            mediaExtractor = null;
                        }
                        ToastUtils.showShort("结束");
                        Log.e(TAG, "decodeThenEncode 耗时 " + (System.currentTimeMillis()  -startTime) + "ms" );
                    }

                }

                @Override
                public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                    e.printStackTrace();
                }

                @Override
                public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

                }
            },new Handler(codecHandlerThread.getLooper()));


            //设置配置信息给mediaCodec
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            encoder.start();

            mMediaMuxer.start();
        } catch (Exception e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }


    private void initCodec(String path){
        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(path);
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

        MediaMetadataRetriever retr = new MediaMetadataRetriever();
        retr.setDataSource(path);
        String width = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        int videoWidth = Integer.parseInt(width);
        int videoHeight = Integer.parseInt(height);
        int videoRotation = Integer.parseInt(rotation);


        try {
            this.decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            // 视频宽高暂时写死
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            decoder.configure(mediaFormat, null, null, 0);


            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            //视频信息配置
            MediaFormat format;

            if (videoRotation == 0 || videoRotation == 180){
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
            }else {
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoHeight, videoWidth);
            }

            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,videoWidth * videoHeight * 6);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);//i帧间隔
            //设置配置信息给mediaCodec
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/zhh.mp4",
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
        } catch (Exception e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }

    private void decodeThenEncode(){

        long startTime = System.currentTimeMillis();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        boolean done = false;
        boolean inputDone = false;
        boolean encodeDone = false;

        int encodeInputIndex = -1;

        int i =0;
        int count = 60;

        while (!done) {
            if (!inputDone){
                int inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                if (i<count){
                    Log.e(TAG, "1 === decoder.dequeueInputBuffer: "+inputIndex );
                }
                i++;
                if (inputIndex>=0){
                    ByteBuffer byteBuffer = decoder.getInputBuffer(inputIndex);
                    byteBuffer.clear();
                    int sampleDataSize = mediaExtractor.readSampleData(byteBuffer,0);
                    if (sampleDataSize>0){
                        decoder.queueInputBuffer(inputIndex,0,sampleDataSize,mediaExtractor.getSampleTime(),0);
                        mediaExtractor.advance();
                    }else {
                        decoder.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    }
                }
            }
            if (!encodeDone){
                if (encodeInputIndex < 0){
                    encodeInputIndex = encoder.dequeueInputBuffer(TIMEOUT_US);
                    Log.e(TAG, "2 === encoder.dequeueInputBuffer: "+encodeInputIndex);
                }
                if (encodeInputIndex >= 0){
                    int index = decoder.dequeueOutputBuffer(info,TIMEOUT_US);
                    if (i<count){
                        Log.e(TAG, "2 === decoder.dequeueOutputBuffer: "+index );
                    }
                    if (index >= 0){
                        ByteBuffer byteBuffer = decoder.getOutputBuffer(index);
                        ByteBuffer encodeBuffer = encoder.getInputBuffer(encodeInputIndex);
                        encodeBuffer.clear();
                        encodeBuffer.position(0);
                        Log.e(TAG, "info.size: "+info.size );
                        encodeBuffer.limit(info.size);
                        encodeBuffer.put(byteBuffer);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoder.queueInputBuffer(encodeInputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            encodeDone = true;
                        }else {
                            encoder.queueInputBuffer(encodeInputIndex,0,info.size,info.presentationTimeUs,0);
                        }
                        decoder.releaseOutputBuffer(index,false);
                        if (i<count){
                            Log.e(TAG, "2 === decoder.releaseOutputBuffer: "+index );
                        }
                        i--;
                        encodeInputIndex = -1;
                    }
                }
            }
            boolean encoderOutputAvailable = true;
            while (encoderOutputAvailable){
                int encodeOutIndex = encoder.dequeueOutputBuffer(outputInfo,TIMEOUT_US);
                if (i<count){
                    Log.e(TAG, "3 === encoder.dequeueOutputBuffer: "+encodeOutIndex );
                }
                if (encodeOutIndex == MediaCodec.INFO_TRY_AGAIN_LATER){
                    encoderOutputAvailable = false;
                }else if (encodeOutIndex>0){
                    done = (outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (done) {
                        encodeDone = true;
                        encoderOutputAvailable = false;
                    }
                    ByteBuffer encodeOutBuffer = encoder.getOutputBuffer(encodeOutIndex);
                    if (outputInfo.size != 0){
                        encodeOutBuffer.position(outputInfo.offset);
                        encodeOutBuffer.limit(outputInfo.offset + outputInfo.size);
                        mMediaMuxer.writeSampleData(videoTrackIndex,encodeOutBuffer,outputInfo);
                    }
                    encoder.releaseOutputBuffer(encodeOutIndex,false);
                }
            }
        }
        release();
        ToastUtils.showShort("结束");
        Log.e(TAG, "decodeThenEncode 耗时 " + (System.currentTimeMillis()  -startTime) + "ms" );
    }

    public void release() {
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
        if (mediaExtractor != null){
            mediaExtractor.release();
            mediaExtractor = null;
        }

    }

    public int[] getSize(){
        if (mediaFormat!= null){
            return new int[]{mediaFormat.getInteger("width"), mediaFormat.getInteger("height")};
        }
        return new int[]{0, 0};
    }

}
