package com.lsjwzh.mediaplayexdemo;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.lsjwzh.media.filedownloader.FileDownloader;
import com.lsjwzh.media.mediaplayer.CacheFileMediaPlayer;
import com.lsjwzh.media.mediaplayer.MediaDownloader;
import com.lsjwzh.media.mediaplayer.MediaPlayer;

import java.io.File;
import java.util.Random;


public class MainActivity extends ActionBarActivity {
    TextureView mTextureView;
    MediaPlayer mMediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView)findViewById(R.id.video_view);
        mTextureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                    } else {
                        mMediaPlayer.start();
                    }
                }
            }
        });
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Surface s = new Surface(surface);
                initMpex(s);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMediaPlayer != null
                && mMediaPlayer.isPrepared()
                && !mMediaPlayer.isPlaying()
                && !mMediaPlayer.isReleased()) {
            mMediaPlayer.start();
        }
    }

    private void initMpex(final Surface s) {
        if(mMediaPlayer !=null){
            mMediaPlayer.clearListeners();
            mMediaPlayer.release();
        }
        mMediaPlayer = new CacheFileMediaPlayer(new MediaDownloader.MediaDownloaderFactory() {
            @Override
            public MediaDownloader createMediaDownloader(String uri) {
                return new MediaDownloaderImpl(uri);
            }
        });
        mMediaPlayer.setDataSource(MainActivity.this, "http://mofunsky-video.qiniudn.com/126/314/20150324094118997736001676.mp4");
        mMediaPlayer.setDisplay(s);
        mMediaPlayer.registerListener(MediaPlayer.OnPreparedListener.class, new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
				Log.e("mpex", " onPrepared then start");
                mp.start();
			}
		});
        mMediaPlayer.registerListener(MediaPlayer.OnErrorListener.class,new MediaPlayer.OnErrorListener(){

			@Override
			public void onError(Throwable e) {
                Log.e("mpex", " call onError listener");
            }
		});
        mMediaPlayer.prepareAsync();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class MediaDownloaderImpl extends MediaDownloader {
        FileDownloader mFileDownloader;
        String mUrl;
        String mLocalPath;

        public MediaDownloaderImpl(String url) {
            mUrl = url;
            mLocalPath = Environment.getExternalStorageDirectory()
                    + "/" + Math.abs(new Random().nextLong()) + ".mp4";
            mFileDownloader = FileDownloader.get(url, mLocalPath);
            mFileDownloader.setEventListener(new FileDownloader.EventListener() {
                @Override
                public void onProgress(long progress, long length) {
                    MediaDownloaderImpl.this.onProgress(progress, length);
                }

                @Override
                public void onSuccess(File pFile) {
                    MediaDownloaderImpl.this.onSuccess(pFile);
                }

                @Override
                public void onError(Throwable t) {
                    MediaDownloaderImpl.this.onError(t);
                }
            });
        }

        @Override
        public void start() {
            mFileDownloader.start();
        }

        @Override
        public void stop() {
            mFileDownloader.stop();
        }

        @Override
        public boolean isFinished() {
            return mFileDownloader.isFinished();
        }

        @Override
        public long getDownloadedSize() {
            return mFileDownloader.getDownloadInfo().getCurrentSize();
        }

        @Override
        public String getRemoteFileUrl() {
            return mUrl;
        }

        @Override
        public String getLocalFilePath() {
            return mLocalPath;
        }
    }
}
