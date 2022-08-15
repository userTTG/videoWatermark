package com.zhh.zhhcamera.test;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import com.blankj.utilcode.util.ToastUtils;
import com.zhh.zhhcamera.gl.InputSurface;
import com.zhh.zhhcamera.gl.OutputSurface;
import com.zhh.zhhcamera.gl.VideoInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @ClassName DecodeThenEncode
 * @Description TODO
 * @Author zhangh-be
 * @Date 2022/8/11 17:42
 * @Version 1.0
 */
public class DecodeSurfaceEncode {

    private static final String TAG = "DecodeThenEncode";

    private final int TIMEOUT_US = 0;

    // 本地 h264 文件路径
    private MediaExtractor mediaExtractor;
    private MediaCodec decoder;
    private MediaCodec encoder;
    private MediaFormat mediaFormat;
    private MediaMuxer mMediaMuxer;
    private long timeOfFrame = 30;
    private int videoTrackIndex;

    private OutputSurface outputSurface = null;
    private InputSurface inputSurface = null;
    private long firstVideoTime=0;

    private ReentrantLock mLock = new ReentrantLock();

    public DecodeSurfaceEncode() {

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
            firstVideoTime = mediaExtractor.getSampleTime();
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaMetadataRetriever retr = new MediaMetadataRetriever();
        retr.setDataSource(path);
        String width = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String height = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String rotation = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

        VideoInfo info = new VideoInfo();

        int videoWidth = info.width = Integer.parseInt(width);
        int videoHeight = info.height = Integer.parseInt(height);
        int videoRotation = info.rotation = Integer.parseInt(rotation);


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
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,videoWidth * videoHeight * 6);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);//i帧间隔
            //设置配置信息给mediaCodec
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();


            this.decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            // 视频宽高暂时写死
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);

            outputSurface = new OutputSurface(info);

//            TestRender render = new TestRender();
//            render.setVideoSize(info.width,info.height);
//            render.onSurfaceCreated(null,null);
//            render.onSurfaceChanged(null,info.width,info.height);

            //new Surface(render.getSurfaceTexture())

            decoder.configure(mediaFormat, outputSurface.getSurface(), null, 0);

            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/zhh.mp4",
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
        } catch (Exception e) {
            // 解码芯片不支持，走软解
            e.printStackTrace();
        }
    }


    public void start() {
        if (decoder == null){
            ToastUtils.showShort("未知错");
            return;
        }
        decoder.start();
        encoder.start();
        mMediaMuxer.start();

        ToastUtils.showShort("开始");
    }

    private void decodeThenEncode(){
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        boolean done = false;
        boolean inputDone = false;
        boolean encodeDone = false;

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
                int index = decoder.dequeueOutputBuffer(info,TIMEOUT_US);
                if (i<count){
                    Log.e(TAG, "2 === decoder.dequeueOutputBuffer: "+index );
                }
                if (index >= 0){
                    boolean doRender = info.size !=0 && info.presentationTimeUs - firstVideoTime>0;
                    decoder.releaseOutputBuffer(index, doRender);
                    if (doRender){
                        // This waits for the image and renders it after it arrives.
                        outputSurface.awaitNewImage();
                        outputSurface.drawImage();
                        // Send it to the encoder.
                        inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                        inputSurface.swapBuffers();
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoder.signalEndOfInputStream();
                        encodeDone = true;
                    }
                    i--;
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
                    if (outputInfo.presentationTimeUs != 0 && outputInfo.size != 0){
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
