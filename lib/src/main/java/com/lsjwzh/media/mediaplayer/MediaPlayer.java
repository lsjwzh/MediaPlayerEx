package com.lsjwzh.media.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.EventListener;
import java.util.List;

/**
 * abstract MediaPlayer
 */
public abstract class MediaPlayer {
    public static final int DEFAULT_MIN_PREPARE_BUFFER_SIZE = 100 * 1024;// 100k

    private EventListenerManager mEventListenerManager = new EventListenerManager();

    /**
     * min buffer size
     */
    private long mMinBufferBlockSize = DEFAULT_MIN_PREPARE_BUFFER_SIZE;
    private long mPrepareBufferSize = DEFAULT_MIN_PREPARE_BUFFER_SIZE;

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

    // public abstract void seekTo(long position,Runnable seekCompleteCallback);
    public abstract void pause();

    public abstract void stop();

    public abstract void reset();

    public abstract void release();

    public abstract long getCurrentPosition();

    public abstract long getDuration();

    public abstract boolean isPlaying();

    public abstract boolean isPrepared();

    public abstract boolean isReleased();

    /**
     * buffer rate,if the value calc by bufferrate is smaller than mMinBufferBlockSize,use mMinBufferBlockSize
     */
    // float mPrepareBufferRate = DEFAULT_PREPARE_BUFFER_RATE;
    public abstract void setDisplay(SurfaceHolder holder);

    @TargetApi(14)
    public abstract void setDisplay(Surface pSurface);

    public abstract void setPlaybackSpeed(float speed);

    public abstract void setVolume(float v1, float v2);

    public abstract void setAudioStreamType(int streamMusic);


    public abstract void setLooping(boolean looping);

    public long getPrepareBufferSize() {
        return mPrepareBufferSize;
    }

    public void setPrepareBufferSize(long prepareBufferSize) {
        mPrepareBufferSize = prepareBufferSize;
    }

    public long getMinBufferBlockSize() {
        return mMinBufferBlockSize;
    }

    public void setMinBufferBlockSize(long pMinBufferBlockSize) {
        mMinBufferBlockSize = pMinBufferBlockSize;
    }

    public synchronized <T extends EventListener> void registerListener(@NonNull Class<T> listenerClass,
                                                                        @NonNull T listener) {
        mEventListenerManager.registerListener(listenerClass, listener);
    }

    public synchronized <T extends EventListener> void registerListener(@NonNull T listener) {
        mEventListenerManager.registerListener(listener);
    }

    public synchronized void unregisterListener(@NonNull EventListener listener) {
        mEventListenerManager.unregisterListener(listener);
    }

    public synchronized void clearListeners() {
        mEventListenerManager.clearListeners();
    }

    @NonNull
    public synchronized <T extends EventListener> List<T> getListeners(
            @NonNull Class<T> pTClass) {
        return mEventListenerManager.getListeners(pTClass);
    }

    public interface OnPreparedListener extends EventListener {
        void onPrepared(MediaPlayer mp);
    }

    public interface OnStartListener extends EventListener {
        void onStart();
    }

    public interface OnPlayCompleteListener extends EventListener {
        void onPlayComplete(MediaPlayer mp);
    }

    public interface OnSeekCompleteListener extends EventListener {
        void onSeekComplete(long positionAfterSeek);
    }

    public interface OnPauseListener extends EventListener {
        void onPause();
    }

    public interface OnStopListener extends EventListener {
        void onStop();
    }

    public interface OnResetListener extends EventListener {
        void onReset();
    }

    public interface OnReleaseListener extends EventListener {
        void onRelease();
    }

    public interface OnPositionUpdateListener extends EventListener {
        void onPositionUpdate(long position, long duration);
    }

    public interface OnVolumeChangedListener extends EventListener {
        void onVolumeChanged(float newV1, float newV2);
    }

    public interface OnBufferingListener extends EventListener {
        void onBuffering(int loadedPercentage);
    }

    public interface OnErrorListener extends EventListener {
        void onError(Throwable e);
    }

    public interface OnInfoListener extends EventListener {
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    public interface OnVideoSizeChangedListener extends EventListener {
        void onVideoSizeChanged(int width, int height);
    }

    public class UnknownMediaPlayerException extends Exception {
        public int what;
        public int extra;

    }
}
