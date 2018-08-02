package com.huhaichao.vuforiademo.CloudRecoVideoPlay;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationControl;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationException;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationSession;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleApplicationGLView;
import com.huhaichao.vuforiademo.SampleApplication.utils.Texture;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlaybackRenderer;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.FUSION_PROVIDER_TYPE;
import com.vuforia.HINT;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.TargetFinder;
import com.vuforia.TargetSearchResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class CloudRecoVideoPlay extends Activity implements SampleApplicationControl {
    private static final String TAG = "CloudRecoVideoPlay";
    private boolean mFinderStarted = false;

    //    private static final String kAccessKey = "7f0e89e71629c9504a8ebe5b5086d50bf7281e81";
    //    private static final String kSecretKey = "f164075a59d802f12caa27337399515af29ee007";
    private static final String kAccessKey = "314c319f560f7ce64e4394506602a349a012580b";
    private static final String kSecretKey = "7705b84d2f517923ca10e6b3063848fd7becda6e";
    private boolean mIsDroidDevice = false;


    //=================================================start========================================
    private SampleApplicationSession vuforiaAppSession;
    public static final int NUM_TARGETS = 2;
    private RelativeLayout mUILayout;
    private DataSet dataSet = null;
    private SampleApplicationGLView mGlView;//自定义OpenGL -> GLSurfaceView
    private VideoPlaybackRenderer mRenderer;//自定义渲染器
    private boolean mReturningFromFullScreen = false;//是否全屏播放
    private int mSeekPosition[] = null;//播放进度
    private String mMovieName[] = null;//电影名
    private boolean mWasPlaying[] = null;//是不是正在播放
    boolean mIsInitialized = false;//是否初始化
    private Vector<Texture> mTextures;//用来渲染
    private boolean mPlayFullscreenVideo = false;
    // Helpers to detect events such as double tapping:
    private GestureDetector mGestureDetector = null;
    private GestureDetector.SimpleOnGestureListener mSimpleListener = null;

    //================================================end===========================================

    //==========================================自定义==============================================
    private List<VideoPlayerHelper> videoPlayerHelperList;
    //==========================================自定义==============================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vuforiaAppSession = new SampleApplicationSession(this);
        //回调 SampleApplicationControl 方法
        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        loadTextures();
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");


        //===========================================start==========================================
        videoPlayerHelperList = new ArrayList<>();

        // TODO: 2018/7/25
        //初始化自定义 VideoPlayHelper
        for (int i = 0; i < NUM_TARGETS; i++) {
            VideoPlayerHelper videoPlayerHelper = new VideoPlayerHelper();
            videoPlayerHelper.init();
            videoPlayerHelper.setActivity(this);
            videoPlayerHelperList.add(videoPlayerHelper);
        }

        mSeekPosition = new int[NUM_TARGETS];//播放进度
        mWasPlaying = new boolean[NUM_TARGETS];//是否正在播放

        mSimpleListener = new GestureDetector.SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(getApplicationContext(),
                mSimpleListener);

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            public boolean onDoubleTap(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            public boolean onDoubleTapEvent(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            // Handle the single tap
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final Handler autofocusHandler = new Handler();
                // Do not react if the StartupScreen is being displayed
                for (int i = 0; i < NUM_TARGETS; i++) {
                    // Verify that the tap happened inside the target
                    if (mRenderer != null && mRenderer.isTapOnScreenInsideTarget(i, e.getX(),
                            e.getY())) {
                        // Check if it is playable on texture
                        if (videoPlayerHelperList.get(i).isPlayableOnTexture()) {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.PAUSED)
                                    || (videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.READY)
                                    || (videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.STOPPED)
                                    || (videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END)) {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END))
                                    mSeekPosition[i] = 0;

                                videoPlayerHelperList.get(i).play(mPlayFullscreenVideo,
                                        mSeekPosition[i]);
                                mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                            } else if (videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING) {
                                // If it is playing then we pause it
                                videoPlayerHelperList.get(i).pause();
                            }
                        } else if (videoPlayerHelperList.get(i).isPlayableFullscreen()) {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            videoPlayerHelperList.get(i).play(true,
                                    VideoPlayerHelper.CURRENT_POSITION);
                        }

                        // Even though multiple videos can be loaded only one
                        // can be playing at any point in time. This break
                        // prevents that, say, overlapping videos trigger
                        // simultaneously playback.
                        break;
                    } else {
                        boolean result = CameraDevice.getInstance().setFocusMode(
                                CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
                        if (!result)
                            Log.e("SingleTapConfirmed", "Unable to trigger focus");

                        // Generates a Handler to trigger continuous auto-focus
                        // after 1 second
                        autofocusHandler.postDelayed(new Runnable() {
                            public void run() {
                                final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
                                        CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                                if (!autofocusResult)
                                    Log.e("SingleTapConfirmed", "Unable to re-enable continuous auto-focus");
                            }
                        }, 1000L);
                    }
                }

                return true;
            }
        });
        mMovieName = new String[NUM_TARGETS];
        mMovieName[0] = "http://mvvideo2.meitudata.com/56ec98e483bfd9733.mp4?k=c2f8ca937248adea502df7ae7e2be2b2&t=5b5bd9d6";
        mMovieName[1] = "http://mvvideo2.meitudata.com/56ec98e483bfd9733.mp4?k=c2f8ca937248adea502df7ae7e2be2b2&t=5b5bd9d6";
        //==========================================================================================
    }

    private void loadTextures() {
        mTextures = new Vector<>();
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_1.png", getAssets()));//识别成功，渲染图片
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_2.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/play.png", getAssets()));//播放按钮图片
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/busy.png", getAssets()));//加载中按钮图片
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/error.png", getAssets()));//错误按钮图片
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        //加入生命周期管理
        vuforiaAppSession.onResume();


        //===========================================start==========================================
        // 加载所有电影
        if (mRenderer != null) {
            for (int i = 0; i < NUM_TARGETS; i++) {
                if (!mReturningFromFullScreen) {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i],
                            false);
                } else {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i],
                            mWasPlaying[i]);
                }
            }
        }

        mReturningFromFullScreen = false;
        //============================================end===========================================
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        vuforiaAppSession.onConfigurationChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //加入生命周期管理
//        vuforiaAppSession.onPause();

        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        //===========================================start==========================================
        //存储电影的播放状态并将其卸载
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (videoPlayerHelperList.get(i).isPlayableOnTexture()) {
                mSeekPosition[i] = videoPlayerHelperList.get(i).getCurrentPosition();
                mWasPlaying[i] = videoPlayerHelperList.get(i).getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING ? true : false;
            }

            // 释放播放助手资源
            if (videoPlayerHelperList.get(i) != null)
                videoPlayerHelperList.get(i).unload();
        }

        mReturningFromFullScreen = false;

        try {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }
        //============================================end===========================================
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        deinitCloudReco();

//        try {
//            //加入生命周期管理
//            vuforiaAppSession.stopAR();
//        } catch (SampleApplicationException e) {
//            Log.e(TAG, e.getString());
//        }
//
//        System.gc();
        //==========================================start===========================================
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is destroyed we need to release all resources:
            if (videoPlayerHelperList.get(i) != null) {
                videoPlayerHelperList.get(i).deinit();
            }
            videoPlayerHelperList.set(i, null);
        }

        try {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
        //============================================end===========================================
    }


    private void deinitCloudReco() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.e(TAG, "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
            return;
        }

        TargetFinder finder = objectTracker.getTargetFinder();
        finder.deinit();
    }

    private void initApplicationAR() {
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

//        mRenderer = new CloudRecoVideoPlayRenderer(vuforiaAppSession, this);
        mRenderer = new VideoPlaybackRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
//        mGlView.setRenderer(mRenderer);

        //=========================================start============================================
        //渲染器具有OpenGL上下文，因此加载到纹理
        //必须在表面创建时发生。 这意味着我们
        //无法从此线程（GUI）加载电影，但我们必须
        //告诉GL线程在创建表面后加载它。
//        for (int i = 0; i < NUM_TARGETS; i++) {
//            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
//            mRenderer.requestLoad(i, mMovieName[i], 0, false);
//        }
        mRenderer.setVideoPlayerHelper(videoPlayerHelperList);

        mGlView.setRenderer(mRenderer);

        for (int i = 0; i < NUM_TARGETS; i++) {
            float[] temp = {0f, 0f, 0f};
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }
        //==========================================end=============================================
    }

    @Override
    public boolean doLoadTrackersData() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

        TargetFinder targetFinder = objectTracker.getTargetFinder();

        if (targetFinder.startInit(kAccessKey, kSecretKey)) {
            targetFinder.waitUntilInitFinished();
        }

        int resultCode = targetFinder.getInitState();
        if (resultCode != TargetFinder.INIT_SUCCESS) {
            Log.e(TAG, "Failed to initialize target finder.");
            return false;
        }

        //===========================================start==========================================
        //创建数据集
//        dataSet = objectTracker.createDataSet();
//        if (dataSet == null) {
//            Log.d(TAG, "doLoadTrackersData: 创建新的跟踪数据失败");
//        }
//
//        //加载数据集
//        if (!dataSet.load("StonesAndChips.xml", STORAGE_TYPE.STORAGE_APPRESOURCE)) {
//            Log.d(TAG, "doLoadTrackersData:加载数据集失败");
//            return false;
//        }
//
//        //启用数据集
//        if (!objectTracker.activateDataSet(dataSet)) {
//            Log.d(TAG, "doLoadTrackersData: 启用数据集失败");
//            return false;
//        }
        //============================================end===========================================

        return true;
    }


    @Override
    public boolean doUnloadTrackersData() {
        return true;
    }

    @Override
    public void onVuforiaResumed() {
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    @Override
    public void onInitARDone(SampleApplicationException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            addContentView(mGlView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);

        } else {
            Log.e(TAG, exception.getString());
        }
    }


    @Override
    public void onVuforiaStarted() {
        mRenderer.updateRenderingPrimitives();

        // Set camera focus mode
        if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)) {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }
    }

    @Override
    public void onVuforiaUpdate(State state) {
        TrackerManager trackerManager = TrackerManager.getInstance();

        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

        TargetFinder finder = objectTracker.getTargetFinder();

        int statusCode = finder.updateSearchResults(TargetFinder.FILTER_NONE);
        Log.d(TAG, "statusCode=" + statusCode);
        if (statusCode < 0) {

        } else if (statusCode == TargetFinder.UPDATE_RESULTS_AVAILABLE) {
            if (finder.getResultCount() > 0) {
                TargetSearchResult result = finder.getResult(0);
                if (result.getTrackingRating() > 0) {
                    finder.enableTracking(result);//
                }
            }
        }
    }


    @Override
    public boolean doInitTrackers() {
        boolean result = true;

        int provider = Vuforia.getActiveFusionProvider();

        if ((provider & ~FUSION_PROVIDER_TYPE.FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS) != 0) {
            if (Vuforia.setAllowedFusionProviders(FUSION_PROVIDER_TYPE.FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS) == FUSION_PROVIDER_TYPE.FUSION_PROVIDER_INVALID_OPERATION) {
                Log.e(TAG, "Failed to select the recommended fusion provider mode (FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS).");
                return false;
            }
        }

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;

        tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Log.e(TAG, "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            Log.i(TAG, "Tracker successfully initialized");
        }
        return result;
    }

    @Override
    public boolean doStartTrackers() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

        boolean result = objectTracker.start();

        TargetFinder targetFinder = objectTracker.getTargetFinder();
        targetFinder.startRecognition();
        mFinderStarted = true;


        //============================================start=========================================
        Tracker tracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (tracker != null) {
            tracker.start();
            //设置同时识别图片的最大数量
            Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 4);
        } else {
            result = false;
        }
        //==============================================end=========================================
        return result;
    }

    @Override
    public boolean doStopTrackers() {
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null) {
            objectTracker.stop();

            TargetFinder targetFinder = objectTracker.getTargetFinder();
            targetFinder.stop();
            mFinderStarted = false;

            targetFinder.clearTrackables();
        } else {
            result = false;
        }
        return result;
    }

    @Override
    public boolean doDeinitTrackers() {
        TrackerManager tManager = TrackerManager.getInstance();

        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());

        return result;
    }

    public void startFinderIfStopped() {
        if (!mFinderStarted) {
            mFinderStarted = true;

            TrackerManager trackerManager = TrackerManager.getInstance();
            ObjectTracker objectTracker = (ObjectTracker) trackerManager
                    .getTracker(ObjectTracker.getClassType());

            TargetFinder targetFinder = objectTracker.getTargetFinder();

            targetFinder.clearTrackables();
            targetFinder.startRecognition();
        }
    }


    private void pauseAll(int except) {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // We can make one exception to the pause all calls:
            if (i != except) {
                // Check if the video is playable on texture
                if (videoPlayerHelperList.get(i).isPlayableOnTexture()) {
                    // If it is playing then we pause it
                    videoPlayerHelperList.get(i).pause();
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }
}
