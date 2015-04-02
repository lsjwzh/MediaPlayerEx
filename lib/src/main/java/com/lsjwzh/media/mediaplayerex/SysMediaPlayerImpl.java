package com.lsjwzh.media.mediaplayerex;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.lsjwzh.media.download.FileDownloader;
import com.lsjwzh.media.proxy.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by panwenye on 14-8-20.
 */
public class SysMediaPlayerImpl extends MediaPlayerEx {
    public static final int NONE = 0;
    public static final int WAIT_FOR_SET_DATASOURCE = 1;
    public static final int WAIT_FOR_BUFFERING = 2;
    public static final int WAIT_FOR_PREPARE = 3;
    static final boolean DEBUG = true;

    @IntDef({NONE, WAIT_FOR_SET_DATASOURCE, WAIT_FOR_BUFFERING, WAIT_FOR_PREPARE})
    @interface State {

    }

    StrongerMediaPlayer mMediaPlayer;
    private boolean mIsPrepared;
    private boolean mIsReleased;
    MediaMonitor mMediaMonitor;
    /**
     * seekTo will cause a error if mediaplayer have not started
     */
    private boolean mIsStarted;
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
    private int mSavedPosition;
    private boolean mErrorHappenedOnDownloading;


    @Override
    public void setDataSource(Context context, String uri) {
        initMediaPlayer();
        try {
            if (getCacheMode() == CACHE_MODE_PROXY) {
                throw new IllegalAccessError("no implementation");
            } else if (getCacheMode() == CACHE_MODE_LOCAL) {
                mStateWithLocalCache = WAIT_FOR_SET_DATASOURCE;
                //if the cache mode is local,we must transfer remote uri to local uri
                mLocalUri = getCacheDir() + File.separator + FileUtil.extractFileNameFromURI(uri);
                //if cachemode not NONE, start buffer
                if (mFileDownloader != null) {
                    mFileDownloader.stop();
                }
                mFileDownloader = FileDownloader.get(uri, mLocalUri);
                mFileDownloader.setEventListener(new FileDownloader.EventListener() {
                    @Override
                    public void onProgress(long progress, long length) {
                        if(DEBUG) {
                            Log.e("mpex", "progress changed:" + progress);
                        }
                        //when  buffer limit reached
                        if (mStateWithLocalCache == WAIT_FOR_SET_DATASOURCE
                                || mStateWithLocalCache == WAIT_FOR_PREPARE
                                || mStateWithLocalCache == WAIT_FOR_BUFFERING) {
                            if(DEBUG) {
                                Log.e("mpex","mStateWithLocalCache :"+mStateWithLocalCache);
                            }
                            long prepareBufferSize = (long) Math.min(getMinBufferBlockSize(), getPrepareBufferRate() * length);
                            if (progress - latestProgressOnPrepare >= prepareBufferSize
                                    || (mFileDownloader != null && mFileDownloader.getDownloadedFile().length() == length)) {
                                if(DEBUG) {
                                    Log.e("mpex", "current downloaded length:" + progress);
                                }
                                //when the file has been downloaded,or reached the buffer limit,excute prepare or setDataSource operation
                                if (mStateWithLocalCache == WAIT_FOR_SET_DATASOURCE
                                        || mStateWithLocalCache == WAIT_FOR_PREPARE) {
                                    if(DEBUG) {
                                        Log.e("mpex", "call setDataSource ");
                                    }
                                    try {
                                        mMediaPlayer.setDataSource(mLocalUri);
                                    } catch (IOException e) {
                                        if(DEBUG) {
                                            Log.e("mpex", " error on setDataSource:" + e.getMessage());
                                        }
                                        for (IEventListener listener : getListeners(OnErrorListener.class)) {
                                            ((OnErrorListener) listener).onError(e);
                                        }
                                        return;
                                    }
                                }
                                if (mStateWithLocalCache == WAIT_FOR_PREPARE
                                        || mStateWithLocalCache == WAIT_FOR_BUFFERING) {
                                    if(DEBUG) {
                                        Log.e("mpex", "call prepareAsync ");
                                    }
                                    prepareAsync();
                                }
                                mStateWithLocalCache = NONE;
                            } else {
                                for (IEventListener listener : getListeners(OnBufferingListener.class)) {
                                    ((OnBufferingListener) listener).onBuffering((int) ((progress - latestProgressOnPrepare) * 1f / prepareBufferSize) * 100);
                                }
                            }
                        }


                    }

                    @Override
                    public void onSuccess(File pFile) {
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
                mMediaPlayer.setDataSource(uri);
            }
        } catch (IOException e) {
            for (IEventListener listener : getListeners(OnErrorListener.class)) {
                ((OnErrorListener) listener).onError(e);
            }
        }
    }

    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new StrongerMediaPlayer(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    UnknownMediaPlayerException unknownMediaPlayerException = new UnknownMediaPlayerException();
                    unknownMediaPlayerException.what = what;
                    unknownMediaPlayerException.extra = extra;
                    return true;
                }
            });
            //use MediaMonitor to update position change
            mMediaMonitor = new MediaMonitor();
            mMediaMonitor.task = new Runnable() {
                @Override
                public void run() {
                    if (mMediaPlayer != null) {
                        mSavedPosition = mMediaPlayer.getCurrentPosition();
                        int duration = mMediaPlayer.getDuration();
                        for (IEventListener listener : getListeners(OnPositionUpdateListener.class)) {
                            ((OnPositionUpdateListener) listener).onPositionUpdate(mSavedPosition, duration);
                        }
                    }
                }
            };
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
                    //fix bug: BufferingUpdate still can been triggered when mediaplayer is playing,
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
                    if (mFileDownloader != null) {
                        latestProgressOnPrepare = mFileDownloader.getDownloadedFile().length();
                        if (!mErrorHappenedOnDownloading && !mFileDownloader.isFinished() && mSavedPosition < getDuration() - 1000) {
                            mStateWithLocalCache = WAIT_FOR_BUFFERING;
                            return;
                        }
                    }
                    for (IEventListener listener : getListeners(OnPlayCompleteListener.class)) {
                        ((OnPlayCompleteListener) listener).onPlayComplete(SysMediaPlayerImpl.this);
                    }
                }
            });
        }
    }

    @Override
    public void prepare() {
        if (mStateWithLocalCache == WAIT_FOR_SET_DATASOURCE) {
            throw new IllegalStateException("must wait for the local file to be buffered completely");
        }
        try {
            mMediaPlayer.prepare();
            mIsPrepared = true;
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
        if (mStateWithLocalCache == WAIT_FOR_SET_DATASOURCE) {
            mStateWithLocalCache = WAIT_FOR_PREPARE;
            return;
        }
        if (mMediaPlayer == null) {
            throw new IllegalStateException("must call setDatasurce firstly");
        }
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if(mSavedPosition>0){
                    mMediaPlayer.seekTo(mSavedPosition);
                }
                mIsPrepared = true;
                for (IEventListener listener : getListeners(OnPreparedListener.class)) {
                    ((OnPreparedListener) listener).onPrepared();
                }
            }
        });
        mMediaPlayer.prepareAsync();
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) {
            mMediaPlayer.start();
            mIsStarted = true;
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
        //seekTo will cause a error if mediaplayer have not been started
        if (!mIsStarted) {
            start();
            pause();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo((int) position);
        }
    }

    @Override
    public void pause() {
        if (!mIsStarted) {
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
        if (!mIsStarted) {
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
        mSavedPosition = 0;
        if (!mIsStarted) {
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
            mIsReleased = true;
            for (IEventListener listener : getListeners(OnReleaseListener.class)) {
                ((OnReleaseListener) listener).onRelease();
            }
        }
    }

    @Override
    public long getCurrentPosition() {
        if (!mIsStarted) {
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
        if (!mIsStarted) {
            return false;
        }
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public boolean isPrepared() {
        return mIsPrepared;
    }

    @Override
    public boolean isReleased() {
        return mIsReleased;
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setDisplay(holder);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @TargetApi(14)
    @Override
    public void setDisplay(Surface pSurface) {
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
}
