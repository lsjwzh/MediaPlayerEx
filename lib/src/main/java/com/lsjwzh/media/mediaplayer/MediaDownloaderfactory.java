package com.lsjwzh.media.mediaplayer;

/**
 * Created by wenye on 15/11/28.
 */
public interface MediaDownloaderFactory {
  MediaDownloader createMediaDownloader(String uri);
}
