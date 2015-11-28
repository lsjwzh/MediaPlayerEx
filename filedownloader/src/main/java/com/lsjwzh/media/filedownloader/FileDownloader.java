package com.lsjwzh.media.filedownloader;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
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
    private static final boolean DEBUG = true;
    /**
     * 清理sPool的阀值。在此值以下，不需要清理sPool
     */
    public static final int CHECK_POINT = 20;
    private static final String TAG = "FileDownloader";
    static ConcurrentHashMap<String, WeakReference<FileDownloader>> sPool = new ConcurrentHashMap<String, WeakReference<FileDownloader>>();
    static ExecutorService sThreadPool = Executors.newCachedThreadPool();
    AtomicBoolean mIsStop = new AtomicBoolean(false);
    AtomicBoolean mIsDownloading = new AtomicBoolean(false);
    String mRemoteUrl;
    String mLocalPath;
    EventListener mEventListener;
    Handler mHandler = new Handler(Looper.getMainLooper());
    SingleThreadDownloadInfo mDownloadInfo;
    private boolean mFinished;

    private FileDownloader(String remoteUrl, String localPath) {
        mRemoteUrl = remoteUrl;
        mLocalPath = localPath;
        mDownloadInfo = new SingleThreadDownloadInfo(localPath);
        mDownloadInfo.read();
    }

    public static synchronized void injectThreadPool(ExecutorService threadPool) {
        sThreadPool = threadPool;
    }

    public static synchronized ExecutorService threadPool() {
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
                        if (sPool.get(key) != null && sPool.get(key).get() == null) {
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

    public EventListener getEventListener() {
        return mEventListener;
    }

    public void setEventListener(EventListener pEventListener) {
        mEventListener = pEventListener;
    }

    public
    @NonNull
    SingleThreadDownloadInfo getDownloadInfo() {
        return mDownloadInfo;
    }

    void postErrorEvent(final Throwable e) {
        e.printStackTrace();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mEventListener != null) {
                    mEventListener.onError(e);
                }
            }
        });
    }

    void postProgressEvent(final long progress, final long length) {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mEventListener != null) {
                    mEventListener.onProgress(progress, length);
                }
            }
        });
    }

    void postCompleteEvent(final File pFile) {
        mDownloadInfo.write();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mEventListener != null) {
                    mEventListener.onSuccess(pFile);
                }
            }
        });
    }

    protected void download() {
        InputStream is = null;
        long readedSize = 0;
        long mediaLength = 0;
        HttpURLConnection httpConnection;
        URL url;
        RandomAccessFile cacheFileRAF = null;
        try {
            url = new URL(mRemoteUrl);
            httpConnection = (HttpURLConnection) url.openConnection();
            File cacheFile = new File(mLocalPath);
            if (!cacheFile.exists()) {
                mDownloadInfo.setCurrentSize(0);
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            }
            cacheFileRAF = new RandomAccessFile(mLocalPath, "rw");// 创建一个相同大小的文件。
            readedSize = mDownloadInfo.getCurrentSize();
            if (DEBUG) {
                Log.e(TAG, "mRemoteUrl:" + mRemoteUrl);
                Log.e(TAG, "readedSize:" + readedSize);
            }
            if (mDownloadInfo.getTotalSize() > 0 && mDownloadInfo.getCurrentSize() == mDownloadInfo.getTotalSize()) {
                onSuccess(cacheFile);
                return;
            }

            httpConnection.setRequestProperty("Connection", "Keep-Alive");
            httpConnection.setRequestProperty("User-Agent", "NetFox");
            httpConnection.setRequestProperty("RANGE", "bytes=" + readedSize + "-");

            is = httpConnection.getInputStream();

            mediaLength = httpConnection.getContentLength();

            if (DEBUG) {
                Log.e(TAG, "mediaLength:" + mediaLength);
            }
            if (mediaLength == -1) {
                return;
            }
            if (mediaLength == 0) {
                onSuccess(cacheFile);
                return;
            }
            mediaLength += readedSize;// 文件总长度=本次请求长度+已经下载长度
            mDownloadInfo.setTotalSize(mediaLength);
            cacheFileRAF.setLength(mediaLength);// 设置文件大小。

            byte buf[] = new byte[1024 * 10];// 一次读取10k数据
            int tmpReadSize = 0;
            int readCountForProgressNotofy = 0;

            onProgress(readedSize, mediaLength);

            while ((tmpReadSize = is.read(buf)) != -1 && !mIsStop.get()) {
                cacheFileRAF.write(buf, 0, tmpReadSize);
                readedSize += tmpReadSize;
                mDownloadInfo.setCurrentSize(readedSize);
                readCountForProgressNotofy++;
                // 每读取十次（100k），发送一次进度通知
                if (readCountForProgressNotofy >= 10) {
                    readCountForProgressNotofy = 0;
                    onProgress(readedSize, mediaLength);
                }
            }
        } catch (OutOfMemoryError outOfMemoryError) {
            if (DEBUG) {
                Log.e(TAG, "onFailure 内存不足:" + outOfMemoryError.getMessage());
            }
            mIsDownloading.set(false);
            postErrorEvent(outOfMemoryError);
        } catch (final Exception e) {
            if (e instanceof FileNotFoundException && readedSize > 0) {
                if (DEBUG) {
                    Log.e(TAG, "onSuccess when FileNotFoundException and readedSize>0");
                }
                onSuccess(new File(mLocalPath));
            } else {
                if (DEBUG) {
                    Log.e(TAG, "onFailure:" + e.getMessage());
                }
                mIsDownloading.set(false);
                postErrorEvent(e);
            }
            return;
        } finally {
            if (cacheFileRAF != null) {
                try {
                    cacheFileRAF.close();
                    cacheFileRAF = null;
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
                if (DEBUG) {
                    Log.e(TAG, "onSuccess:" + readedSize + ":" + mediaLength);
                }
                onSuccess(new File(mLocalPath));
            }
        }
    }

    private void onProgress(long readSize, long mediaLength) {
        postProgressEvent(readSize, mediaLength);
        if (DEBUG) {
            Log.d(TAG, "onProgress：" + readSize);
        }
    }

    private void onSuccess(final File cacheFile) {
        mFinished = true;
        mDownloadInfo.write();
        synchronized (mRemoteUrl) {
            sPool.remove(mRemoteUrl);
        }
        mIsDownloading.set(false);
        onProgress(cacheFile.length(), cacheFile.length());
        postCompleteEvent(cacheFile);
        if (DEBUG) {
            Log.d(TAG, "onProgress end：" + cacheFile.length());
        }
    }

    public void stop() {
        mDownloadInfo.write();
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

    public static interface EventListener {
        public void onProgress(long progress, long length);

        public void onSuccess(File pFile);

        public void onError(Throwable t);
    }

}
