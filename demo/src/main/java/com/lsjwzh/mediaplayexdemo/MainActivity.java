package com.lsjwzh.mediaplayexdemo;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.lsjwzh.media.mediaplayerex.DefaultMediaPlayerImpl;
import com.lsjwzh.media.mediaplayerex.MediaPlayerEx;


public class MainActivity extends ActionBarActivity {
    TextureView mTextureView;
    MediaPlayerEx  mMediaPlayerEx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView)findViewById(R.id.video_view);
        mTextureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayerEx != null) {
                    if (mMediaPlayerEx.isPlaying()) {
                        mMediaPlayerEx.pause();
                    } else {
                        mMediaPlayerEx.start();
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

    private void initMpex(final Surface s) {
        if(mMediaPlayerEx!=null){
            mMediaPlayerEx.clearListeners();
            mMediaPlayerEx.release();
        }
        mMediaPlayerEx = new DefaultMediaPlayerImpl();
        mMediaPlayerEx.setDataSource(MainActivity.this, "http://mofunsky-video.qiniudn.com/126/314/20150324094118997736001676.mp4");
        mMediaPlayerEx.setDisplay(s);
        mMediaPlayerEx.registerListener(MediaPlayerEx.OnPreparedListener.class, new MediaPlayerEx.OnPreparedListener() {
			@Override
			public void onPrepared() {
				Log.e("mpex", " onPrepared then start");
				mMediaPlayerEx.start();
			}
		});
        mMediaPlayerEx.registerListener(MediaPlayerEx.OnErrorListener.class,new MediaPlayerEx.OnErrorListener(){

			@Override
			public void onError(Throwable e) {
                Log.e("mpex", " call onError listener");
            }
		});
        mMediaPlayerEx.prepareAsync();
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
}
