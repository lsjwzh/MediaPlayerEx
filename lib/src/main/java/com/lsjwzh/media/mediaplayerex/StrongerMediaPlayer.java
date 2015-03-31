package com.lsjwzh.media.mediaplayerex;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.IntDef;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author pwy
 * @since 14-2-14
 */
public class StrongerMediaPlayer extends MediaPlayer {
    public static final String TAG = "StrongerMediaPlayer";
    private static int MSG_START = 1;
    private static int MSG_PAUSE = 2;

    private OnErrorListener errHandler;
    private OnPreparedListener onPreparedListener;
    private OnCompletionListener onCompletionListener;
    private OnSeekCompleteListener onSeekCompleteListener;
    private boolean mIsPlaying;
    private boolean mIsPreparing;
    private AtomicLong mLatestHandleTime = new AtomicLong(0);

    private Handler mOpHandler;




    public StrongerMediaPlayer(OnErrorListener errorOp) {
        mOpHandler = new Handler( Looper.getMainLooper()) {

            @Override
            public void handleMessage(final Message msg) {
                //如果最后处理时间与现在的时间差，在100ms内，则将操作放到原操作100ms之后处理
                long timeSpread = SystemClock.elapsedRealtime() - mLatestHandleTime.get();
                if (timeSpread < 100) {
                    long delayTime = 110 - timeSpread;
                    removeCallbacksAndMessages(null);
                    this.sendEmptyMessageDelayed(msg.what, delayTime);
                    return;
                }
                if (msg.what == MSG_START) {
                    try {
                        mIsPlaying = true;
                        StrongerMediaPlayer.super.start();
                    } catch (Exception e) {
                        onError();
                    }
                } else if (msg.what == MSG_PAUSE) {
                    try {
                        mIsPlaying = false;
                        StrongerMediaPlayer.super.pause();
                    } catch (Exception e) {
                        onError();
                    }
                }
                mLatestHandleTime.set(SystemClock.elapsedRealtime());
            }
        };
//        this.setErrHandler(errorOp);
        this.setOnErrorListener(errorOp);
        super.setOnCompletionListener(new OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mIsPlaying = false;
                if(getOnCompletionListener()!=null){
                    getOnCompletionListener().onCompletion(mp);
                }
            }
        });
        setScreenOnWhilePlaying(true);
    }


    @Override
    public void setDataSource(String path) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        super.setDataSource(path);
    }

    @Override
    public void setDataSource(FileDescriptor fd, long offset, long length) throws IOException, IllegalArgumentException, IllegalStateException {
        super.setDataSource(fd, offset, length);
    }

    @Override
    public void prepare() throws IOException, IllegalStateException {
        mIsPreparing = true;
        try {
            super.prepare();
        }catch (Exception e){
            e.printStackTrace();
        }
        mIsPreparing = false;
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        mIsPreparing = true;
        super.setOnErrorListener(new OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
                mIsPreparing = false;
                if(getErrHandler()!=null){
                    getErrHandler().onError(mediaPlayer,i,i2);
                }
                return false;
            }
        });
        super.setOnPreparedListener(new OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mIsPreparing = false;
                if (getOnPreparedListener() != null) {
                    getOnPreparedListener().onPrepared(mediaPlayer);
                }
            }
        });
        super.prepareAsync();
    }

    @Override
    public void start() throws IllegalStateException {
        if(mOpHandler!=null) {
            mOpHandler.handleMessage(mOpHandler.obtainMessage(MSG_START));
        }
    }

    @Override
    public void stop() throws IllegalStateException {

        try {
            super.stop();
            mIsPlaying = false;
        } catch (Exception e) {
            onError();
        }
    }

    @Override
    public void pause() throws IllegalStateException {
        if(mOpHandler!=null) {
            mOpHandler.handleMessage(mOpHandler.obtainMessage(MSG_PAUSE));
        }
    }

    @Override
    public boolean isPlaying() {
        return mIsPlaying;
    }

    @Override
    public void seekTo(final int msec) throws IllegalStateException {
        try {
            super.seekTo(msec);
        } catch (Exception e) {
            onError();
        }
    }

    @Override
    public int getCurrentPosition() {
        try {
            return super.getCurrentPosition();
        } catch (Exception e) {
            onError();
            return -1;
        }
    }

    @Override
    public void reset() {
        try {
            super.reset();
        } catch (Exception e) {
            onError();
            return;
        }
    }


    @Override
    public void release() {
        try {
           mIsPlaying=false;
            //当在准备当中时,等待准备完毕或者准备失败再释放资源
            //需要等待prepare完毕再释放资源
            if(mIsPreparing) {
                setOnErrorListener(new OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
                        reset();
                        release();
                        return false;
                    }
                });
                setOnPreparedListener(new OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        reset();
                        release();
                    }
                });
                return;
            }
            mOpHandler.removeCallbacksAndMessages(null);
            mOpHandler = null;
            setOnCompletionListener(null);
            setOnErrorListener(null);
            setOnPreparedListener(null);
            setOnSeekCompleteListener(null);
            setOnBufferingUpdateListener(null);
            setOnErrorListener(null);
            setOnInfoListener(null);
            setOnVideoSizeChangedListener(null);
            super.reset();
            super.release();
        } catch (Exception e) {
            onError();
            return;
        }
    }

    @Override
    protected void finalize() {
        if (mOpHandler != null) {
            mOpHandler.removeCallbacksAndMessages(null);
        }
        super.finalize();
    }

    private void onError() {
        mIsPlaying = false;
        if (getErrHandler() != null) getErrHandler().onError(this, 0, 0);
    }

    @Override
    public int getDuration() {
        try {
            return super.getDuration();
        } catch (Exception e) {
            onError();
            return -1;
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        super.setVolume(leftVolume, rightVolume);
    }


    public OnErrorListener getErrHandler() {
        return errHandler;
    }
    @Override
    public void setOnErrorListener(OnErrorListener listener){
        this.errHandler = listener;
        if(!mIsPreparing) {
            super.setOnErrorListener(listener);
        }

    }
    @Override
    public void setOnPreparedListener(OnPreparedListener listener) {
        this.onPreparedListener = listener;
        if(!mIsPreparing) {
            super.setOnPreparedListener(listener);
        }
    }

    @Override
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        this.onSeekCompleteListener = listener;
        super.setOnSeekCompleteListener(listener);
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletionListener = listener;
    }

    public OnPreparedListener getOnPreparedListener() {
        return onPreparedListener;
    }

    public OnCompletionListener getOnCompletionListener() {
        return onCompletionListener;
    }

    public OnSeekCompleteListener getOnSeekCompleteListener() {
        return onSeekCompleteListener;
    }

}