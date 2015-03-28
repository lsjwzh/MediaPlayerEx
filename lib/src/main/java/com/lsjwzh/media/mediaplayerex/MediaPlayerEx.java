package com.lsjwzh.media.mediaplayerex;

import android.content.Context;
import android.support.annotation.IntDef;
import android.view.SurfaceHolder;


import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * interface for MediaPlayerCompat
 * Created by panwenye on 14-8-19.
 */
public abstract class MediaPlayerEx {
    public static final int CACHE_MODE_NONE = 0;
    public static final int CACHE_MODE_LOCAL = 1;
    public static final int CACHE_MODE_PROXY = 1;
    public static final float DEFAULT_PREPARE_BUFFER_RATE = 0.1f;
    public static final int DEFAULT_MIN_PREPARE_BUFFER_SIZE = 50 * 1024;


    @IntDef({CACHE_MODE_NONE, CACHE_MODE_LOCAL,CACHE_MODE_PROXY})
    public @interface CacheMode{

    }
    public class UnknownMediaPlayerException extends Exception {
        public int what;
        public int extra;

    }

    public interface EventListener {
        void onPrepared();

        void onStart();

        void onPlayComplete();

        /**
         * call after seek action complete
         *
         * @param positionAfterSeek
         */
        void onSeekComplete(long positionAfterSeek);

        void onPause();

        void onStop();

        void onReset();

        void onRelease();

        /**
         * notify position change while playback
         *
         * @param position
         * @param duration
         */
        void onPositionUpdate(long position, long duration);

        /**
         * trigger when volume changed
         *
         * @param newV1
         * @param newV2
         */
        void onVolumeChanged(float newV1, float newV2);

        /**
         * notify buffering progress
         *
         * @param loadedPercentage
         */
        void onBuffering(int loadedPercentage);

        void onError(Exception e);

        void onVideoSizeChanged(int width, int height);
    }


    final LinkedList<EventListener> mListeners = new LinkedList<EventListener>();
    @CacheMode int mCacheMode = 0;
    String mCacheDir;


    /**
     * buffer rate,if the value calc by bufferrate is smaller than mMinBufferBlockSize,use mMinBufferBlockSize
     */
    float mPrepareBufferRate = DEFAULT_PREPARE_BUFFER_RATE;

    /**
     * min buffer size
     */
    long mMinBufferBlockSize = DEFAULT_MIN_PREPARE_BUFFER_SIZE;

    /**
     * @param context only needed in ExoPlayer,SysMediaPlayer will ignore this arg
     * @param path    data source uri
     */
    public abstract void setDataSource(Context context, String path);

    /**
     * prepare the media.
     */
    public abstract void prepare();

    public abstract void prepareAsync();

    public abstract void start();

    public abstract void seekTo(long position);

    //    public abstract void seekTo(long position,Runnable seekCompleteCallback);
    public abstract void pause();

    public abstract void stop();

    public abstract void reset();

    public abstract void release();

    public abstract long getCurrentPosition();

    public abstract long getDuration();

    public abstract boolean isPlaying();

    public abstract boolean isPrepared();

    public abstract boolean isReleased();

    public abstract void setDisplay(SurfaceHolder holder);

    public abstract void setPlaybackSpeed(float speed);

    public abstract void setVolume(float v1, float v2);

    public abstract void setAudioStreamType(int streamMusic);

    public void addListener(EventListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void setCacheMode(@CacheMode int pCacheMode){
        mCacheMode = pCacheMode;
    }

    public @CacheMode int getCacheMode(){
        return mCacheMode;
    }

    public String getCacheDir(){
        return mCacheDir;
    }

    public void setCacheDir(String pCacheDir){
        mCacheDir = pCacheDir;
    }

    public float getPrepareBufferRate() {
        return mPrepareBufferRate;
    }

    public void setPrepareBufferRate(float pPrepareBufferRate) {
        mPrepareBufferRate = pPrepareBufferRate;
    }


    public long getMinBufferBlockSize() {
        return mMinBufferBlockSize;
    }

    public void setMinBufferBlockSize(long pMinBufferBlockSize) {
        mMinBufferBlockSize = pMinBufferBlockSize;
    }


    public void removeListener(EventListener listener) {
        mListeners.remove(listener);
    }

    public List<EventListener> getListeners() {
        EventListener[] eventListeners = new EventListener[mListeners.size()];
        return Arrays.asList(mListeners.toArray(eventListeners));
    }
}
