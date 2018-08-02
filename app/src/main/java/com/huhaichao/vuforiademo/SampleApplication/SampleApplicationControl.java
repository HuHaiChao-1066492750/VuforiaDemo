/*===============================================================================
Copyright (c) 2016-2017 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.huhaichao.vuforiademo.SampleApplication;

import com.vuforia.State;


//  Interface to be implemented by the activity which uses SampleApplicationSession
public interface SampleApplicationControl
{
    
    // To be called to initialize the trackers:被调用来初始化跟踪器
    boolean doInitTrackers();
    
    
    // To be called to load the trackers' data:被调用来加载跟踪器的数据
    boolean doLoadTrackersData();
    
    
    // To be called to start tracking with the initialized trackers and their:被调用初始化跟踪跟踪器和它们的跟踪
    // loaded data
    boolean doStartTrackers();
    
    
    // To be called to stop the trackers:被调用停止追踪者
    boolean doStopTrackers();
    
    
    // To be called to destroy the trackers' data：被调用销毁追踪器数据
    boolean doUnloadTrackersData();
    
    
    // To be called to deinitialize the trackers：被调用初始化追踪器
    boolean doDeinitTrackers();
    
    
    // This callback is called after the Vuforia initialization is complete,： Vuforia准备完成
    // the trackers are initialized, their data loaded and
    // tracking is ready to start
    void onInitARDone(SampleApplicationException e);
    
    
    // This callback is called every cycle 周期
    void onVuforiaUpdate(State state);


    // This callback is called on Vuforia resume
    void onVuforiaResumed();


    // This callback is called once Vuforia has been started
    void onVuforiaStarted();

}
