package com.lsjwzh.mediaplayexdemo;

import android.graphics.SurfaceTexture;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;

import com.lsjwzh.media.mediaplayerex.MediaPlayerEx;
import com.lsjwzh.media.mediaplayerex.SysMediaPlayerImpl;


public class MainActivity extends ActionBarActivity {
    TextureView mTextureView;
    MediaPlayerEx  mMediaPlayerEx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = (TextureView)findViewById(R.id.video_view);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Surface s= new Surface(surface);
                mMediaPlayerEx = new SysMediaPlayerImpl();
                mMediaPlayerEx.setCacheMode(MediaPlayerEx.CACHE_MODE_LOCAL);
                mMediaPlayerEx.setDataSource(MainActivity.this,"http://mofunsky-mfs.qiniudn.com/105/323/20150325105916006859000205_mfs.mp4");
                mMediaPlayerEx.setDisplay(s);
                mMediaPlayerEx.registerListener(MediaPlayerEx.OnPreparedListener.class,new MediaPlayerEx.OnPreparedListener(){

                    @Override
                    public void onPrepared() {
                        mMediaPlayerEx.start();
                    }
                });
                mMediaPlayerEx.prepareAsync();
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
