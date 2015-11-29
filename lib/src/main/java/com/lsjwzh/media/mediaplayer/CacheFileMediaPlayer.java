package com.lsjwzh.media.mediaplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.IOException;

/**
 * Created by panwenye on 14-8-20.
 */
public class CacheFileMediaPlayer extends MediaPlayer {
    private static final boolean DEBUG = false;
    private static final int NONE = 0;
    private static final int WAIT_FOR_PREPARE = 1;

    private StrongerMediaPlayer mMediaPlayer;
    private MediaMonitor mMediaMonitor;
    private MediaDownloader mMediaDownloader;
    private final MediaDownloaderFactory mMediaDownloaderFactory;

    @State
    private int mStateWithLocalCache;
    long latestProgressOnPrepare = 0;
    private boolean mHasPrepared;
    private boolean mHasReleased;
    private boolean mMPErrorHappened;
    private Context mContext;
    private SurfaceHolder mSurfaceHolder;
    private Surface mSurface;
    /**
     * seekTo will cause a error if mediaplayer have not started
     */
    private boolean mHasStarted;
    private long mSavedPosition;
    private boolean mLooping;


    public CacheFileMediaPlayer(MediaDownloaderFactory mediaDownloaderFactory) {
        mMediaDownloaderFactory = mediaDownloaderFactory;
    }

    @Override
    public void setDataSource(Context context, String uri) {
        mContext = context;
        if (isRemoteMedia(uri)) {
            if (mMediaDownloader != null) {
                mMediaDownloader.stop();
            }
            mMediaDownloader = mMediaDownloaderFactory.createMediaDownloader(uri);
            mMediaDownloader.registerListener(new MediaDownloader.OnDownloadListener() {
                @Override
                public void onProgress(long progress, long length) {
                    if (DEBUG) {
                        Log.e("mpex", "progress changed:" + progress);
                    }
                    long prepareBufferSize = latestProgressOnPrepare == 0
                            ? getPrepareBufferSize() : getMinBufferBlockSize();
                    // getPrepareBufferRate() * length);
                    if (progress - latestProgressOnPrepare >= prepareBufferSize
                            || (mMediaDownloader != null && progress == length)) {
                        tryPrepareMp();
                    }
                    // notify buffering event
                    if (progress - latestProgressOnPrepare < prepareBufferSize) {
                        for (EventListener listener : getListeners(OnBufferingListener.class)) {
                            ((OnBufferingListener) listener)
                                    .onBuffering((int) ((progress - latestProgressOnPrepare) * 1f / getMinBufferBlockSize()) * 100);
                        }
                    }
                }

                @Override
                public void onSuccess(File pFile) {
                    if (DEBUG) {
                        Log.e("mpex", latestProgressOnPrepare + "/" + pFile.length());
                    }
                    tryPrepareMp();
                    for (EventListener listener : getListeners(OnBufferingListener.class)) {
                        ((OnBufferingListener) listener).onBuffering(100);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    for (EventListener listener : getListeners(OnErrorListener.class)) {
                        ((OnErrorListener) listener).onError(t);
                    }
                }
            });
            mMediaDownloader.start();
        } else {
            initMediaPlayer();
            try {
                mMediaPlayer.setDataSource(uri);
            } catch (IOException e) {
                for (EventListener listener : getListeners(OnErrorListener.class)) {
                    ((OnErrorListener) listener).onError(e);
                }
            }
        }
    }

    private void tryPrepareMp() {
        // when the file has been downloaded,or reached the buffer limit,excute prepare or setDataSource
        // operation
        if (mStateWithLocalCache == WAIT_FOR_PREPARE) {
            if (DEBUG) {
                Log.e("mpex", "call setDataSource ");
            }
            try {
                boolean isPlayingBeforeReinit = mHasStarted;
                initMediaPlayer();
                mHasStarted = isPlayingBeforeReinit;
                mMediaPlayer.setDataSource(mMediaDownloader.getLocalFilePath());
            } catch (IOException e) {
                if (DEBUG) {
                    Log.e("mpex", " error on setDataSource:" + e.getMessage());
                }
                for (EventListener listener : getListeners(OnErrorListener.class)) {
                    ((OnErrorListener) listener).onError(e);
                }
                return;
            }
            if (DEBUG) {
                Log.e("mpex", "call prepareAsync ");
            }
            prepareAsync();
        }
    }

    /**
     * is uri a remote data source
     *
     * @param uri
     * @return
     */
    private boolean isRemoteMedia(String uri) {
        return uri.startsWith("http:") || uri.startsWith("https:");
    }

    private void waitForReinit() {
        if (DEBUG) {
            Log.e("mpex", "call waitreinit");
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.setOnBufferingUpdateListener(null);
            mMediaPlayer.setDisplay(null);
            mMediaPlayer.setSurface(null);
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mStateWithLocalCache = WAIT_FOR_PREPARE;
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            mHasReleased = false;
            mHasStarted = false;
            mHasPrepared = false;
            mMPErrorHappened = false;
            mMediaPlayer = new StrongerMediaPlayer(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    Log.e("mpex", "what:" + what + ",extra:" + extra + "isPrepared:" + isPrepared());
                    if (mMediaDownloader != null) {
                        if (mMediaDownloader.getDownloadedSize() > latestProgressOnPrepare
                                + getMinBufferBlockSize()) {
                            latestProgressOnPrepare = mMediaDownloader.getDownloadedSize();
                        }
                    }
                    if (isPrepared()) {
                        waitForReinit();
                        if (mMediaDownloader != null && mMediaDownloader.isFinished()) {
                            tryPrepareMp();
                        }
                        return true;
                    }
                    mMPErrorHappened = true;
                    UnknownMediaPlayerException unknownMediaPlayerException = new UnknownMediaPlayerException();
                    unknownMediaPlayerException.what = what;
                    unknownMediaPlayerException.extra = extra;
                    for (EventListener listener : getListeners(OnErrorListener.class)) {
                        ((OnErrorListener) listener).onError(unknownMediaPlayerException);
                    }
                    return true;
                }
            });
            // use MediaMonitor to update position change
            if (mMediaMonitor == null) {
                mMediaMonitor = new MediaMonitor();
                mMediaMonitor.task = new Runnable() {
                    @Override
                    public void run() {
                        if (mMediaPlayer != null && isPrepared() && !mMPErrorHappened) {
                            mSavedPosition = mMediaPlayer.getCurrentPosition();
                            int duration = mMediaPlayer.getDuration();
                            if (DEBUG) {
                                Log.e("mpex", "mSavedPosition:" + mSavedPosition);
                            }
                            for (EventListener listener : getListeners(OnPositionUpdateListener.class)) {
                                ((OnPositionUpdateListener) listener).onPositionUpdate(mSavedPosition, duration);
                            }
                        }
                    }
                };
            }

            mMediaPlayer.setOnSeekCompleteListener(new android.media.MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(android.media.MediaPlayer mp) {
                    for (EventListener listener : getListeners(OnSeekCompleteListener.class)) {
                        ((OnSeekCompleteListener) listener).onSeekComplete(getCurrentPosition());
                    }
                }
            });
            mMediaPlayer.setOnBufferingUpdateListener(new android.media.MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(android.media.MediaPlayer mp, int percent) {
                    // fix bug: BufferingUpdate still can been triggered when mediaplayer is playing,
                    if (isPlaying()) {
                        percent = 100;
                    }
                    for (EventListener listener : getListeners(OnBufferingListener.class)) {
                        ((OnBufferingListener) listener).onBuffering(percent);
                    }
                }
            });
            mMediaPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer mp) {
                    for (EventListener listener : getListeners(OnPlayCompleteListener.class)) {
                        ((OnPlayCompleteListener) listener).onPlayComplete(CacheFileMediaPlayer.this);
                    }
                }
            });
            if (mSurfaceHolder != null) {
                mMediaPlayer.setDisplay(mSurfaceHolder);
            } else if (mSurface != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    mMediaPlayer.setSurface(mSurface);
                }
            }
            mMediaPlayer.setLooping(mLooping);
        }
    }

    @Override
    public void prepare() {
        if (mMediaPlayer == null || !mMediaPlayer.hasSetDataSource()) {
            throw new IllegalStateException("must call setDatasurce firstly");
        }
        try {
            mMediaPlayer.prepare();
            mHasPrepared = true;
            latestProgressOnPrepare = mMediaDownloader.getDownloadedSize();
            for (EventListener listener : getListeners(OnPreparedListener.class)) {
                ((OnPreparedListener) listener).onPrepared();
            }
        } catch (IOException e) {
            for (EventListener listener : getListeners(OnErrorListener.class)) {
                ((OnErrorListener) listener).onError(e);
            }
        }
    }

    @Override
    public void prepareAsync() {
        if (mMediaPlayer == null || !mMediaPlayer.hasSetDataSource()) {
            mStateWithLocalCache = WAIT_FOR_PREPARE;
            return;
        }
        mStateWithLocalCache = NONE;
        mMediaPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(android.media.MediaPlayer mp) {
                if (DEBUG) {
                    Log.e("mpex", "prepareAsync success");
                }
                mHasPrepared = true;
                if (mHasStarted) {
                    if (DEBUG) {
                        Log.e("mpex", "seek to : " + mSavedPosition);
                    }
                    mMediaPlayer.setOnSeekCompleteListener(new android.media.MediaPlayer.OnSeekCompleteListener() {
                        @Override
                        public void onSeekComplete(android.media.MediaPlayer mp) {
                            mMediaPlayer.start();
                        }
                    });
                    mMediaPlayer.seekTo((int) mSavedPosition);
                    return;
                }
                latestProgressOnPrepare = mMediaDownloader.getDownloadedSize();
                for (EventListener listener : getListeners(OnPreparedListener.class)) {
                    if (DEBUG) {
                        Log.e("mpex", "call onPrepared");
                    }
                    ((OnPreparedListener) listener).onPrepared();
                }
            }
        });
        try {
            mMediaPlayer.prepareAsync();
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e("mpex", "error on prepareAsync ");
        }
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mHasStarted = true;
            if (mMediaMonitor != null) {
                mMediaMonitor.start();
            }
            for (EventListener listener : getListeners(OnStartListener.class)) {
                ((OnStartListener) listener).onStart();
            }
        }
    }

    @Override
    public void seekTo(final long position) {
        // seekTo will cause a error if mediaplayer have not been started
        if (!mHasStarted) {
            start();
            pause();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo((int) position);
        }
    }

    @Override
    public void pause() {
        if (!mHasStarted) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            if (mMediaMonitor != null) {
                mMediaMonitor.pause();
            }
            for (EventListener listener : getListeners(OnPauseListener.class)) {
                ((OnPauseListener) listener).onPause();
            }
        }
    }

    @Override
    public void stop() {
        if (!mHasStarted) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            if (mMediaMonitor != null) {
                mMediaMonitor.pause();
            }
            for (EventListener listener : getListeners(OnStopListener.class)) {
                ((OnStopListener) listener).onStop();
            }
        }
    }

    @Override
    public void reset() {
        if (!mHasStarted) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            if (mMediaMonitor != null) {
                mMediaMonitor.pause();
            }
            for (EventListener listener : getListeners(OnResetListener.class)) {
                ((OnResetListener) listener).onReset();
            }
        }
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            if (mMediaMonitor != null) {
                mMediaMonitor.quit();
            }
            mMediaPlayer.release();
            mMediaPlayer = null;
            mHasReleased = true;
            for (EventListener listener : getListeners(OnReleaseListener.class)) {
                ((OnReleaseListener) listener).onRelease();
            }
        }
    }

    @Override
    public long getCurrentPosition() {
        if (!mHasStarted) {
            return 0;
        }
        if (mMediaPlayer != null) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getDuration();
        }
        return 0;
    }

    @Override
    public boolean isPlaying() {
        if (!mHasStarted) {
            return false;
        }
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public boolean isPrepared() {
        return mHasPrepared;
    }

    @Override
    public boolean isReleased() {
        return mHasReleased;
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setDisplay(holder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setLooping(boolean looping) {
        mLooping = looping;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setLooping(looping);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(14)
    @Override
    public void setDisplay(Surface pSurface) {
        mSurface = pSurface;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setSurface(pSurface);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        throw new UnsupportedOperationException("the default mediaplayer of system is unable to change playback speed");
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(v1, v2);
        }
    }

    @Override
    public void setAudioStreamType(int streamMusic) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setAudioStreamType(streamMusic);
        }
    }

    @IntDef({NONE, WAIT_FOR_PREPARE})
    @interface State {

    }
}
