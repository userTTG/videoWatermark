package com.zhh.zhhcamera.test;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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

    private ReentrantLock mLock = new ReentrantLock();

    public DecodeThenEncode() {

    }

    public void setPath(String path){
        initCodec(path);
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

        try {
            this.decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);

            // 视频宽高暂时写死
//            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720);
//            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            decoder.configure(mediaFormat, null, null, 0);


            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            //视频信息配置
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mediaFormat.getInteger("width"), mediaFormat.getInteger("height"));
            //颜色
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,mediaFormat.getInteger("frame-rate"));//帧数
            format.setInteger(MediaFormat.KEY_BIT_RATE,5000000);//比特率
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


    public void start() {
        if (decoder == null){
            ToastUtils.showShort("未知错");
            return;
        }
        decoder.start();
        encoder.start();
        mMediaMuxer.start();

        ToastUtils.showShort("开始");

        new Thread(this::decodeThenEncode).start();
    }

    private void decodeThenEncode(){
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
    }

    private void encodeH264(){
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (true){
            mLock.lock();
            try {
                if (encoder != null){
                    //获取可用ByteBuffer下标
                    int index = encoder.dequeueOutputBuffer(bufferInfo, -1);
                    if(index>=0){
                        ByteBuffer buffer = encoder.getOutputBuffer(index);
                        byte[] outData=new byte[bufferInfo.size];
                        Log.e(TAG, "encodeH264: "+bufferInfo.size );
                        //给outData设置数据
                        buffer.get(outData);
                        //写入文件
                        mMediaMuxer.writeSampleData(videoTrackIndex,buffer,bufferInfo);
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
