package com.huhaichao.vuforiademo.CloudRecoVideoPlay;

import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_TYPE;
import com.huhaichao.vuforiademo.VideoPlayback.app.VideoPlayback.VideoPlayerHelper.MEDIA_STATE;

/**
 * Created by Administrator on 2018/7/25.
 */

public class VideoPlaybackModel {
    private String uniqueTargetId;//识别目标唯一Id
    private String name;//识别目标名字
    private String metaData;//识别目标元数据包
    private int currentPosition = 0;//当前进度位置
    private MEDIA_STATE mediaState;//播放器状态
    private MEDIA_TYPE mediaType = MEDIA_TYPE.ON_TEXTURE_FULLSCREEN;//播放器类型
    private boolean isPlayImmediately = false;//是否立即播放
    private long lostTrackingSince = -1;//跟踪丢失的时间戳
    private boolean isLoadRequested = false;//数据是否已加载
    private float keyframeAspectRatio;//关键帧宽高比

    public String getUniqueTargetId() {
        return uniqueTargetId;
    }

    public void setUniqueTargetId(String uniqueTargetId) {
        this.uniqueTargetId = uniqueTargetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetaData() {
        return metaData;
    }

    public void setMetaData(String metaData) {
        this.metaData = metaData;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public MEDIA_STATE getMediaState() {
        return mediaState;
    }

    public void setMediaState(MEDIA_STATE mediaState) {
        this.mediaState = mediaState;
    }

    public MEDIA_TYPE getMediaType() {
        return mediaType;
    }

    public void setMediaType(MEDIA_TYPE mediaType) {
        this.mediaType = mediaType;
    }

    public boolean getPlayImmediately() {
        return isPlayImmediately;
    }

    public void setPlayImmediately(boolean playImmediately) {
        this.isPlayImmediately = playImmediately;
    }

    public long getLostTrackingSince() {
        return lostTrackingSince;
    }

    public void setLostTrackingSince(long lostTrackingSince) {
        this.lostTrackingSince = lostTrackingSince;
    }

    public boolean getLoadRequested(boolean loadRequested) {
        return isLoadRequested;
    }

    public void setLoadRequested(boolean loadRequested) {
        isLoadRequested = loadRequested;
    }

    public float getKeyframeAspectRatio() {
        return keyframeAspectRatio;
    }

    public void setKeyframeAspectRatio(float keyframeAspectRatio) {
        this.keyframeAspectRatio = keyframeAspectRatio;
    }


    @Override
    public boolean equals(Object obj) {
        VideoPlaybackModel videoPlaybackModel = new VideoPlaybackModel();
        if (!this.getUniqueTargetId().equals(videoPlaybackModel.getUniqueTargetId())) {
            return false;
        }
        return true;
    }
}
