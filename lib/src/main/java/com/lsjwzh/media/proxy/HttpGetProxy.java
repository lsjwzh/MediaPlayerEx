package com.lsjwzh.media.proxy;

import android.content.Context;
import android.os.AsyncTask;
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
import java.util.concurrent.TimeUnit;

/**
 * 代理服务器类
 * ToDo 分拆优化
 *
 * @author panwenye
 */
public class HttpGetProxy {

    final static public String TAG = "HttpGetProxy";
    /**
     * 避免某些Mediaplayer不播放尾部就结束
     */
    private static final int SIZE = 110 * 1024;
    WeakHashMap<Socket, Proxy> proxyTaskPool = new WeakHashMap<Socket, Proxy>();
    /**
     * 预加载所需的大小
     */
    private int mBufferSize;
    /**
     * 链接带的端口
     */
    private int remotePort = -1;
    /**
     * 远程服务器地址
     */
    private String remoteHost;
    /**
     * 代理服务器使用的端口
     */
    private int localPort;
    /**
     * 本地服务器地址
     */
    private String localHost;
    /**
     * TCP Server，接收Media Player连接
     */
    private ServerSocket localServer = null;
//	private DownloadThread downloadThread = null;
    /**
     * 服务器的Address
     */
    private SocketAddress serverAddress;
    /**
     * Response对象
     */
    private Config.ProxyResponse proxyResponse = null;
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
    private boolean isRelease;
//    private Proxy proxy2;
    private  Context mContext = null;



    /**
     * 初始化代理服务器，并启动代理服务器
     *
     * @param dirPath 缓存文件夹的路径
     * @param size    所需预加载的大小
     * @param maximum 预加载文件最大数
     */
    public HttpGetProxy(Context pContext,String dirPath, int size, int maximum) {
        try {
            mContext = pContext;
            //初始化代理服务器
            mBufferDirPath = dirPath;
            mBufferSize = size;
//            mBufferFileMaximum = maximum;
            localHost = Config.LOCAL_IP_ADDRESS;
            localServer = new ServerSocket(0, 1, InetAddress.getByName(localHost));
            localPort = localServer.getLocalPort();//有ServerSocket自动分配端口
            mEnable = true;
            //启动代理服务器
            startProxy();
        } catch (Exception e) {
            mEnable = false;
        }
    }

    private void startProxy() {
        new Thread("startProxy") {
            public void run() {
                while (!isRelease) {
                    try {
                        final Socket finalS = localServer.accept();
                        if (Config.DEBUG) Log.i(TAG, "......accept Socket...........");

                        AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                processRequest(finalS);
                                return null;
                            }
                        };
                        asyncTask.execute();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    /**
     * 处理socket请求
     *
     * @param s
     */
    public void processRequest(Socket s) {
        try {
            if (isRelease) {
                s.close();
                if (Config.DEBUG) Log.i(TAG, "......released...........");
                return;
            }
            if (Config.DEBUG) Log.i(TAG, "......started...........");
            //小米系统释放多余  socekt
            if (proxyTaskPool.size() > 5 && !Build.MODEL.contains("MI")) {
                for (Object skt : proxyTaskPool.keySet().toArray()) {
                    proxyTaskPool.get((Socket) skt).closeSockets();
                }
                if (Config.DEBUG) Log.e(TAG, "CLOSE_PROXY_POOL");
            }
            proxyTaskPool.put(s, new Proxy(s));
//            refuseOnCurrentProcess();
            proxyTaskPool.get(s).run();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, Utils.getExceptionMessage(e));
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
        remoteHost = originalURI.getHost();
        if (originalURI.getPort() != -1) {// URL带Port
            serverAddress = new InetSocketAddress(remoteHost, originalURI.getPort());// 使用默认端口
            remotePort = originalURI.getPort();// 保存端口，中转时替换
            localUrl = mMediaUrl.replace(remoteHost + ":" + originalURI.getPort(), localHost + ":" + localPort);
        } else {// URL不带Port
            serverAddress = new InetSocketAddress(remoteHost, Config.HTTP_PORT);// 使用80端口
            remotePort = -1;
            localUrl = mMediaUrl.replace(remoteHost, localHost + ":" + localPort);
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
        isRelease = true;
        for (Object skt : proxyTaskPool.keySet().toArray()) {
            proxyTaskPool.get((Socket) skt).closeSockets();
        }
        try {
            localServer.close();
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
                httpParser = new HttpParser(remoteHost, remotePort, localHost,
                        localPort);
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
                utils = new HttpGetProxyUtils(sckPlayer, serverAddress);

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
                        proxyResponse = httpParser.getProxyResponse(remote_reply,
                                bytes_read);
                        if (proxyResponse != null) {
                            // send http header to mediaplayer
                            utils.sendToMP(proxyResponse._body);
                            break;
                        }
                    }
                } else if (isExists) {
                    proxyResponse = new Config.ProxyResponse();
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
                    proxyResponse._body = fakeResponse.getBytes();
                    //样例：Content-Range: bytes 2267097-257405191/257405192
                    try {
                        // 获取起始位置
                        String currentPosition = Utils.getSubString(fakeResponse, HttpParser.CONTENT_RANGE_PARAMS, "-");
                        proxyResponse._currentPosition = Integer.valueOf(currentPosition);

                        // 获取最终位置
                        String startStr = HttpParser.CONTENT_RANGE_PARAMS + currentPosition + "-";
                        String duration = Utils.getSubString(fakeResponse, startStr, "/");
                        proxyResponse._duration = Integer.valueOf(duration);
                    } catch (Exception ex) {
                        if (Config.DEBUG) Log.e(TAG, Utils.getExceptionMessage(ex));
                    }
                    utils.sendToMP(proxyResponse._body);
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
                        + "_duration" + proxyResponse._duration
                        + "filelength:" + mediaFile.length());
                int skipCount = (int) request._rangePosition;
                boolean isNeedFetchDataFromServer = (mediaFile.length() < proxyResponse._duration + 1);//读取完毕
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
                    proxyResponse = utils.removeResponseHeader(
                            sckServer, httpParser);

                    // ------------------------------------------------------
                    // 把网络服务器的反馈发到MediaPlayer，网络服务器->代理服务器->MediaPlayer
                    // ------------------------------------------------------
                    proxyResponse._currentPosition = skipCount;
                    while (sckServer != null
                            && ((bytes_read = sckServer.getInputStream().read(
                            remote_reply)) != -1)) {
                        try {// 拖动进度条时，容易在此异常，断开重连
                            utils.sendToMP(remote_reply, bytes_read);
                            proxyResponse._currentPosition += bytes_read;
                            if (Config.DEBUG)
                                Log.d(TAG, "....sendToMP from server....:" + proxyResponse._currentPosition);
                        } catch (Exception e) {
                            if (Config.DEBUG) Log.e(TAG, e.toString());
                            if (Config.DEBUG) Log.e(TAG, Utils.getExceptionMessage(e));
                            break;// 发送异常直接退出while
                        }
                        if (proxyResponse._currentPosition >= proxyResponse._duration) {
                            break;
                        }

//                            // 已完成读取
                        if (proxyResponse._currentPosition > proxyResponse._duration - SIZE) {
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
                            remote_reply = new byte[(int) (proxyResponse._duration - proxyResponse._currentPosition + 1)];
                            while (proxyResponse._currentPosition < proxyResponse._duration &&
                                    (bytes_read = sckServer.getInputStream().read(
                                            remote_reply)) != -1) {
                                utils.sendToMP(remote_reply, bytes_read);
                                proxyResponse._currentPosition += bytes_read;
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