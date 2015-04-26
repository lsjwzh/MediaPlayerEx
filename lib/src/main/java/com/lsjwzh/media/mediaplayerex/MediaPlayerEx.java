package com.lsjwzh.media.mediaplayerex;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.view.Surface;
import android.view.SurfaceHolder;

/**
 * interface for MediaPlayerCompat
 * Created by panwenye on 14-8-19.
 */
public abstract class MediaPlayerEx {
    public static final int DEFAULT_MIN_PREPARE_BUFFER_SIZE = 100 * 1024;// 100k
    final Hashtable<Class<? extends IEventListener>, LinkedList<IEventListener>> mListenersMap = new Hashtable<Class<? extends IEventListener>, LinkedList<IEventListener>>();
    // @CacheMode
    // int mCacheMode = 0;
    String mCacheDir = Environment.getExternalStorageDirectory() + "/mpex";
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

    public String getCacheDir() {
        return mCacheDir;
    }

    public void setCacheDir(String pCacheDir) {
        mCacheDir = pCacheDir;
    }

    public long getMinBufferBlockSize() {
        return mMinBufferBlockSize;
    }

    public void setMinBufferBlockSize(long pMinBufferBlockSize) {
        mMinBufferBlockSize = pMinBufferBlockSize;
    }

    public synchronized <T extends IEventListener> void registerListener(@NonNull Class<T> listenerClass,
                                                                         @NonNull T listener) {
        Class clazzAsKey = findDirectSubClassOfIEventListener(listenerClass);
        // if not any listeners,create a listener list
        if (!mListenersMap.containsKey(clazzAsKey)) {
            LinkedList<IEventListener> listeners = new LinkedList<IEventListener>();
            listeners.add(listener);
            mListenersMap.put(clazzAsKey, listeners);
        } else {
            LinkedList<IEventListener> listeners = mListenersMap.get(clazzAsKey);
            listeners.add(listener);
        }
    }

    public synchronized <T extends IEventListener> void registerListener(@NonNull T listener) {
        List<Class> eventClasses = findEventInterfaces(listener.getClass());
        for (Class c : eventClasses) {
            registerListener(c, listener);
        }
    }

    public synchronized void unregisterListener(@NonNull IEventListener listener) {
        Class clazzAsKey = findDirectSubClassOfIEventListener(listener.getClass());
        if (mListenersMap.containsKey(clazzAsKey)) {
            LinkedList<IEventListener> listeners = mListenersMap.get(clazzAsKey);
            listeners.remove(listener);
        }
    }

    public synchronized void clearListeners() {
        mListenersMap.clear();
    }

    public synchronized
    @NonNull
    <T extends IEventListener> List<IEventListener> getListeners(@NonNull Class<T> pTClass) {
        Class clazzAsKey = findDirectSubClassOfIEventListener(pTClass);
        if (!mListenersMap.containsKey(clazzAsKey)) {
            LinkedList<IEventListener> listeners = new LinkedList<IEventListener>();
            mListenersMap.put(clazzAsKey, listeners);
        }
        return mListenersMap.get(clazzAsKey);
    }

    /**
     * ensure the result is direct sub class of IEventListener
     *
     * @param pListenerClass
     * @return
     */
    protected Class findDirectSubClassOfIEventListener(Class pListenerClass) {
        if (pListenerClass.isInterface() && pListenerClass.getInterfaces().length > 0) {
            for (Class c : pListenerClass.getInterfaces()) {
                if (c == IEventListener.class) {
                    return pListenerClass;
                }
            }
        }
        throw new IllegalAccessError("can not find direct sub class of IEventListener ");
    }

    protected List<Class> findEventInterfaces(Class pListenerClass) {
        List<Class> classList = new ArrayList<Class>();
        for (Class clazz : pListenerClass.getInterfaces()) {
            if (IEventListener.class.isAssignableFrom(clazz)) {
                classList.add(clazz);
            }
        }
        return classList;
    }

    public interface IEventListener {

    }

    public interface OnPreparedListener extends IEventListener {
        public void onPrepared();
    }

    public interface OnStartListener extends IEventListener {
        public void onStart();
    }

    public interface OnPlayCompleteListener extends IEventListener {
        public void onPlayComplete(MediaPlayerEx mp);
    }

    public interface OnSeekCompleteListener extends IEventListener {
        public void onSeekComplete(long positionAfterSeek);
    }

    public interface OnPauseListener extends IEventListener {
        public void onPause();
    }

    public interface OnStopListener extends IEventListener {
        public void onStop();
    }

    public interface OnResetListener extends IEventListener {
        public void onReset();
    }

    public interface OnReleaseListener extends IEventListener {
        public void onRelease();
    }

    public interface OnPositionUpdateListener extends IEventListener {
        public void onPositionUpdate(long position, long duration);
    }

    public interface OnVolumeChangedListener extends IEventListener {
        public void onVolumeChanged(float newV1, float newV2);

    }

    public interface OnBufferingListener extends IEventListener {
        public void onBuffering(int loadedPercentage);

    }

    public interface OnErrorListener extends IEventListener {
        public void onError(Throwable e);

    }

    public interface OnVideoSizeChangedListener extends IEventListener {
        public void onVideoSizeChanged(int width, int height);

    }

    public class UnknownMediaPlayerException extends Exception {
        public int what;
        public int extra;

    }
}
