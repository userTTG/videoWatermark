package com.zhh.zhhcamera.test;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.blankj.utilcode.util.ToastUtils;

import java.io.FileWriter;
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
public class DecodeThenEncode{

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
    int videoRotation = 0;

    private ReentrantLock mLock = new ReentrantLock();

    private OnDecodeListener onDecodeListener;

    public DecodeThenEncode() {

    }

    public void setOnDecodeListener(OnDecodeListener onDecodeListener) {
        this.onDecodeListener = onDecodeListener;
    }

    public void setPath(String path){
        initCodec(path);
        start();
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
        String bitrate = retr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
        int videoWidth = Integer.parseInt(width);
        int videoHeight = Integer.parseInt(height);
        int videoBitrate = Integer.parseInt(bitrate);
        videoRotation = Integer.parseInt(rotation);

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

//            if (videoRotation == 0 || videoRotation == 180){
//                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
//            }else {
//                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoHeight, videoWidth);
//            }

            format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);

            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            int maxSize = videoWidth*videoHeight*8;
            Log.e(TAG, "0 === KEY_MAX_INPUT_SIZE: "+maxSize);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,maxSize);
            //极低码率 宽*高*3/4
            //低码率 宽*高*3/2
            //中码率 宽*高*3
            //高码率 宽*高*3*2
            //极高码率 宽*高*3*4
            Log.e(TAG, "0 === KEY_BIT_RATE: "+videoBitrate+"/"+(videoHeight*videoWidth*6));
            format.setInteger(MediaFormat.KEY_BIT_RATE,videoHeight*videoWidth*6);//比特率
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);//i帧间隔
            //设置配置信息给mediaCodec
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

            mMediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory()+"/zhh.mp4",
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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

        ToastUtils.showShort("开始");

        new Thread(this::decodeThenEncode).start();
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
                        Image image = decoder.getOutputImage(index);
                        Rect rect = image.getCropRect();
                        int imageFormat = image.getFormat();
                        byte[] i420bytes = VideoUtil.getDataFromImage(image, VideoUtil.COLOR_FormatI420);
                        decoder.getOutputImage(index);
                        ByteBuffer encodeBuffer = encoder.getInputBuffer(encodeInputIndex);
                        encodeBuffer.clear();
                        encodeBuffer.position(0);
                        //24883200
                        Log.e(TAG, "2 === ByteBuffer size: "+encodeBuffer.remaining() + "/"+i420bytes.length +",image format "+imageFormat);
                        encodeBuffer.put(i420bytes);

                        if (onDecodeListener != null){
                            byte[] nv21 = VideoUtil.I420Tonv21(i420bytes,rect.width(),rect.height());
                            onDecodeListener.onOutput(VideoUtil.getBitmapImageFromYUV(nv21,rect.width(),rect.height()));
                        }

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
                    }else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat newFormat = decoder.getOutputFormat();
                        Log.e(TAG, "2 === decodeThenEncode format: " + newFormat.toString() );
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
                } else if (encodeOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat newFormat = encoder.getOutputFormat();
//                byte[] header_sps = {0, 0, 0, 1, 39, 100, 0, 31, -84, 86, -64, -120, 30, 105, -88, 8, 8, 8, 16};
//                byte[] header_pps = {0, 0, 0, 1, 40, -18, 60, -80};
//                encodeMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//                encodeMediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
//                encodeVideoTrackIndex = mMediaMuxer.addTrack(encodeMediaFormat);

//                    newFormat.setInteger(MediaFormat.KEY_WIDTH,640);
//                    newFormat.setInteger(MediaFormat.KEY_HEIGHT,300);
                    newFormat.setInteger(MediaFormat.KEY_ROTATION,videoRotation);
                    videoTrackIndex = mMediaMuxer.addTrack(newFormat);
                    mMediaMuxer.start();
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
        if (mMediaMuxer != null){
            mMediaMuxer.stop();
            mMediaMuxer.release();
        }

    }

    public int[] getSize(){
        if (mediaFormat!= null){
            return new int[]{mediaFormat.getInteger("width"), mediaFormat.getInteger("height")};
        }
        return new int[]{0, 0};
    }

    public static interface OnDecodeListener{
        public void onOutput(Bitmap bitmap);
    }

}
