package com.lsjwzh.mediaproxy;

/**
 * Config
 *
 */
public class Config {

    final static public String LOCAL_IP_ADDRESS = "127.0.0.1";
    final static public int HTTP_PORT = 8109;
    final static public String HTTP_BODY_END = "\r\n\r\n";
    final static public String HTTP_RESPONSE_BEGIN = "HTTP/";
    final static public String HTTP_REQUEST_BEGIN = "GET ";
    final static public String HTTP_REQUEST_LINE1_END = " HTTP/";
    final static public int MIN_PRE_BUFFER_SIZE = 50 * 1024;//50k
    public static final boolean DEBUG = true;


    static public class ProxyRequest {
        /**
         * Http Request 内容
         */
        public String _body;
        /**
         * Ranage的位置
         */
        public long _rangePosition;
    }

    static public class ProxyResponse {
        public byte[] _body;
        public byte[] _other;
        public long _currentPosition;
        public long _duration;
    }
}