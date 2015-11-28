package com.lsjwzh.media.mediaplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Created by panwenye on 14-8-20.
 */
public class MediaMonitor implements Runnable {
    private final Object mLock = new Object();
    public Runnable task;
    volatile boolean isRunning = false;
    Thread mThread;
    Handler initHandler = new Handler(Looper.getMainLooper());

    /**
     * Creates a worker thread with the given name. The thread
     * then runs a {@link Looper}.
     */
    public MediaMonitor() {
        mThread = new Thread(null, this, "monitor");
        mThread.start();
    }


    public void start() {
        synchronized (mLock) {
            if (!isRunning) {
                isRunning = true;
            }
            mLock.notifyAll();
        }
    }

    public void run() {
        while (!Thread.interrupted()) {
            synchronized (mLock) {
                if (!isRunning) {
                    try {
                        initHandler.removeCallbacksAndMessages(null);
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            SystemClock.sleep(100);
            //not execute task in Monitor Thread
            if (task != null&&isRunning) {
                try {
                    initHandler.removeCallbacksAndMessages(null);
                    initHandler.post(task);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else {
                initHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    public void pause() {
        synchronized (mLock) {
            isRunning = false;
        }
    }

    public void quit() {
        if(mThread!=null){
            task = null;
            synchronized (mLock) {
                isRunning = false;
                mLock.notifyAll();
            }
            mThread.interrupt();
        }
    }


}

