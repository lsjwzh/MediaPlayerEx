package com.lsjwzh.media.mediaplayerex;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.support.annotation.IntDef;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.lsjwzh.media.proxy.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by panwenye on 14-8-20.
 */
public class MediaPlayerWithProxyImpl extends MediaPlayerEx {
    public static final int NONE = 0;
    public static final int WAIT_FOR_SET_DATASOURCE = 1;
    public static final int WAIT_FOR_BUFFERING = 2;
    public static final int WAIT_FOR_PREPARE = 3;
    static final boolean DEBUG = true;

    @IntDef({NONE, WAIT_FOR_SET_DATASOURCE, WAIT_FOR_BUFFERING, WAIT_FOR_PREPARE})
    @interface State {

    }

    StrongerMediaPlayer mMediaPlayer;
    private boolean mHasPrepared;
    private boolean mHasReleased;
    MediaMonitor mMediaMonitor;
    /**
     * seekTo will cause a error if mediaplayer have not started
     */
    private boolean mHasStarted;
    /**
     * proxy url
     */
    String mProxyUri;



    @Override
    public void setDataSource(Context context, String uri) {
        initMediaPlayer();
        try {
            if (uri.startsWith("http:")) {
                //if the cache mode is local,we must transfer remote uri to local uri
                mProxyUri = getCacheDir() + File.separator + FileUtil.extractFileNameFromURI(uri);
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
                        int duration = mMediaPlayer.getDuration();
                        for (IEventListener listener : getListeners(OnPositionUpdateListener.class)) {
                            ((OnPositionUpdateListener) listener).onPositionUpdate(mMediaPlayer.getCurrentPosition(), duration);
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
                    for (IEventListener listener : getListeners(OnPlayCompleteListener.class)) {
                        ((OnPlayCompleteListener) listener).onPlayComplete(MediaPlayerWithProxyImpl.this);
                    }
                }
            });
        }
    }

    @Override
    public void prepare() {
        try {
            mMediaPlayer.prepare();
            mHasPrepared = true;
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
        if (mMediaPlayer == null) {
            throw new IllegalStateException("must call setDatasurce firstly");
        }
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mHasPrepared = true;
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
        //seekTo will cause a error if mediaplayer have not been started
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

    @Override
    public void setLooping(boolean looping) {

    }
}
