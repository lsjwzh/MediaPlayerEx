package com.lsjwzh.media.mediaplayer;

import android.support.annotation.NonNull;

import java.io.File;
import java.util.EventListener;
import java.util.List;

/**
 * Created by wenye on 15/11/28.
 */
public abstract class MediaDownloader {
  private EventListenerManager mEventListenerManager = new EventListenerManager();

  public abstract void start();

  public abstract void stop();

  public abstract boolean isFinished();

  public abstract long getDownloadedSize();

  public abstract String getRemoteFileUrl();

  public abstract String getLocalFilePath();

  protected void onProgress(long progress, long length) {
    for(OnDownloadListener listener : getListeners()) {
      listener.onProgress(progress, length);
    }
  }

  protected void onSuccess(File file) {
    for(OnDownloadListener listener : getListeners()) {
      listener.onSuccess(file);
    }
  }

  protected void onError(Throwable t) {
    for(OnDownloadListener listener : getListeners()) {
      listener.onError(t);
    }
  }

  public synchronized void registerListener(@NonNull OnDownloadListener listener) {
    mEventListenerManager.registerListener(listener);
  }

  public synchronized void unregisterListener(@NonNull OnDownloadListener listener) {
    mEventListenerManager.unregisterListener(listener);
  }

  public synchronized void clearListeners() {
    mEventListenerManager.clearListeners();
  }

  @NonNull
  public synchronized List<OnDownloadListener> getListeners() {
    return mEventListenerManager.getListeners(OnDownloadListener.class);
  }

  @NonNull
  public synchronized <T extends EventListener> List<T> getListeners(
          @NonNull Class<T> pTClass) {
    return mEventListenerManager.getListeners(pTClass);
  }

  public interface OnDownloadListener extends EventListener {
    void onProgress(long progress, long length);

    void onSuccess(File pFile);

    void onError(Throwable t);
  }

  /**
   * Created by wenye on 15/11/28.
   */
  public interface MediaDownloaderFactory {
    MediaDownloader createMediaDownloader(String uri);
  }
}
