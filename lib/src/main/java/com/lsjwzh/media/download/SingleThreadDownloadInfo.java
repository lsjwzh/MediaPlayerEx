package com.lsjwzh.media.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import android.support.annotation.NonNull;

import com.google.gson.Gson;

public class SingleThreadDownloadInfo {
    private String mDownloadedFilePath;
    private long mCurrentSize;
    private long mTotalSize;

    public SingleThreadDownloadInfo(@NonNull String filePath) {
        mDownloadedFilePath = filePath;
    }

    public void read() {
        StringBuffer sb = new StringBuffer();
        FileInputStream fr = null;
        try {
            fr = new FileInputStream(new File(mDownloadedFilePath + ".m"));
            byte[] buffer = new byte[512];
            int readSize = 0;
            while ((readSize = fr.read(buffer)) > 0) {
                String string = new String(buffer, 0, readSize, Charset.defaultCharset().name());
                sb.append(string);
            }
            SingleThreadDownloadInfo obj = new Gson().fromJson(sb.toString(), SingleThreadDownloadInfo.class);
            this.mCurrentSize = obj.mCurrentSize;
            this.mTotalSize = obj.mTotalSize;
            this.mDownloadedFilePath = obj.mDownloadedFilePath;
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void write() {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mDownloadedFilePath + ".m");
            fos.write(new Gson().toJson(this).getBytes(Charset.defaultCharset().name()));
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public long getCurrentSize() {
        return mCurrentSize;
    }

    public void setCurrentSize(long mCurrentSize) {
        this.mCurrentSize = mCurrentSize;
    }

    public long getTotalSize() {
        return mTotalSize;
    }

    public void setTotalSize(long mTotalSize) {
        this.mTotalSize = mTotalSize;
    }

    public static class Range {
        public long mStart;
        public long mEnd;

        public Range(long start, long end) {
            mStart = start;
            mEnd = end;
        }
    }
}
