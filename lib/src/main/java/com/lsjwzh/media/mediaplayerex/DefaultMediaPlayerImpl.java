package com.lsjwzh.media.mediaplayerex;

import java.io.File;
import java.io.IOException;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.lsjwzh.media.download.FileDownloader;
import com.lsjwzh.media.proxy.FileUtil;

/**
 * Created by panwenye on 14-8-20.
 */
public class DefaultMediaPlayerImpl extends MediaPlayerEx {
    public static final int NONE = 0;
    public static final int WAIT_FOR_PREPARE = 1;
    static final boolean DEBUG = true;
    StrongerMediaPlayer mMediaPlayer;
    MediaMonitor mMediaMonitor;
    /**
     * local url,only for LOCAL CACHE MODE
     */
    String mLocalUri;
    /**
     * only for LOCAL CACHE MODE
     */
    FileDownloader mFileDownloader;
    /**
     * only for LOCAL CACHE MODE
     */
    @State
    int mStateWithLocalCache;
    /**
     * only for LOCAL CACHE MODE
     */
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
    private boolean mErrorHappenedOnDownloading;
    private boolean mLooping;

    @Override
    public void setDataSource(Context context, String uri) {
        mContext = context;
        try {
            if (isRemoteMedia(uri)) {
                // if the cache mode is local,we must transfer remote uri to local uri
                mLocalUri = getCacheDir() + File.separator + FileUtil.extractFileNameFromURI(uri);
                // if cachemode not NONE, start buffer
                if (mFileDownloader != null) {
                    mFileDownloader.stop();
                }
                mFileDownloader = FileDownloader.get(uri, mLocalUri);
                mFileDownloader.setEventListener(new FileDownloader.EventListener() {
                    @Override
                    public void onProgress(long progress, long length) {
                        if (DEBUG) {
                            Log.e("mpex", "progress changed:" + progress);
                        }
                        long prepareBufferSize = getMinBufferBlockSize();// (long) Math.max(getMinBufferBlockSize(),
                        // getPrepareBufferRate() * length);
                        if (progress - latestProgressOnPrepare >= prepareBufferSize
                                || (mFileDownloader != null && progress == length)) {
                            tryPrepareMp();
                        }
                        // notify buffering event
                        if (progress - latestProgressOnPrepare < prepareBufferSize) {
                            for (IEventListener listener : getListeners(OnBufferingListener.class)) {
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
                        for (IEventListener listener : getListeners(OnBufferingListener.class)) {
                            ((OnBufferingListener) listener).onBuffering(100);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        mErrorHappenedOnDownloading = true;
                        for (IEventListener listener : getListeners(OnErrorListener.class)) {
                            ((OnErrorListener) listener).onError(t);
                        }
                    }
                });
                mFileDownloader.start();
            } else {
                initMediaPlayer();
                mLocalUri = uri;
                mMediaPlayer.setDataSource(uri);
            }
        } catch (IOException e) {
            for (IEventListener listener : getListeners(OnErrorListener.class)) {
                ((OnErrorListener) listener).onError(e);
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
                mMediaPlayer.setDataSource(mLocalUri);
            } catch (IOException e) {
                if (DEBUG) {
                    Log.e("mpex", " error on setDataSource:" + e.getMessage());
                }
                for (IEventListener listener : getListeners(OnErrorListener.class)) {
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

    private void waitreinit() {
        if (DEBUG) {
            Log.e("mpex", "call waitreinit");
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnSeekCompleteListener(null);
            mMediaPlayer.setOnBufferingUpdateListener(null);
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
            mMediaPlayer = new StrongerMediaPlayer(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("mpex", "what:" + what + ",extra:" + extra + "isPrepared:" + isPrepared());
                    if (mFileDownloader != null) {
                        if (mFileDownloader.getDownloadInfo().getCurrentSize() > latestProgressOnPrepare
                                + getMinBufferBlockSize()) {
                            latestProgressOnPrepare = mFileDownloader.getDownloadInfo().getCurrentSize();
                        }
                    }
                    if (isPrepared()) {
                        waitreinit();
                        if (mFileDownloader != null && mFileDownloader.isFinished()) {
                            tryPrepareMp();
                        }
                        return true;
                    }
                    mMPErrorHappened = true;
                    UnknownMediaPlayerException unknownMediaPlayerException = new UnknownMediaPlayerException();
                    unknownMediaPlayerException.what = what;
                    unknownMediaPlayerException.extra = extra;
                    for (IEventListener listener : getListeners(OnErrorListener.class)) {
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
                            for (IEventListener listener : getListeners(OnPositionUpdateListener.class)) {
                                ((OnPositionUpdateListener) listener).onPositionUpdate(mSavedPosition, duration);
                            }
                        }
                    }
                };
            }

            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    for (IEventListener listener : getListeners(OnSeekCompleteListener.class)) {
                        ((OnSeekCompleteListener) listener).onSeekComplete(getCurrentPosition());
                    }
                }
            });
            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    // fix bug: BufferingUpdate still can been triggered when mediaplayer is playing,
                    if (isPlaying()) {
                        percent = 100;
                    }
                    for (IEventListener listener : getListeners(OnBufferingListener.class)) {
                        ((OnBufferingListener) listener).onBuffering(percent);
                    }
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    for (IEventListener listener : getListeners(OnPlayCompleteListener.class)) {
                        ((OnPlayCompleteListener) listener).onPlayComplete(DefaultMediaPlayerImpl.this);
                    }
                }
            });
            if (mSurfaceHolder != null) {
                mMediaPlayer.setDisplay(mSurfaceHolder);
            } else if (mSurface != null) {
                mMediaPlayer.setSurface(mSurface);
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
            latestProgressOnPrepare = mFileDownloader.getDownloadInfo().getCurrentSize();
            for (IEventListener listener : getListeners(OnPreparedListener.class)) {
                ((OnPreparedListener) listener).onPrepared();
            }
        } catch (IOException e) {
            for (IEventListener listener : getListeners(OnErrorListener.class)) {
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
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (DEBUG) {
                    Log.e("mpex", "prepareAsync success");
                }
                mHasPrepared = true;
                if (mHasStarted) {
                    if (DEBUG) {
                        Log.e("mpex", "seek to : " + mSavedPosition);
                    }
                    mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                        @Override
                        public void onSeekComplete(MediaPlayer mp) {
                            mMediaPlayer.start();
                        }
                    });
                    mMediaPlayer.seekTo((int) mSavedPosition);
                    return;
                }
                latestProgressOnPrepare = mFileDownloader.getDownloadInfo().getCurrentSize();
                for (IEventListener listener : getListeners(OnPreparedListener.class)) {
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
            for (IEventListener listener : getListeners(OnStartListener.class)) {
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
            for (IEventListener listener : getListeners(OnPauseListener.class)) {
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
            for (IEventListener listener : getListeners(OnStopListener.class)) {
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
            for (IEventListener listener : getListeners(OnResetListener.class)) {
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
            for (IEventListener listener : getListeners(OnReleaseListener.class)) {
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
