package com.huhaichao.vuforiademo.CloudRecoVideoPlay;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.huhaichao.vuforiademo.SampleApplication.SampleAppRenderer;
import com.huhaichao.vuforiademo.SampleApplication.SampleAppRendererControl;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationSession;
import com.huhaichao.vuforiademo.SampleApplication.utils.CubeShaders;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleMath;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleUtils;
import com.huhaichao.vuforiademo.SampleApplication.utils.Teapot;
import com.huhaichao.vuforiademo.SampleApplication.utils.Texture;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_TYPE;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_STATE;
import com.vuforia.Device;
import com.vuforia.ImageTarget;
import com.vuforia.ImageTargetResult;
import com.vuforia.Matrix44F;
import com.vuforia.ObjectTracker;
import com.vuforia.State;
import com.vuforia.TargetFinder;
import com.vuforia.TargetSearchResult;
import com.vuforia.Tool;
import com.vuforia.TrackableResult;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Administrator on 2018/7/23.
 */

public class CloudRecoVideoPlayRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl {
    private final SampleApplicationSession vuforiaAppSession;
    private final SampleAppRenderer mSampleAppRenderer;

    private static final float OBJECT_SCALE_FLOAT = 0.003f;

    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;

    private Vector<Texture> mTextures;

    private Teapot mTeapot;

    private final CloudRecoVideoPlay mActivity;

    private boolean mIsActive = false;

    private VideoPlayerHelper mVideoPlayerHelper;//播放器助手
    private int[] videoTextureID = new int[1];
    private MEDIA_TYPE mCanRequestType = null;
    private boolean mShouldPlayImmediately = false;
    private boolean mLoadRequested = false;//是否加载
    private int mSeekPosition = 0;
    private String videoUrl = "";//播放地址

    CloudRecoVideoPlayRenderer(SampleApplicationSession session, CloudRecoVideoPlay activity) {
        vuforiaAppSession = session;

        mActivity = activity;

        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 0.010f, 5f);

        //初始化视频的纹理
        videoTextureID[0] = -1;
    }

    public void setVideoPlayerHelper(VideoPlayerHelper videoPlayerHelper) {
        this.mVideoPlayerHelper = videoPlayerHelper;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initRendering();

        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();

        if (mVideoPlayerHelper != null) {
            if (!mVideoPlayerHelper.setupSurfaceTexture(videoTextureID[0])) {
                mCanRequestType = MEDIA_TYPE.FULLSCREEN;
            } else {
                mCanRequestType = MEDIA_TYPE.ON_TEXTURE_FULLSCREEN;
            }
        }

        //加载视频数据
        if (mLoadRequested) {
            mVideoPlayerHelper.load(videoUrl, mCanRequestType, mShouldPlayImmediately, mSeekPosition);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        vuforiaAppSession.onSurfaceChanged(width, height);

        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        //视频数据重新加载
        if (mLoadRequested && mVideoPlayerHelper != null) {
            mVideoPlayerHelper.load(videoUrl, mCanRequestType, mShouldPlayImmediately, mSeekPosition);
            mLoadRequested = false;
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mIsActive)
            return;
        if (mVideoPlayerHelper != null) {
            if (mVideoPlayerHelper.isPlayableOnTexture()) {
                if (mVideoPlayerHelper.getStatus() == MEDIA_STATE.PLAYING) {
                    mVideoPlayerHelper.updateVideoData();
                }

            }
        }
        mSampleAppRenderer.render();
    }


    public void setActive(boolean active) {
        mIsActive = active;

        if (mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }

    //初始化渲染器renderer
    private void initRendering() {

    }

    public void updateRenderingPrimitives() {
        mSampleAppRenderer.updateRenderingPrimitives();
    }

    //渲染展示
    @Override
    public void renderFrame(State state, float[] projectionMatrix) {

    }

    public void setTextures(Vector<Texture> textures) {
        mTextures = textures;
    }

//    //初始化渲染器renderer
//    private void initRendering() {
//        // Define clear color
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
//                : 1.0f);
//
//        //纹理的生成与绑定纹理Id
//        for (Texture t : mTextures) {
//            GLES20.glGenTextures(1, t.mTextureID, 0);
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
//            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
//                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
//                    GLES20.GL_UNSIGNED_BYTE, t.mData);
//        }
//
//        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
//                CubeShaders.CUBE_MESH_VERTEX_SHADER,
//                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);
//
//        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
//                "vertexPosition");
//        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
//                "vertexTexCoord");
//        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
//                "modelViewProjectionMatrix");
//        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
//                "texSampler2D");
//        mTeapot = new Teapot();
//    }

//    //渲染展示
//    @Override
//    public void renderFrame(State state, float[] projectionMatrix) {
//        // Renders video background replacing Renderer.DrawVideoBackground()
//        mSampleAppRenderer.renderVideoBackground(state);
//
//        // Set the device pose matrix as identity
//        Matrix44F devicePoseMatrix = SampleMath.Matrix44FIdentity();
//        Matrix44F modelMatrix;
//
//        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
//        GLES20.glEnable(GLES20.GL_CULL_FACE);
//
//        // Start the target finder if we can't find an Image Target result
//        // If the Device pose exists, we can assume we will receive two
//        // Trackable Results if the ImageTargetResult is available:
//        // ImageTargetResult and DeviceTrackableResult
//        int numExpectedResults = state.getDeviceTrackableResult() == null ? 0 : 1;
//        if (state.getNumTrackableResults() <= numExpectedResults) {
//            mActivity.startFinderIfStopped();
//        }
//
//
//        // Read device pose from the state and create a corresponding view matrix (inverse of the device pose)
//        if (state.getDeviceTrackableResult() != null
//                && state.getDeviceTrackableResult().getStatus() != TrackableResult.STATUS.NO_POSE) {
//            modelMatrix = Tool.convertPose2GLMatrix(state.getDeviceTrackableResult().getPose());
//
//            // We transpose here because Matrix44FInverse returns a transposed matrix
//            devicePoseMatrix = SampleMath.Matrix44FTranspose(SampleMath.Matrix44FInverse(modelMatrix));
//        }
//
//        //渲染展示
//        // Did we find any trackables this frame?
//        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
//            TrackableResult result = state.getTrackableResult(tIdx);
//            //====hhc
//            ImageTarget imageTarget = (ImageTarget) result.getTrackable();
//            Log.d("hhc", "getName123: "+imageTarget.getName());
//            Log.d("hhc", "getMetaData456: "+imageTarget.getMetaData());
//            //====hhc
//            modelMatrix = Tool.convertPose2GLMatrix(result.getPose());
//
//            if (result.isOfType(ImageTargetResult.getClassType())) {
//                TrackerManager trackerManager = TrackerManager.getInstance();
//                ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());
//                TargetFinder finder = objectTracker.getTargetFinder();
//                if (finder.getResultCount() > 0) {
//                    TargetSearchResult result1 = finder.getResult(0);
//                    Log.d("hhc", "getMetaData= " + result1.getMetaData());
//                }
//                // TODO: 2018/7/13
////                mActivity.stopFinderIfStarted();
//
//                // Renders the augmentation
//                renderModel(projectionMatrix, devicePoseMatrix.getData(), modelMatrix.getData());
//
//                SampleUtils.checkGLError("CloudReco renderFrame");
//            }
//        }
//
//        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//
////        Renderer.getInstance().end();
//    }
//
//
//    private void renderModel(float[] projectionMatrix, float[] viewMatrix, float[] modelMatrix) {
//        int textureIndex = 0;
//        float[] modelViewProjection = new float[16];
//
//        // Apply local transformation to our model
//        Matrix.translateM(modelMatrix, 0, 0.0f, 0.0f, OBJECT_SCALE_FLOAT);
//        Matrix.scaleM(modelMatrix, 0, OBJECT_SCALE_FLOAT,
//                OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);
//
//        // Combine device pose (view matrix) with model matrix
//        Matrix.multiplyMM(modelMatrix, 0, viewMatrix, 0, modelMatrix, 0);
//
//        // Do the final combination with the projection matrix
//        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelMatrix, 0);
//
//        // activate the shader program and bind the vertex/normal/tex coords
//        GLES20.glUseProgram(shaderProgramID);
//        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
//                0, mTeapot.getVertices());
//        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT,
//                false, 0, mTeapot.getTexCoords());
//
//        GLES20.glEnableVertexAttribArray(vertexHandle);
//        GLES20.glEnableVertexAttribArray(textureCoordHandle);
//
//        // activate texture 0, bind it, and pass to shader
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
//                mTextures.get(textureIndex).mTextureID[0]);
//        GLES20.glUniform1i(texSampler2DHandle, 0);
//
//        // pass the model view matrix to the shader
//        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
//                modelViewProjection, 0);
//
//        // finally draw the teapot
//        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mTeapot.getNumObjectIndex(),
//                GLES20.GL_UNSIGNED_SHORT, mTeapot.getIndices());
//
//        // disable the enabled arrays
//        GLES20.glDisableVertexAttribArray(vertexHandle);
//        GLES20.glDisableVertexAttribArray(textureCoordHandle);
//    }
}
