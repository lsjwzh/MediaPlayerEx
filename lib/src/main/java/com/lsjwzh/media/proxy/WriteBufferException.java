package com.lsjwzh.media.proxy;

/**
 * 名称: ${SIMPLE_CLASS_NAME}<br>
 * 简述: <br>
 * 类型: JAVA<br>
 * 最近修改时间:14-2-11 上午11:10<br>
 *
 * @author pwy
 * @since 14-2-11
 */
public class WriteBufferException extends Exception {
    public WriteBufferException() {
        super();
    }

    public WriteBufferException(String detailMessage) {
        super(detailMessage);
    }

    public WriteBufferException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public WriteBufferException(Throwable throwable) {
        super(throwable);
    }
}
