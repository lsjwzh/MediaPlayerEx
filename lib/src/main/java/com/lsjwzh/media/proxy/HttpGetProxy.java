package com.lsjwzh.media.proxy;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 代理服务器类
 * ToDo 分拆优化
 *
 * @author panwenye
 */
public class HttpGetProxy {
    final static Executor sThreadPool = Executors.newCachedThreadPool();
    final static public String TAG = "HttpGetProxy";
    /**
     * 避免某些Mediaplayer不播放尾部就结束
     */
    private static final int SIZE = 100 * 1024;//100kb
    WeakHashMap<Socket, Proxy> proxyTaskPool = new WeakHashMap<Socket, Proxy>();
    /**
     * 预加载所需的大小
     */
    private int mBufferSize;
    /**
     * 链接带的端口
     */
    private int mRemotePort = -1;
    /**
     * 远程服务器地址
     */
    private String mRemoteHost;
    /**
     * 代理服务器使用的端口
     */
    private int mLocalPort;
    /**
     * 本地服务器地址
     */
    private String mLocalHost;
    /**
     * TCP Server，接收Media Player连接
     */
    private ServerSocket mLocalServer = null;
//	private DownloadThread downloadThread = null;
    /**
     * 服务器的Address
     */
    private SocketAddress mServerAddress;
    /**
     * Response对象
     */
    private Config.ProxyResponse mProxyResponse = null;
    /**
     * 缓存文件夹
     */
    private String mBufferDirPath = null;
    /**
     * 视频id，预加载文件以ID命名
     */
    private String mId, mUrl;
    /**
     * 有效的媒体文件链接(重定向之后)
     */
    private String mMediaUrl;
    /**
     * 预加载文件路径
     */
    private String mMediaFilePath;
    /**
     * 预加载是否可用
     */
    private boolean mEnable = false;
    //	private Proxy proxy=null;
    private boolean mIsRelease;
//    private Proxy proxy2;
    private  Context mContext = null;



    /**
     * 初始化代理服务器，并启动代理服务器
     *
     * @param dirPath 缓存文件夹的路径
     * @param bufferSize    所需预加载的大小
     */
    public HttpGetProxy(Context pContext,String dirPath, int bufferSize) {
        try {
            mContext = pContext;
            //初始化代理服务器
            mBufferDirPath = dirPath;
            mBufferSize = bufferSize;
            mLocalHost = Config.LOCAL_IP_ADDRESS;
            mLocalServer = new ServerSocket(0, 1, InetAddress.getByName(mLocalHost));
            mLocalPort = mLocalServer.getLocalPort();//有ServerSocket自动分配端口
            mEnable = true;
            //启动代理服务器
            startProxy();
        } catch (Throwable e) {
            mEnable = false;
        }
    }

    /**
     * start proxy
     */
    private void startProxy() {
        new Thread("startProxy") {
            public void run() {
                while (!mIsRelease) {
                    try {
                        final Socket finalS = mLocalServer.accept();
                        if (Config.DEBUG) Log.i(TAG, "......accept Socket...........");
                        sThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                processRequest(finalS);
                            }
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * handle socket request
     *
     * @param s
     */
    public void processRequest(Socket s) {
        try {
            if (mIsRelease) {
                s.close();
                if (Config.DEBUG) Log.i(TAG, "......released...........");
                return;
            }
            if (Config.DEBUG) Log.i(TAG, "......started...........");
            //小米系统释放多余  socekt
            if (proxyTaskPool.size() > 5 && !Build.MODEL.contains("MI")) {
                for (Object skt : proxyTaskPool.keySet().toArray()) {
                    proxyTaskPool.get(skt).closeSockets();
                }
                if (Config.DEBUG) Log.e(TAG, "CLOSE_PROXY_POOL");
            }
            proxyTaskPool.put(s, new Proxy(s));
            proxyTaskPool.get(s).run();
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
    }

    /**
     * 获取播放链接
     *
     * @param id
     * @param isHandleRedirectUrl 是否处理重定向
     */
    public String getLocalURL(String id, boolean isHandleRedirectUrl) {
        mId = mUrl = id;
        String fileName = Utils.getValidFileName(mId);
        mMediaFilePath = mBufferDirPath + "/" + fileName;
        //代理服务器不可用
        if (!getEnable())
            return mUrl;
        mMediaUrl = mUrl;
        //排除HTTP特殊,如重定向
        if (isHandleRedirectUrl) {
            mMediaUrl = Utils.getRedirectUrl(mUrl);
        }
        //Log.e("Http mMediaUrl",mMediaUrl);
        // ----获取对应本地代理服务器的链接----//
        String localUrl = "";
        URI originalURI = URI.create(mMediaUrl);
        mRemoteHost = originalURI.getHost();
        if (originalURI.getPort() != -1) {// URL带Port
            mServerAddress = new InetSocketAddress(mRemoteHost, originalURI.getPort());// 使用默认端口
            mRemotePort = originalURI.getPort();// 保存端口，中转时替换
            localUrl = mMediaUrl.replace(mRemoteHost + ":" + originalURI.getPort(), mLocalHost + ":" + mLocalPort);
        } else {// URL不带Port
            mServerAddress = new InetSocketAddress(mRemoteHost, Config.HTTP_PORT);// 使用80端口
            mRemotePort = -1;
            localUrl = mMediaUrl.replace(mRemoteHost, mLocalHost + ":" + mLocalPort);
        }
        //for test
        //localUrl = mMediaUrl;
        return localUrl;
    }

    /**
     * 代理服务器是否可用
     *
     * @return
     */
    public boolean getEnable() {
        //判断外部存储器是否可用
        File dir = new File(mBufferDirPath);
        mEnable = dir.exists();
        if (!mEnable)
            return mEnable;

        //获取可用空间大小
        long freeSize = Utils.getAvailaleSize(mBufferDirPath);
        mEnable = (freeSize > mBufferSize);

        return mEnable;
    }

    public void release() {
        mIsRelease = true;
        for (Object skt : proxyTaskPool.keySet().toArray()) {
            proxyTaskPool.get((Socket) skt).closeSockets();
        }
        try {
            mLocalServer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private class Proxy {
        /**
         * 收发Media Player请求的Socket
         */
        private Socket sckPlayer = null;
        /**
         * 收发Media Server请求的Socket
         */
        private Socket sckServer = null;

        public Proxy(Socket sckPlayer) {
            this.sckPlayer = sckPlayer;
        }

        public void run() {
            if (Config.DEBUG) Log.i(TAG, "wait file create");
            HttpParser httpParser = null;
            HttpGetProxyUtils utils = null;
            int bytes_read;
            byte[] local_request = new byte[1024 * 50];
            byte[] remote_reply = new byte[1024 * 50];
            try {
                Log.i(TAG, "<----------------------------------->");
                httpParser = new HttpParser(mRemoteHost, mRemotePort, mLocalHost,
                        mLocalPort);
                //将来自mediaplayer的请求，改成对远程接口的请求
                Config.ProxyRequest request = null;
                while ((bytes_read = sckPlayer.getInputStream().read(
                        local_request)) != -1) {
                    byte[] buffer = httpParser.getRequestBody(local_request,
                            bytes_read);
                    if (buffer != null) {
                        request = httpParser.getProxyRequest(buffer);
                        break;
                    }
                }
                if (request == null) {
                    return;
                }
                //构建代理工具对象
                utils = new HttpGetProxyUtils(sckPlayer, mServerAddress);

                if (proxyTaskPool.size() > 5 && !Build.MODEL.contains("MI")) {
                    for (Object skt : proxyTaskPool.keySet().toArray()) {
                        proxyTaskPool.get((Socket) skt).closeSockets();
                    }
                    if (Config.DEBUG) Log.e(TAG, "CLOSE_PROXY_POOL");
                }

                File mediaFile = new File(mMediaFilePath);
                boolean isExists = mediaFile.exists();
                //如果有网络连接才从网络获取数据
                if (NetworkUtil.isNetConnecting(mContext)) {
                    sckServer = utils.sentToServer(request._body);// 发送MediaPlayer的request
                    while (((bytes_read = sckServer.getInputStream().read(
                            remote_reply)) != -1)) {
                        mProxyResponse = httpParser.getProxyResponse(remote_reply,
                                bytes_read);
                        if (mProxyResponse != null) {
                            // send http header to mediaplayer
                            utils.sendToMP(mProxyResponse._body);
                            break;
                        }
                    }
                } else if (isExists) {
                    mProxyResponse = new Config.ProxyResponse();
                    String fakeResponse = "HTTP/1.1 206 Partial Content\n" +
                            "Server: nginx/1.4.7\n" +
                            "Date: Wed, 07 May 2014 03:03:30 GMT\n" +
                            "Content-Type: video/webm\n" +
                            "Content-Length: " + mediaFile.length() + "\n" +
                            "Last-Modified: Wed, 07 May 2014 03:03:30 GMT\n" +
                            "Connection: keep-alive\n" +
                            "ETag: \"5369a282-12be89\"\n" +
                            "Content-Range: bytes "
                            + request._rangePosition + "-" + (mediaFile.length() - 1) + "/" + mediaFile.length();
                    mProxyResponse._body = fakeResponse.getBytes();
                    //样例：Content-Range: bytes 2267097-257405191/257405192
                    try {
                        // 获取起始位置
                        String currentPosition = Utils.getSubString(fakeResponse, HttpParser.CONTENT_RANGE_PARAMS, "-");
                        mProxyResponse._currentPosition = Integer.valueOf(currentPosition);

                        // 获取最终位置
                        String startStr = HttpParser.CONTENT_RANGE_PARAMS + currentPosition + "-";
                        String duration = Utils.getSubString(fakeResponse, startStr, "/");
                        mProxyResponse._duration = Integer.valueOf(duration);
                    } catch (Exception ex) {
                        if (Config.DEBUG) Log.e(TAG, Utils.getExceptionMessage(ex));
                    }
                    utils.sendToMP(mProxyResponse._body);
                } else {
                    int checkCount = 0;
                    //如果不存在，则等待1000ms再试一次
                    while (!isExists && checkCount < 5) {
                        if (Config.DEBUG) Log.i(TAG, "wait file create");
                        checkCount++;
//                    Thread.sleep(1000);
                        SystemClock.sleep(1000);
                        isExists = mediaFile.exists();
                    }
                    if (!isExists) {
                        closeSockets();
                        return;
                    }
                }

//                }


                // ------------------------------------------------------
                // 把网络服务器的反馈发到MediaPlayer，网络服务器->代理服务器->MediaPlayer
                // ------------------------------------------------------

                Log.i(TAG, "----------------->需要发送预加载到MediaPlayer _rangePosition:" + request._rangePosition
                        + "_duration" + mProxyResponse._duration
                        + "filelength:" + mediaFile.length());
                int skipCount = (int) request._rangePosition;
                boolean isNeedFetchDataFromServer = (mediaFile.length() < mProxyResponse._duration + 1);//读取完毕
//                if(Build.MODEL.startsWith("MI")||Build.MODEL.startsWith("HM")
//                        ||Build.ID.startsWith("HM")){
//                    if(Config.DEBUG) Log.d(TAG,"not FetchDataFromServer in model "+Build.MODEL);
//                    isNeedFetchDataFromServer = false;
//                }
                //只在三星系列手机获取网络数据
                if (!Build.MODEL.startsWith("GT")) {
                    if (Config.DEBUG)
                        Log.d(TAG, "not FetchDataFromServer in model " + Build.MODEL);
                    isNeedFetchDataFromServer = false;
                }
//                isExists = false;
//   int waitCount = 0;//等待确认缓冲完毕的次数
                //发送数据，一直到数据输出完毕
                while (isExists && sckPlayer != null && skipCount < mediaFile.length()) {// 需要发送预加载到MediaPlayer
                    //发送预加载文件
                    int sentBufferSize = utils.sendPrebufferToMP(
                            mMediaFilePath, skipCount);
                    if (sentBufferSize > 0) skipCount += sentBufferSize;
                    if (sentBufferSize == -3) {
                        //写入数据异常时，不需要再次续写。而是应当直接关闭socket
                        //本地数据不够，自己去网络获取
//                        isNeedFetchDataFromServer = true;
                        break;
                    }
                    //如果从0开始请求，且发送缓冲数据失败，则等待200ms再发送 ToDo 优化

                }
                //开始直接从服务端获取数据
                if (isNeedFetchDataFromServer) {
                    // 修改Range后的Request发送给服务器
                    int newRange = (int) (skipCount);
                    String newRequestStr = httpParser
                            .modifyRequestRange(request._body, newRange);
                    Log.i(TAG, newRequestStr);
                    try {
                        if (sckServer != null)
                            sckServer.close();
                    } catch (IOException ex) {
                        if (Config.DEBUG) Log.d(TAG, ex.toString());
                    }
                    sckServer = utils.sentToServer(newRequestStr);
                    // 把服务器的Response的Header去掉
                    mProxyResponse = utils.removeResponseHeader(
                            sckServer, httpParser);

                    // ------------------------------------------------------
                    // 把网络服务器的反馈发到MediaPlayer，网络服务器->代理服务器->MediaPlayer
                    // ------------------------------------------------------
                    mProxyResponse._currentPosition = skipCount;
                    while (sckServer != null
                            && ((bytes_read = sckServer.getInputStream().read(
                            remote_reply)) != -1)) {
                        try {// 拖动进度条时，容易在此异常，断开重连
                            utils.sendToMP(remote_reply, bytes_read);
                            mProxyResponse._currentPosition += bytes_read;
                            if (Config.DEBUG)
                                Log.d(TAG, "....sendToMP from server....:" + mProxyResponse._currentPosition);
                        } catch (Exception e) {
                            if (Config.DEBUG) Log.e(TAG, e.toString());
                            if (Config.DEBUG) Log.e(TAG, Utils.getExceptionMessage(e));
                            break;// 发送异常直接退出while
                        }
                        if (mProxyResponse._currentPosition >= mProxyResponse._duration) {
                            break;
                        }

//                            // 已完成读取
                        if (mProxyResponse._currentPosition > mProxyResponse._duration - SIZE) {
                            if (Config.DEBUG) Log.i(TAG, "....ready....over....");
//                            Schedulers.io().createWorker().schedule(new Action0() {
//                                @Override
//                                public void call() {
//                                    try {
//                                        closeSockets();
//                                    } finally {
//                                        if (Config.DEBUG)
//                                            Log.e(TAG, "close proxy after  ready....over 3s");
//                                    }
//                                }
//                            }, 3000, TimeUnit.MILLISECONDS);
                            //读取最后部分数据
                            remote_reply = new byte[(int) (mProxyResponse._duration - mProxyResponse._currentPosition + 1)];
                            while (mProxyResponse._currentPosition < mProxyResponse._duration &&
                                    (bytes_read = sckServer.getInputStream().read(
                                            remote_reply)) != -1) {
                                utils.sendToMP(remote_reply, bytes_read);
                                mProxyResponse._currentPosition += bytes_read;
                                if (Config.DEBUG)
                                    Log.d(TAG, "....read from server end......" + bytes_read);
                                //超过3s强关socket
                            }
                            break;
                        }
                    }
                    if (Config.DEBUG) Log.i(TAG, "....read from server....over....");

                }
                // 关闭 2个SOCKET
            } catch (Exception e) {
                if (Config.DEBUG) Log.e(TAG, e.toString());
                if (Config.DEBUG) Log.e(TAG, Utils.getExceptionMessage(e));
            } finally {
                closeSockets();
            }
        }

        /**
         * 关闭现有的链接
         */
        public void closeSockets() {
            if (Config.DEBUG) Log.i(TAG, "<---------------closeSockets-------------------->");
            try {// 开始新的request之前关闭过去的Socket
                if (sckPlayer != null) {
                    sckPlayer.close();
                    proxyTaskPool.remove(sckPlayer);
                    sckPlayer = null;
                }

                if (sckServer != null) {
                    sckServer.close();
                    sckServer = null;
                }
            } catch (IOException e1) {
                if (Config.DEBUG) Log.d(TAG, e1.toString());
            }
        }
    }
}