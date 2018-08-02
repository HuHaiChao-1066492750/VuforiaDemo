/*===============================================================================
Copyright (c) 2016,2018 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback;

import android.app.Activity;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import com.huhaichao.vuforiademo.CloudRecoVideoPlay.CloudRecoVideoPlay;
import com.huhaichao.vuforiademo.CloudRecoVideoPlay.VideoPlaybackModel;
import com.huhaichao.vuforiademo.SampleApplication.SampleAppRenderer;
import com.huhaichao.vuforiademo.SampleApplication.SampleAppRendererControl;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationSession;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleMath;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleUtils;
import com.huhaichao.vuforiademo.SampleApplication.utils.Texture;
import com.vuforia.Device;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_STATE;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_TYPE;
import com.vuforia.ImageTarget;
import com.vuforia.Matrix44F;
import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.Vec2F;
import com.vuforia.Vec3F;
import com.vuforia.Vuforia;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VideoPlaybackRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl {
    private static final String LOGTAG = "VideoPlaybackRenderer";

    SampleApplicationSession vuforiaAppSession;
    SampleAppRenderer mSampleAppRenderer;

    // 特定的视频回放渲染
    private int videoPlaybackShaderID = 0;
    private int videoPlaybackVertexHandle = 0;
    private int videoPlaybackTexCoordHandle = 0;
    private int videoPlaybackMVPMatrixHandle = 0;
    private int videoPlaybackTexSamplerOESHandle = 0;

    public int videoPlaybackTextureID[] = new int[VideoPlayback.NUM_TARGETS]; //都是-1

    // 特定的关键帧和图标渲染
    private int keyframeShaderID = 0;
    private int keyframeVertexHandle = 0;
    private int keyframeTexCoordHandle = 0;
    private int keyframeMVPMatrixHandle = 0;
    private int keyframeTexSampler2DHandle = 0;

    // We cannot use the default texture coordinates of the quad since these
    // will change depending on the video itself
    private float videoQuadTextureCoords[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};

    //这个变量将保存变换的坐标（每个帧都改变）
    private float videoQuadTextureCoordsTransformedStones[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};
    private float videoQuadTextureCoordsTransformedChips[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};

    //目标跟踪尺寸
    public Vec3F targetPositiveDimensions[] = new Vec3F[VideoPlayback.NUM_TARGETS];//每次渲染都初始化

    private static int NUM_QUAD_VERTEX = 4;
    private static int NUM_QUAD_INDEX = 6;

    //位置 纹理
    private double quadVerticesArray[] = {-1.0f, -1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f};

    private double quadTexCoordsArray[] = {0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};

    private double quadNormalsArray[] = {0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,};

    private short quadIndicesArray[] = {0, 1, 2, 2, 3, 0};

    private Buffer quadVertices, quadTexCoords, quadIndices, quadNormals;

    private boolean mIsActive = false;//是否激活
    private Matrix44F tappingProjectionMatrix = null;

    private float[][] mTexCoordTransformationMatrix = null;
    //    private VideoPlayerHelper mVideoPlayerHelper[] = null;
//    private String mMovieName[] = null;//相当于 getMetaData
//    private MEDIA_TYPE mCanRequestType[] = null;//可以请求类型
//    private int mSeekPosition[] = null;//进度
//    private boolean mShouldPlayImmediately[] = null;//是否立即播放
//    private long mLostTrackingSince[] = null;//跟踪丢失的时间戳
    private boolean mLoadRequested[] = null;//是否加载请求

    private Activity mActivity;

    //需要计算屏幕触摸是否在目标内
    Matrix44F modelViewMatrix[] = new Matrix44F[VideoPlayback.NUM_TARGETS];

    private Vector<Texture> mTextures;//纹理

    boolean isTracking[] = new boolean[VideoPlayback.NUM_TARGETS];//是否在跟踪
//    public MEDIA_STATE currentStatus[] = new MEDIA_STATE[VideoPlayback.NUM_TARGETS];

    //保持视频数据和关键帧的 宽高比
    float videoQuadAspectRatio[] = new float[VideoPlayback.NUM_TARGETS];
    float keyframeAspectRatio;

    //========================新的自定义 start=======================
    private List<VideoPlaybackModel> videoPlaybackModelList;//存放ImageTarget(识别成功的)
    private List<VideoPlayerHelper> videoPlayerHelperList;//播放器助手列表
    private List<Integer> videoTextureIdList;//视频播放纹理Id

    //========================新的自定义  end========================
    public VideoPlaybackRenderer(Activity activity, SampleApplicationSession session) {
        mActivity = activity;
        vuforiaAppSession = session;

        //设置设备模式AR/VR和立体声模式。
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 0.01f, 5f);

        videoPlaybackModelList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            VideoPlaybackModel videoPlaybackModel = new VideoPlaybackModel();
            videoPlaybackModel.setUniqueTargetId("");
            videoPlaybackModelList.add(videoPlaybackModel);
        }
//        mVideoPlayerHelper = new VideoPlayerHelper[VideoPlayback.NUM_TARGETS];
//        mMovieName = new String[VideoPlayback.NUM_TARGETS];
//        mCanRequestType = new MEDIA_TYPE[VideoPlayback.NUM_TARGETS];
//        mSeekPosition = new int[VideoPlayback.NUM_TARGETS];
//        mShouldPlayImmediately = new boolean[VideoPlayback.NUM_TARGETS];
//        mLostTrackingSince = new long[VideoPlayback.NUM_TARGETS];
        mLoadRequested = new boolean[VideoPlayback.NUM_TARGETS];
        mTexCoordTransformationMatrix = new float[VideoPlayback.NUM_TARGETS][16];

        // Initialize the arrays to default values
        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
//            mVideoPlayerHelper[i] = null;
//            mMovieName[i] = "";
//            mCanRequestType[i] = MEDIA_TYPE.ON_TEXTURE_FULLSCREEN;
//            mSeekPosition[i] = 0;
//            mShouldPlayImmediately[i] = false;
//            mLostTrackingSince[i] = -1;
            mLoadRequested[i] = false;
        }

        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++)
            targetPositiveDimensions[i] = new Vec3F();

        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++)
            modelViewMatrix[i] = new Matrix44F();
    }

    public void setVideoPlayerHelper(int target, VideoPlayerHelper newVideoPlayerHelper) {
//        mVideoPlayerHelper[target] = newVideoPlayerHelper;
    }

    public void setVideoPlayerHelper(List<VideoPlayerHelper> videoPlayerHelperList) {
        this.videoPlayerHelperList = videoPlayerHelperList;
    }

    public void requestLoad(int target, String movieName, int seekPosition, boolean playImmediately) {
//        mMovieName[target] = movieName;
        videoPlaybackModelList.get(target).setCurrentPosition(seekPosition);
        videoPlaybackModelList.get(target).setPlayImmediately(playImmediately);
        mLoadRequested[target] = true;
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initRendering();

        Vuforia.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();

        // TODO: 2018/7/12
        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
            if (videoPlayerHelperList.get(i) != null) {
                //检查是否可以嵌入AR场景中播放
                if (!videoPlayerHelperList.get(i).setupSurfaceTexture(videoPlaybackTextureID[i])) {
                    videoPlaybackModelList.get(i).setMediaType(MEDIA_TYPE.FULLSCREEN);
                } else {
                    videoPlaybackModelList.get(i).setMediaType(MEDIA_TYPE.ON_TEXTURE_FULLSCREEN);
                }
            }
        }
    }

    //当表面改变大小时调用
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Vuforia.onSurfaceChanged(width, height);

        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        // 每次暂停都必须卸载电影以释放资源
        // surface表面创建和表面改变时重新加载
        //activity生命周期管理，在pause时卸载了视频数据
        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
            if (mLoadRequested[i] && videoPlayerHelperList.get(i) != null) {
                videoPlayerHelperList.get(i).load(videoPlaybackModelList.get(i).getMetaData(), videoPlaybackModelList.get(i).getMediaType(), videoPlaybackModelList.get(i).getPlayImmediately(), videoPlaybackModelList.get(i).getCurrentPosition());
                mLoadRequested[i] = false;
            }
        }
    }

    // 绘制当前帧时回调
    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mIsActive)
            return;

        for (int i = 0; i < CloudRecoVideoPlay.NUM_TARGETS; i++) {
            if (videoPlayerHelperList.get(i) != null) {
                //检查是否可以嵌入AR场景中播放
                if (videoPlayerHelperList.get(i).isPlayableOnTexture()) {
                    // First we need to update the video data. This is a built
                    // in Android call
                    // Here, the decoded data is uploaded to the OES texture
                    // We only need to do this if the movie is playing
                    if (videoPlayerHelperList.get(i).getStatus() == MEDIA_STATE.PLAYING) {
                        videoPlayerHelperList.get(i).updateVideoData();
                    }

                    // According to the Android API
                    // (http://developer.android.com/reference/android/graphics/SurfaceTexture.html)
                    // transforming the texture coordinates needs to happen
                    // every frame.
                    videoPlayerHelperList.get(i).getSurfaceTextureTransformMatrix(mTexCoordTransformationMatrix[i]);
                    setVideoDimensions(i,
                            videoPlayerHelperList.get(i).getVideoWidth(),
                            videoPlayerHelperList.get(i).getVideoHeight(),
                            mTexCoordTransformationMatrix[i]);
                }

                setStatus(i, videoPlayerHelperList.get(i).getStatus().getNumericType());
            }
        }

        //调用我们的函数来从SampleAppRenderer类中呈现内容
        mSampleAppRenderer.render();

        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
            //询问当前是否正在跟踪目标，如果是，则对其作出反应
            if (isTracking(i)) {
                //正在跟踪，所以时间戳为无效值
                videoPlaybackModelList.get(i).setLostTrackingSince(-1);
            } else {
                //获取跟踪丢失的时间戳
                if (videoPlaybackModelList.get(i).getLostTrackingSince() < 0)
                    videoPlaybackModelList.get(i).setLostTrackingSince(SystemClock.uptimeMillis());
                else {
                    // 超过两秒，则暂停播放器
                    if ((SystemClock.uptimeMillis() - videoPlaybackModelList.get(i).getLostTrackingSince()) > 2000) {
                        if (videoPlayerHelperList.get(i) != null) {
                            videoPlayerHelperList.get(i).pause();
                        }
                    }
                }
            }
        }
    }

    public void setActive(boolean active) {
        mIsActive = active;

        if (mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    private void initRendering() {
        // Define clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        // 创建关键帧和图标的纹理
        for (Texture t : mTextures) {
            GLES20.glGenTextures(1, t.mTextureID, 0);//生成一个纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);//绑定纹理Id
            //纹理贴图的取样方式，包括拉伸方式，取临近值和线性值
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        //创建视频数据的纹理
        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
            GLES20.glGenTextures(1, videoPlaybackTextureID, i);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoPlaybackTextureID[i]);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        //显示视频数据的着色器
        videoPlaybackShaderID = SampleUtils.createProgramFromShaderSrc(
                VideoPlaybackShaders.VIDEO_PLAYBACK_VERTEX_SHADER,
                VideoPlaybackShaders.VIDEO_PLAYBACK_FRAGMENT_SHADER);
        videoPlaybackVertexHandle = GLES20.glGetAttribLocation(videoPlaybackShaderID, "vertexPosition");
        videoPlaybackTexCoordHandle = GLES20.glGetAttribLocation(videoPlaybackShaderID, "vertexTexCoord");
        videoPlaybackMVPMatrixHandle = GLES20.glGetUniformLocation(videoPlaybackShaderID, "modelViewProjectionMatrix");
        videoPlaybackTexSamplerOESHandle = GLES20.glGetUniformLocation(videoPlaybackShaderID, "texSamplerOES");

        //关键帧的着色器
        keyframeShaderID = SampleUtils.createProgramFromShaderSrc(
                KeyFrameShaders.KEY_FRAME_VERTEX_SHADER,
                KeyFrameShaders.KEY_FRAME_FRAGMENT_SHADER);
        keyframeVertexHandle = GLES20.glGetAttribLocation(keyframeShaderID, "vertexPosition");
        keyframeTexCoordHandle = GLES20.glGetAttribLocation(keyframeShaderID, "vertexTexCoord");
        keyframeMVPMatrixHandle = GLES20.glGetUniformLocation(keyframeShaderID, "modelViewProjectionMatrix");
        keyframeTexSampler2DHandle = GLES20.glGetUniformLocation(keyframeShaderID, "texSampler2D");

        //保持视频数据和关键帧的宽高比
        keyframeAspectRatio = (float) mTextures.get(0).mHeight / (float) mTextures.get(0).mWidth;
//        keyframeAspectRatio[VideoPlayback.CHIPS] = (float) mTextures.get(1).mHeight / (float) mTextures.get(1).mWidth;

        quadVertices = fillBuffer(quadVerticesArray);
        quadTexCoords = fillBuffer(quadTexCoordsArray);
        quadIndices = fillBuffer(quadIndicesArray);
        quadNormals = fillBuffer(quadNormalsArray);
    }

    //double转float
    private Buffer fillBuffer(double[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length); //每个浮点占用4字节
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (double d : array) {
            bb.putFloat((float) d);
        }
        bb.rewind();
        return bb;
    }

    //short转float
    private Buffer fillBuffer(short[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(2 * array.length); //每个短整形占用2字节
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : array)
            bb.putShort(s);
        bb.rewind();
        return bb;
    }

    private Buffer fillBuffer(float[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length); // each float takes 4 bytes
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (float d : array)
            bb.putFloat(d);
        bb.rewind();
        return bb;
    }

    public void updateRenderingPrimitives() {
        mSampleAppRenderer.updateRenderingPrimitives();
    }

    // 开始渲染 ： mSampleAppRenderer.render();
    @Override
    public void renderFrame(State state, float[] projectionMatrix) {
        //呈现视频背景替换渲染器
        mSampleAppRenderer.renderVideoBackground(state);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        if (tappingProjectionMatrix == null) {
            tappingProjectionMatrix = new Matrix44F();
            tappingProjectionMatrix.setData(projectionMatrix);
        }

        float temp[] = {0.0f, 0.0f, 0.0f};
        for (int i = 0; i < VideoPlayback.NUM_TARGETS; i++) {
            isTracking[i] = false;
            targetPositiveDimensions[i].setData(temp);
        }

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult trackableResult = state.getTrackableResult(tIdx);
            ImageTarget imageTarget = (ImageTarget) trackableResult.getTrackable();

            int currentTarget;

            //ImageTarget:
            //getUniqueTargetId：识别图Id，作为唯一主键
            //getMetaData：上传的文本数据
            //getName：识别图名称
            if (imageTarget.getName().compareTo("stones") == 0) {
                currentTarget = VideoPlayback.STONES;
            } else {
                currentTarget = VideoPlayback.CHIPS;
                if (!videoPlaybackModelList.get(currentTarget).getUniqueTargetId().equals(imageTarget.getUniqueTargetId())) {
//                    VideoPlaybackModel videoModel=new VideoPlaybackModel();
//                    videoModel.setUniqueTargetId(imageTarget.getUniqueTargetId());
//                    videoPlaybackModelList.add(videoModel);
                    videoPlaybackModelList.get(currentTarget).setUniqueTargetId(imageTarget.getUniqueTargetId());
                    videoPlaybackModelList.get(currentTarget).setMetaData(imageTarget.getMetaData());
                    videoPlayerHelperList.get(currentTarget).load(videoPlaybackModelList.get(currentTarget).getMetaData(),
                            videoPlaybackModelList.get(currentTarget).getMediaType(), videoPlaybackModelList.get(currentTarget).getPlayImmediately(),
                            videoPlaybackModelList.get(currentTarget).getCurrentPosition());
                    mLoadRequested[currentTarget] = true;
                    Log.d("hhc", "getMetaData= " + imageTarget.getMetaData());
                    Log.d("hhc", "getName= " + imageTarget.getName());
                    Log.d("hhc", "getId= " + imageTarget.getId());
                    Log.d("hhc", "getType=" + imageTarget.getType());
                    Log.d("hhc", "getUniqueTargetId= " + imageTarget.getUniqueTargetId());
                    Log.d("hhc", "getUserData= " + imageTarget.getUserData());
                } else {

                }
            }
            modelViewMatrix[currentTarget] = Tool.convertPose2GLMatrix(trackableResult.getPose());

            isTracking[currentTarget] = true;

            targetPositiveDimensions[currentTarget] = imageTarget.getSize();

            //宽度/2，高度相同
            temp[0] = targetPositiveDimensions[currentTarget].getData()[0] / 2.0f;
            temp[1] = targetPositiveDimensions[currentTarget].getData()[1] / 2.0f;
            targetPositiveDimensions[currentTarget].setData(temp);

            //如果电影已经准备好播放，或者它已经结束播放，我们渲染关键帧。
            if ((videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.READY)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.REACHED_END)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.NOT_READY)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.ERROR)) {
                float[] modelViewMatrixKeyframe = Tool.convertPose2GLMatrix(trackableResult.getPose()).getData();
                float[] modelViewProjectionKeyframe = new float[16];

                //这里我们使用关键帧的纵横比，因为它很可能不是完美的正方形
                float ratio = 1.0f;
                if (mTextures.get(currentTarget).mSuccess) {
                    ratio = keyframeAspectRatio;
                } else {
                    ratio = targetPositiveDimensions[currentTarget].getData()[1] / targetPositiveDimensions[currentTarget].getData()[0];
                }

                Matrix.scaleM(modelViewMatrixKeyframe, 0,
                        targetPositiveDimensions[currentTarget].getData()[0],
                        targetPositiveDimensions[currentTarget].getData()[0] * ratio,
                        targetPositiveDimensions[currentTarget].getData()[0]);
                Matrix.multiplyMM(modelViewProjectionKeyframe, 0,
                        projectionMatrix, 0, modelViewMatrixKeyframe, 0);

                GLES20.glUseProgram(keyframeShaderID);

                //准备渲染关键帧
                GLES20.glVertexAttribPointer(keyframeVertexHandle, 3, GLES20.GL_FLOAT, false, 0, quadVertices);
                GLES20.glVertexAttribPointer(keyframeTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords);

                GLES20.glEnableVertexAttribArray(keyframeVertexHandle);
                GLES20.glEnableVertexAttribArray(keyframeTexCoordHandle);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                // TODO: 2018/7/25
                // 传入的textures的mTextures.get(currentTarget)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(currentTarget).mTextureID[0]);
                GLES20.glUniformMatrix4fv(keyframeMVPMatrixHandle, 1, false, modelViewProjectionKeyframe, 0);
                GLES20.glUniform1i(keyframeTexSampler2DHandle, 0);

                //渲染
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX, GLES20.GL_UNSIGNED_SHORT, quadIndices);

                GLES20.glDisableVertexAttribArray(keyframeVertexHandle);
                GLES20.glDisableVertexAttribArray(keyframeTexCoordHandle);

                GLES20.glUseProgram(0);
            } else {
                // 在任何其他情况下，例如播放或暂停，我们呈现实际内容
                float[] modelViewMatrixVideo = Tool.convertPose2GLMatrix(
                        trackableResult.getPose()).getData();
                float[] modelViewProjectionVideo = new float[16];

                //这里我们使用视频帧的长宽比
                Matrix.scaleM(modelViewMatrixVideo, 0,
                        targetPositiveDimensions[currentTarget].getData()[0],
                        targetPositiveDimensions[currentTarget].getData()[0] * videoQuadAspectRatio[currentTarget],
                        targetPositiveDimensions[currentTarget].getData()[0]);
                Matrix.multiplyMM(modelViewProjectionVideo, 0, projectionMatrix, 0, modelViewMatrixVideo, 0);

                GLES20.glUseProgram(videoPlaybackShaderID);

                // 准备渲染关键帧
                GLES20.glVertexAttribPointer(videoPlaybackVertexHandle, 3, GLES20.GL_FLOAT, false, 0, quadVertices);

                if (imageTarget.getName().compareTo("stones") == 0) {
                    GLES20.glVertexAttribPointer(videoPlaybackTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0,
                            fillBuffer(videoQuadTextureCoordsTransformedStones));
                } else {
                    GLES20.glVertexAttribPointer(videoPlaybackTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0,
                            fillBuffer(videoQuadTextureCoordsTransformedChips));
                }
                GLES20.glEnableVertexAttribArray(videoPlaybackVertexHandle);
                GLES20.glEnableVertexAttribArray(videoPlaybackTexCoordHandle);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                //绑定纹理
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoPlaybackTextureID[currentTarget]);
                GLES20.glUniformMatrix4fv(videoPlaybackMVPMatrixHandle, 1, false, modelViewProjectionVideo, 0);
                GLES20.glUniform1i(videoPlaybackTexSamplerOESHandle, 0);

                // Render
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX,
                        GLES20.GL_UNSIGNED_SHORT, quadIndices);

                GLES20.glDisableVertexAttribArray(videoPlaybackVertexHandle);
                GLES20.glDisableVertexAttribArray(videoPlaybackTexCoordHandle);

                GLES20.glUseProgram(0);
            }

            //下面的部分呈现图标。所使用的实际纹理是从资产文件夹中加载的
            if ((videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.READY)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.REACHED_END)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.PAUSED)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.NOT_READY)
                    || (videoPlaybackModelList.get(currentTarget).getMediaState() == VideoPlayerHelper.MEDIA_STATE.ERROR)) {
                //如果电影已准备好播放、暂停、已经结束或尚未准备好，那么我们将显示其中一个图标。
                float[] modelViewMatrixButton = Tool.convertPose2GLMatrix(trackableResult.getPose()).getData();
                float[] modelViewProjectionButton = new float[16];

                GLES20.glDepthFunc(GLES20.GL_LEQUAL);

                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                Matrix.translateM(modelViewMatrixButton, 0, 0.0f, 0.0f,
                        targetPositiveDimensions[currentTarget].getData()[1] / 10.98f);
                Matrix.scaleM(modelViewMatrixButton, 0,
                        (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f),
                        (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f),
                        (targetPositiveDimensions[currentTarget].getData()[1] / 2.0f));
                Matrix.multiplyMM(modelViewProjectionButton, 0,
                        projectionMatrix, 0, modelViewMatrixButton, 0);

                GLES20.glUseProgram(keyframeShaderID);

                GLES20.glVertexAttribPointer(keyframeVertexHandle, 3,
                        GLES20.GL_FLOAT, false, 0, quadVertices);
                GLES20.glVertexAttribPointer(keyframeTexCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, quadTexCoords);

                GLES20.glEnableVertexAttribArray(keyframeVertexHandle);
                GLES20.glEnableVertexAttribArray(keyframeTexCoordHandle);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                //根据我们所处的状态，我们选择适当的纹理来显示
                switch (videoPlaybackModelList.get(currentTarget).getMediaState()) {
                    case READY:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(2).mTextureID[0]);
                        break;
                    case REACHED_END:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(2).mTextureID[0]);
                        break;
                    case PAUSED:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(2).mTextureID[0]);
                        break;
                    case NOT_READY:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(3).mTextureID[0]);
                        break;
                    case ERROR:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(4).mTextureID[0]);
                        break;
                    default:
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(3).mTextureID[0]);
                        break;
                }
                GLES20.glUniformMatrix4fv(keyframeMVPMatrixHandle, 1, false,
                        modelViewProjectionButton, 0);
                GLES20.glUniform1i(keyframeTexSampler2DHandle, 0);

                //渲染
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, NUM_QUAD_INDEX, GLES20.GL_UNSIGNED_SHORT, quadIndices);

                GLES20.glDisableVertexAttribArray(keyframeVertexHandle);
                GLES20.glDisableVertexAttribArray(keyframeTexCoordHandle);

                GLES20.glUseProgram(0);

                //最后，我们将深度FUNC返回到它的初始状态。
                GLES20.glDepthFunc(GLES20.GL_LESS);
                GLES20.glDisable(GLES20.GL_BLEND);
            }
            SampleUtils.checkGLError("VideoPlayback renderFrame");
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        Renderer.getInstance().end();
    }

    //这里我们计算触摸事件在目标之内。
    public boolean isTapOnScreenInsideTarget(int target, float x, float y) {
        Vec3F intersection;

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        intersection = SampleMath.getPointToPlaneIntersection(SampleMath.Matrix44FInverse(tappingProjectionMatrix),
                modelViewMatrix[target], metrics.widthPixels, metrics.heightPixels,
                new Vec2F(x, y), new Vec3F(0, 0, 0), new Vec3F(0, 0, 1));

        // The target returns as pose the center of the trackable. The following
        // if-statement simply checks that the tap is within this range
        if ((intersection.getData()[0] >= -(targetPositiveDimensions[target].getData()[0]))
                && (intersection.getData()[0] <= (targetPositiveDimensions[target].getData()[0]))
                && (intersection.getData()[1] >= -(targetPositiveDimensions[target].getData()[1]))
                && (intersection.getData()[1] <= (targetPositiveDimensions[target].getData()[1]))) {
            return true;
        } else {
            return false;
        }
    }

    public void setVideoDimensions(int target, float videoWidth, float videoHeight, float[] textureCoordMatrix) {
        // The quad originaly comes as a perfect square, however, the video
        // often has a different aspect ration such as 4:3 or 16:9,
        // To mitigate this we have two options:
        // 1) We can either scale the width (typically up)
        // 2) We can scale the height (typically down)
        // Which one to use is just a matter of preference. This example scales
        // the height down.
        // (see the render call in renderFrame)
        videoQuadAspectRatio[target] = videoHeight / videoWidth;

        float mtx[] = textureCoordMatrix;
        float tempUVMultRes[] = new float[2];

        if (target == VideoPlayback.STONES) {
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedStones[0],
                    videoQuadTextureCoordsTransformedStones[1],
                    videoQuadTextureCoords[0], videoQuadTextureCoords[1], mtx);
            videoQuadTextureCoordsTransformedStones[0] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedStones[1] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedStones[2],
                    videoQuadTextureCoordsTransformedStones[3],
                    videoQuadTextureCoords[2], videoQuadTextureCoords[3], mtx);
            videoQuadTextureCoordsTransformedStones[2] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedStones[3] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedStones[4],
                    videoQuadTextureCoordsTransformedStones[5],
                    videoQuadTextureCoords[4], videoQuadTextureCoords[5], mtx);
            videoQuadTextureCoordsTransformedStones[4] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedStones[5] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedStones[6],
                    videoQuadTextureCoordsTransformedStones[7],
                    videoQuadTextureCoords[6], videoQuadTextureCoords[7], mtx);
            videoQuadTextureCoordsTransformedStones[6] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedStones[7] = tempUVMultRes[1];
        } else if (target == VideoPlayback.CHIPS) {
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedChips[0],
                    videoQuadTextureCoordsTransformedChips[1],
                    videoQuadTextureCoords[0], videoQuadTextureCoords[1], mtx);
            videoQuadTextureCoordsTransformedChips[0] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedChips[1] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedChips[2],
                    videoQuadTextureCoordsTransformedChips[3],
                    videoQuadTextureCoords[2], videoQuadTextureCoords[3], mtx);
            videoQuadTextureCoordsTransformedChips[2] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedChips[3] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedChips[4],
                    videoQuadTextureCoordsTransformedChips[5],
                    videoQuadTextureCoords[4], videoQuadTextureCoords[5], mtx);
            videoQuadTextureCoordsTransformedChips[4] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedChips[5] = tempUVMultRes[1];
            tempUVMultRes = uvMultMat4f(
                    videoQuadTextureCoordsTransformedChips[6],
                    videoQuadTextureCoordsTransformedChips[7],
                    videoQuadTextureCoords[6], videoQuadTextureCoords[7], mtx);
            videoQuadTextureCoordsTransformedChips[6] = tempUVMultRes[0];
            videoQuadTextureCoordsTransformedChips[7] = tempUVMultRes[1];
        }
    }

    // Multiply the UV coordinates by the given transformation matrix
    public float[] uvMultMat4f(float transformedU, float transformedV, float u, float v, float[] pMat) {
        float x = pMat[0] * u + pMat[4] * v /* + pMat[ 8]*0.f */ + pMat[12] * 1.f;
        float y = pMat[1] * u + pMat[5] * v /* + pMat[ 9]*0.f */ + pMat[13] * 1.f;

        float result[] = new float[2];
        result[0] = x;
        result[1] = y;
        return result;
    }

    public void setStatus(int target, int value) {
        switch (value) {
            case 0:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.REACHED_END);
                break;
            case 1:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.PAUSED);
                break;
            case 2:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.STOPPED);
                break;
            case 3:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.PLAYING);
                break;
            case 4:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.READY);
                break;
            case 5:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.NOT_READY);
                break;
            case 6:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.ERROR);
                break;
            default:
                videoPlaybackModelList.get(target).setMediaState(VideoPlayerHelper.MEDIA_STATE.NOT_READY);
                break;
        }
    }

    //是否在跟踪
    public boolean isTracking(int target) {
        return isTracking[target];
    }

    public void setTextures(Vector<Texture> textures) {
        mTextures = textures;
    }
}
