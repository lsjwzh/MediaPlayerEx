package com.lsjwzh.media.filedownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

import android.support.annotation.NonNull;

import com.google.gson.Gson;

public class MultiThreadDownloadInfo {
	private List<Range> mRanges = new ArrayList<Range>();
	private String mDownloadedFilePath;
	private long mCurrentSize;
	private long mTotalSize;

	public MultiThreadDownloadInfo(@NonNull String filePath) {
		mDownloadedFilePath = filePath;
	}

	public void addRange(Range range) {
		mRanges.add(range);
	}

	public void removeRange(Range range) {
		mRanges.remove(range);
	}

	public List<Range> getRanges() {
		return mRanges;
	}

	public void read() {
		StringBuffer sb = new StringBuffer();
		FileReader fr = null;
		try {
			fr = new FileReader(new File(mDownloadedFilePath + ".m"));
			CharBuffer charBuffer = CharBuffer.allocate(1024);
			int readSize = 0;
			while ((readSize = fr.read(charBuffer)) > 0) {
				sb.append(charBuffer, 0, readSize);
			}
			MultiThreadDownloadInfo obj = new Gson().fromJson(sb.toString(), MultiThreadDownloadInfo.class);
			this.mRanges = obj.mRanges;
			this.mCurrentSize = obj.mCurrentSize;
			this.mTotalSize = obj.mTotalSize;
			this.mDownloadedFilePath = obj.mDownloadedFilePath;
		} catch (IOException e) {
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
			fos.write(new Gson().toJson(this).getBytes("utf-8"));
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
