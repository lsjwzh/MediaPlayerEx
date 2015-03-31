package com.lsjwzh.media.download;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;

import com.lsjwzh.media.proxy.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by panwenye on 14-11-9.
 */
public class FileDownloader {
    /**
     * 清理sPool的阀值。在此值以下，不需要清理sPool
     */
    public static final int CHECK_POINT = 20;
    static ConcurrentHashMap<String, WeakReference<FileDownloader>> sPool = new ConcurrentHashMap<String, WeakReference<FileDownloader>>();

    static ExecutorService sThreadPool = Executors.newCachedThreadPool();

    public static synchronized void injectThreadPool(ExecutorService threadPool){
        sThreadPool = threadPool;
    }
    public static synchronized ExecutorService threadPool(){
        return sThreadPool;
    }

    public static
    @NonNull
    FileDownloader get(@NonNull String url, @NonNull String localPath) {
        FileDownloader retFileDownloader = null;
        if (sPool.containsKey(url) && sPool.get(url).get() != null) {
            retFileDownloader = sPool.get(url).get();
        }
        if (retFileDownloader == null) {
            synchronized (url) {
                if (!sPool.containsKey(url) || sPool.get(url).get() == null) {
                    retFileDownloader = new FileDownloader(url, localPath);
                    sPool.put(url, new WeakReference<FileDownloader>(retFileDownloader));
                    Log.d(TAG, "init FileDownloader");
                } else {
                    retFileDownloader = sPool.get(url).get();
                }
            }
        }
        aysncTrim();
        return retFileDownloader;
    }

    static void aysncTrim() {
        if (sPool.size() < CHECK_POINT) {
            return;
        }
        Log.d(TAG, "aysncTrim FileDownloader Pool");
        threadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] keys = new String[0];
                    keys = sPool.keySet().toArray(keys);
                    for (String key : keys) {
                        if (sPool.get(key) != null
                                && sPool.get(key).get() == null) {
                            sPool.remove(key);
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    Log.d(TAG, "aysncTrim FileDownloader Pool Error");
                } finally {
                    Log.d(TAG, "aysncTrim FileDownloader Pool Over");
                }
            }
        });
    }


    private static final String TAG = "FileDownloader";
    AtomicBoolean mIsStop = new AtomicBoolean(false);
    AtomicBoolean mIsDownloading = new AtomicBoolean(false);
    private boolean mFinished;
    String mRemoteUrl;
    String mLocalPath;
    EventListener mEventListener;
    Handler mHandler = new Handler(Looper.getMainLooper());

    private FileDownloader(String remoteUrl, String localPath) {
        mRemoteUrl = remoteUrl;
        mLocalPath = localPath;
    }

    public void start() {
        if (mIsDownloading.get()) {
            return;
        }
        mIsDownloading.set(true);
        mIsStop.set(false);
        startDownload();
    }

    protected void startDownload() {
        threadPool().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    download();
                } catch (Throwable e) {
                    postErrorEvent(e);
                }
            }
        });
    }


    public void setEventListener(EventListener pEventListener) {
        mEventListener = pEventListener;
    }
    public EventListener getEventListener(){
        return mEventListener;
    }

    public File getDownloadedFile() {
        return new File(mLocalPath);
    }

    void postErrorEvent(final Throwable e){
        e.printStackTrace();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mEventListener!=null){
                    mEventListener.onError(e);
                }
            }
        });
    }

    void postProgressEvent(final long progress,final long length){
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mEventListener!=null){
                    mEventListener.onProgress(progress, length);
                }
            }
        });
    }

    void postCompleteEvent(final File pFile){
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mEventListener!=null){
                    mEventListener.onSuccess(pFile);
                }
            }
        });
    }

    protected void download() {
        FileOutputStream out = null;
        InputStream is = null;
        long readedSize = 0;
        long mediaLength = 0;
        final File cacheFile = new File(mLocalPath);
        HttpURLConnection httpConnection;
        URL url ;
        try {
            url = new URL(mRemoteUrl);
            httpConnection = (HttpURLConnection) url
                    .openConnection();
//            System.out.println("localPath: " + localPath);

            if (!cacheFile.exists()) {
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            }

            readedSize = cacheFile.length();
            if (Config.DEBUG) {
                Log.e(TAG, "mRemoteUrl:" + mRemoteUrl);
                Log.e(TAG, "readedSize:" + readedSize);
            }
            out = new FileOutputStream(cacheFile, true);

            httpConnection.setRequestProperty("Connection", "Keep-Alive");
            httpConnection.setRequestProperty("User-Agent", "NetFox");
            httpConnection.setRequestProperty("RANGE", "bytes="
                    + readedSize + "-");

            is = httpConnection.getInputStream();

            mediaLength = httpConnection.getContentLength();

            if (Config.DEBUG) {
                Log.e(TAG, "mediaLength:" + mediaLength);
            }
            if (mediaLength == -1) {
                return;
            }
            if (mediaLength == 0) {
                onSuccess(cacheFile);
                return;
            }
            mediaLength += readedSize;//文件总长度=本次请求长度+已经下载长度

            byte buf[] = new byte[1024*2];//一次读取2k数据
            int tmpReadSize = 0;
            int readCountForProgressNotofy = 0;

            onProgress(readedSize, mediaLength);

            while ((tmpReadSize = is.read(buf)) != -1 && !mIsStop.get()) {
                readCountForProgressNotofy++;
                out.write(buf, 0, tmpReadSize);
                readedSize += tmpReadSize;
                //每读取十次（20k），发送一次进度通知
                if(readCountForProgressNotofy>=10) {
                    readCountForProgressNotofy = 0;
                    onProgress(readedSize, mediaLength);
//                    SystemClock.sleep(2000);//mock slow network
                }
            }
        } catch (OutOfMemoryError outOfMemoryError) {
            if (Config.DEBUG) {
                Log.e(TAG, "onFailure 内存不足:" + outOfMemoryError.getMessage());
            }
            mIsDownloading.set(false);
            postErrorEvent(outOfMemoryError);
        } catch (final Exception e) {
            if (e instanceof FileNotFoundException && readedSize > 0) {
                if (Config.DEBUG) {
                    Log.e(TAG, "onSuccess when FileNotFoundException and readedSize>0");
                }
                mFinished = true;
                onSuccess(cacheFile);
            } else {
                if (Config.DEBUG) {
                    Log.e(TAG, "onFailure:" + e.getMessage());
                }
                mIsDownloading.set(false);
                postErrorEvent(e);
            }
            return;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.getFD().sync();
                    out.close();
                } catch (IOException e) {
                    //
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //
                }
            }

            if (!mIsStop.get() && readedSize >= mediaLength) {
                if (Config.DEBUG) {
                    Log.e(TAG, "onSuccess:" + readedSize + ":" + mediaLength);
                }
                mFinished = true;
                onSuccess(cacheFile);
            }
        }
    }

    private void onProgress(long readSize, long mediaLength) {
        postProgressEvent(readSize,mediaLength);
        if (Config.DEBUG) {
            Log.d(TAG, "onProgress：" + readSize);
        }
    }

    private void onSuccess(final File cacheFile) {
        synchronized (mRemoteUrl) {
            sPool.remove(mRemoteUrl);
        }
        mIsDownloading.set(false);
        onProgress(cacheFile.length(), cacheFile.length());
        postCompleteEvent(cacheFile);
        if (Config.DEBUG) {
            Log.d(TAG, "onProgress end：" + cacheFile.length());
        }
    }

    public void stop() {
        setEventListener(null);
        mIsDownloading.set(false);
        mIsStop.set(true);
    }

    public boolean isStoped() {
        return mIsStop.get();
    }

    public boolean isFinished() {
        return mFinished;
    }

    public static interface EventListener{
        public void onProgress(long progress,long length);
        public void onSuccess(File pFile);
        public void onError(Throwable t);
    }

}
