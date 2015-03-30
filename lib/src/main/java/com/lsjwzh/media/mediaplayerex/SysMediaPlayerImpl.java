package com.lsjwzh.media.mediaplayerex;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;

import com.lsjwzh.media.download.FileDownloader;

import com.lsjwzh.media.proxy.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by panwenye on 14-8-20.
 */
public class SysMediaPlayerImpl extends MediaPlayerEx {
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
     *  only for LOCAL CACHE MODE
     */
    boolean mWaitForSetDataSource;
    boolean mWaitForPrepareAsync;

    @Override
    public void setDataSource(Context context, String uri) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new StrongerMediaPlayer(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    MediaPlayerEx.UnknownMediaPlayerException unknownMediaPlayerException = new UnknownMediaPlayerException();
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
                        int currentPosition = mMediaPlayer.getCurrentPosition();
                        int duration = mMediaPlayer.getDuration();
                        for (IEventListener listener : getListeners(OnPositionUpdateListener.class)) {
                            ((OnPositionUpdateListener)listener).onPositionUpdate(currentPosition, duration);
                        }
                    }
                }
            };
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    for (IEventListener listener : getListeners(OnSeekCompleteListener.class)) {
                        ((OnSeekCompleteListener)listener).onSeekComplete(getCurrentPosition());
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
                        ((OnBufferingListener)listener).onBuffering(percent);
                    }
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {

//                    isVideoBuffered().
//                            subscribe(new SubscriberBase<Boolean>() {
//                                @Override
//                                public void doOnNext(Boolean isVideoBuffered) {
//                                    if (!isVideoBuffered
//                                            || mSavedPosition < getMediaController().getDuration() - 1500) {
//                                        //文件提前结束
//                                        waitToReprepare();
//                                        return;
//                                    } else {
//                                        if (AppConfig.DEBUG) {
//                                            Log.e(TAG, "set mIsVideoComplete = true");
//                                        }
//                                        mIsVideoComplete = true;
//                                        if (mFavSectionWrapper != null) {
//                                            mFavSectionWrapper.setVisibility(View.VISIBLE);
//                                        }
//                                        isPlayingBeforeTracking = false;
//                                        if(mVideoControllerView!=null
//                                                &&mVideoControllerView.mVideoControllerRepeatSwitch.isChecked()){
//                                            startOrPauseVideo();
//                                        }
//                                    }
//                                }
//                            });
                    for (IEventListener listener : getListeners(OnPlayCompleteListener.class)) {
                        ((OnPlayCompleteListener)listener).onPlayComplete(SysMediaPlayerImpl.this);
                    }
                }
            });
        }
        try {
            if (getCacheMode() == CACHE_MODE_PROXY) {
                throw new IllegalAccessError("no implementation");
            } else if (getCacheMode() == CACHE_MODE_LOCAL) {
                mWaitForSetDataSource = true;
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
                        //when  buffer limit reached
                        if(mWaitForSetDataSource) {
                            long prepareBufferSize = (long) Math.max(getMinBufferBlockSize(), getPrepareBufferRate() * length);
                            if (progress >= prepareBufferSize) {
                                mWaitForSetDataSource = false;
                                try {
                                    mMediaPlayer.setDataSource(mLocalUri);
                                } catch (IOException e) {
                                    for (IEventListener listener : getListeners(OnErrorListener.class)) {
                                        ((OnErrorListener)listener).onError(e);
                                    }
                                }
                                if(mWaitForPrepareAsync){
                                    mWaitForPrepareAsync = false;
                                    prepareAsync();
                                }
                            }
                        }
                    }

                    @Override
                    public void onSuccess(File pFile) {

                    }

                    @Override
                    public void onError(Throwable t) {
                        for (IEventListener listener : getListeners(OnErrorListener.class)) {
                            ((OnErrorListener)listener).onError(t);
                        }
                    }
                });
                mFileDownloader.start();
            }else {
                mMediaPlayer.setDataSource(uri);
            }
        } catch (IOException e) {
            for (IEventListener listener : getListeners(OnErrorListener.class)) {
                ((OnErrorListener)listener).onError(e);
            }
        }
    }

    @Override
    public void prepare() {
        if(mWaitForSetDataSource){
            throw new IllegalStateException("must wait for the local file to be buffered completely");
        }
        try {
            mMediaPlayer.prepare();
            mIsPrepared = true;
            for (IEventListener listener : getListeners(OnPreparedListener.class)) {
                ((OnPreparedListener)listener).onPrepared();
            }
        } catch (IOException e) {
            for (IEventListener listener : getListeners(OnErrorListener.class)) {
                ((OnErrorListener)listener).onError(e);
            }
        }
    }

    @Override
    public void prepareAsync() {
        if(mWaitForSetDataSource){
            mWaitForPrepareAsync = true;
            return;
        }
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mIsPrepared = true;
                for (IEventListener listener : getListeners(OnPreparedListener.class)) {
                    ((OnPreparedListener)listener).onPrepared();
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
                ((OnStartListener)listener).onStart();
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
                ((OnPauseListener)listener).onPause();
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
                ((OnStopListener)listener).onStop();
            }
        }
    }

    @Override
    public void reset() {
        if (!mIsStarted) {
            return;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            if (mMediaMonitor != null) {
                mMediaMonitor.pause();
            }
            for (IEventListener listener : getListeners(OnResetListener.class)) {
                ((OnResetListener)listener).onReset();
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
                ((OnReleaseListener)listener).onRelease();
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
