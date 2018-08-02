package com.huhaichao.vuforiademo.CloudRecoVideoPlay;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.huhaichao.vuforiademo.R;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationControl;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationException;
import com.huhaichao.vuforiademo.SampleApplication.SampleApplicationSession;
import com.huhaichao.vuforiademo.SampleApplication.utils.SampleApplicationGLView;
import com.huhaichao.vuforiademo.SampleApplication.utils.Texture;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlaybackRenderer;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ImageTarget;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.Vector;

/**
 * Vuforia三个主要概念设计：设备/相机、跟踪、渲染
 */
public class MainActivity extends Activity implements SampleApplicationControl {
    private static final String TAG = "hhc";
    private SampleApplicationSession vuforiaAppSession;
    private VideoPlayerHelper mVideoPlayerHelper[] = null;
    private static final int NUM_TARGETS = 1;
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
    private String uri = "http://mvvideo1.meitudata.com/56e9cef1ced688983.mp4?k=1d99ecf88ebe91d3442be4c4e4cf8c01&t=5b5a836a";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vuforiaAppSession = new SampleApplicationSession(this);//大部分操作逻辑，通过接口回调返回到当前activity
        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//初始化AR以及屏幕方向
        mVideoPlayerHelper = new VideoPlayerHelper[NUM_TARGETS];//自定义视频播放器助手，处理目标的电影回放

        //初始化自定义 VideoPlayHelper
        for (int i = 0; i < NUM_TARGETS; i++) {
            mVideoPlayerHelper[i] = new VideoPlayerHelper();
            mVideoPlayerHelper[i].init();
            mVideoPlayerHelper[i].setActivity(this);
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
                        if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.PAUSED)
                                    || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.READY)
                                    || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.STOPPED)
                                    || (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END)) {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.REACHED_END))
                                    mSeekPosition[i] = 0;

                                mVideoPlayerHelper[i].play(mPlayFullscreenVideo,
                                        mSeekPosition[i]);
                                mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                            } else if (mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING) {
                                // If it is playing then we pause it
                                mVideoPlayerHelper[i].pause();
                            }
                        } else if (mVideoPlayerHelper[i].isPlayableFullscreen()) {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            mVideoPlayerHelper[i].play(true,
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

        mTextures = new Vector<Texture>();
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_1.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_2.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/play.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/busy.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/error.png", getAssets()));

        mMovieName = new String[NUM_TARGETS];
//        mMovieName[0] = "VideoPlayback/VuforiaSizzleReel_1.mp4";
//        mMovieName[1] = "VideoPlayback/VuforiaSizzleReel_2.mp4";
        mMovieName[0] = uri;

        mUILayout = (RelativeLayout) View.inflate(this, R.layout.activity_main, null);
        //在AR初始化之前，设置一个背景
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);
    }

    @Override
    protected void onResume() {
        super.onResume();
        vuforiaAppSession.onResume();

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
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        vuforiaAppSession.onConfigurationChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        //存储电影的播放状态并将其卸载
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                mSeekPosition[i] = mVideoPlayerHelper[i].getCurrentPosition();
                mWasPlaying[i] = mVideoPlayerHelper[i].getStatus() == VideoPlayerHelper.MEDIA_STATE.PLAYING ? true : false;
            }

            // 释放播放助手资源
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unload();
        }

        mReturningFromFullScreen = false;

        try {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is destroyed we need to release all resources:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].deinit();
            mVideoPlayerHelper[i] = null;
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
    }

    @Override
    public void onBackPressed() {
        pauseAll(-1);
        super.onBackPressed();
    }

    private void pauseAll(int except) {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // We can make one exception to the pause all calls:
            if (i != except) {
                // Check if the video is playable on texture
                if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    //①初始化跟踪器
    @Override
    public boolean doInitTrackers() {
        Log.d(TAG, "doInitTrackers: ");
        boolean result = true;
        //初始化 image tracker
        TrackerManager trackerManager = TrackerManager.getInstance();

        //初始化类型
        //1.ObjectTracker:一种追踪现实世界中物体的追踪器。trackable实例的数据存储在DataSet实例中
        //2.SmartTerrain:用于在运行时收集有关用户环境的信息
        //3.DeviceTracker:
        //  ①RotationalDeviceTracker
        //  ②PositionalDeviceTracker
        Tracker tracker = trackerManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Log.d(TAG, "doInitTrackers: ObjectTracker初始化失败");
            result = false;
        }
        return result;
    }

    //②加载跟踪器数据集
    @Override
    public boolean doLoadTrackersData() {
        Log.d(TAG, "doLoadTrackersData: ");
        //获取 image tracker
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.d(TAG, "doLoadTrackersData: ObjectTracker没有初始化");
            return false;
        }

        //创建数据集
        dataSet = objectTracker.createDataSet();
        if (dataSet == null) {
            Log.d(TAG, "doLoadTrackersData: 创建新的跟踪数据失败");
        }

        //加载数据集
        if (!dataSet.load("StonesAndChips.xml", STORAGE_TYPE.STORAGE_APPRESOURCE)) {
            Log.d(TAG, "doLoadTrackersData:加载数据集失败");
            return false;
        }

        //启用数据集
        if (!objectTracker.activateDataSet(dataSet)) {
            Log.d(TAG, "doLoadTrackersData: 启用数据集失败");
            return false;
        }
        return true;
    }

    //③Vuforia SDK初始化完成(扫描结果回调的注册)
    //初始化发生异常都会回调到这里
    @Override
    public void onInitARDone(SampleApplicationException e) {
        Log.d(TAG, "onInitARDone: ");
        if (e == null) {
            initApplicationAR();
            mRenderer.setActive(true);

            addContentView(mGlView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 先绘制
            mUILayout.bringToFront();

            mUILayout.setVisibility(View.GONE);

            // 设置背景色
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);

            mIsInitialized = true;
        } else {
            Toast.makeText(this, e.getString(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    //④初始化AR应用程序组件
    public void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        //初始化GLView
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        //初始化渲染器
        mRenderer = new VideoPlaybackRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);

        //渲染器具有OpenGL上下文，因此加载到纹理
        //必须在表面创建时发生。 这意味着我们
        //无法从此线程（GUI）加载电影，但我们必须
        //告诉GL线程在创建表面后加载它。
        for (int i = 0; i < NUM_TARGETS; i++) {
            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
            mRenderer.requestLoad(i, mMovieName[i], 0, false);
        }

        mGlView.setRenderer(mRenderer);

        for (int i = 0; i < NUM_TARGETS; i++) {
            float[] temp = {0f, 0f, 0f};
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }
    }

    //⑤启动跟踪器
    @Override
    public boolean doStartTrackers() {
        Log.d(TAG, "doStartTrackers: ");
        boolean result = true;
        Tracker tracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (tracker != null) {
            tracker.start();
            //设置同时识别图片的最大数量
            Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 4);
        } else {
            result = false;
        }
        return result;
    }

    //⑥更新要渲染的基元(要渲染视图的时候用到)
    @Override
    public void onVuforiaStarted() {
        Log.d(TAG, "onVuforiaStarted: ");
        mRenderer.updateRenderingPrimitives();
        // 设置相机对焦模式
        if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)) {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }
    }

    //⑦在周期内一直回调(识别接口回调的结果)
    @Override
    public void onVuforiaUpdate(State state) {
        Log.d(TAG, "onVuforiaUpdate: ");

        //在自定义的Renderer的renderFrame方法渲染出来
    }

    //⑧停止跟踪器
    @Override
    public boolean doStopTrackers() {
        Log.d(TAG, "doStopTrackers: ");
        boolean result = true;
        Tracker tracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (tracker != null) {
            tracker.stop();
        } else {
            result = false;
        }
        return result;
    }

    //⑨销毁跟踪器数据集
    @Override
    public boolean doUnloadTrackersData() {
        Log.d(TAG, "doUnloadTrackersData: ");
        boolean result = true;
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker == null) {
            Log.d(TAG, "doUnloadTrackersData: 销毁跟踪器数据集失败");
            return false;
        }

        if (dataSet != null) {
            //数据集关闭失败
            if (objectTracker.getActiveDataSet(0) == dataSet && !objectTracker.deactivateDataSet(dataSet)) {
                Log.d(TAG, "doUnloadTrackersData: 销毁跟踪器数据集失败，跟踪器数据集不能被关闭");
                result = false;
            } else if (!objectTracker.destroyDataSet(dataSet)) {
                Log.d(TAG, "doUnloadTrackersData: 销毁跟踪器数据集失败");
                result = false;
            }
            dataSet = null;
        }
        return result;
    }

    //⑩取消初始化跟踪器
    @Override
    public boolean doDeinitTrackers() {
        Log.d(TAG, "doDeinitTrackers: ");
        TrackerManager trackerManager = TrackerManager.getInstance();
        return trackerManager.deinitTracker(ObjectTracker.getClassType());
    }

    @Override
    public void onVuforiaResumed() {
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    public static void main(String[] args) {
        String s1 = "123456";
        String[] s_1 = s1.split(",");
        for (int i = 0; i < s_1.length; i++) {
            System.out.println("s1=");
        }

        String s2 = "123,456,789";
        String[] s_2 = s2.split(",");
        for (int i = 0; i < s_2.length; i++) {
            System.out.println("s2=");
        }
    }
}
