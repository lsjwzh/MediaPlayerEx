package com.lsjwzh.media.proxy;

import android.util.Log;

import com.lsjwzh.media.proxy.Config.ProxyResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * 代理服务器工具类
 *
 * @author hellogv
 */
public class HttpGetProxyUtils {
    final static public String TAG = "HttpGetProxyUtils";

    /**
     * 收发Media Player请求的Socket
     */
    private Socket mSckPlayer = null;

    /**
     * 服务器的Address
     */
    private SocketAddress mServerAddress;

    public HttpGetProxyUtils(Socket sckPlayer, SocketAddress address) {
        mSckPlayer = sckPlayer;
        mServerAddress = address;
    }

    /**
     * 发送预加载至服务器
     * todo OOM line 65
     *
     * @param fileName  预加载文件
     * @param skipCount skip的大小
     * @return 已发送的大小，不含skip的大小
     * @throws Exception
     */
    public int sendPrebufferToMP(String fileName, long skipCount) throws WriteBufferException {
        int fileBufferSize = 0;

        byte[] file_buffer = new byte[100 * 1024];
        int bytes_read = 0;
        long startTimeMills = System.currentTimeMillis();

        File file = new File(fileName);
        if (file.exists() == false) {
            Log.i(TAG, ">>>不存在预加载文件");
            return 0;
        }
        if (skipCount > (file.length())) {// Range大小超过预缓存的太小
            Log.i(TAG, ">>>不读取预加载文件 range:" + skipCount + ",buffer:" + file.length());
            return -3;
        }

        if (file.length() < Config.MIN_PRE_BUFFER_SIZE) {// 可用的预缓存太小，没必要读取以及重发Request
            Log.i(TAG, ">>>预加载文件太小，不读取预加载" + file.length() + ":" + Config.MIN_PRE_BUFFER_SIZE);
            return -1;
        }

        FileInputStream fInputStream = null;
        try {
            fInputStream = new FileInputStream(file);
            if (skipCount > 0) {
//                long skipByteCount = 0;
//                for (int i = 0; i < skipCount; ++i) {
//                    fInputStream.read();
//                    skipByteCount++;
//                }
                fInputStream.skip(skipCount);
                Log.i(TAG, ">>>skip:" + skipCount);
            }

            while ((bytes_read = fInputStream.read(file_buffer)) != -1) {
                mSckPlayer.getOutputStream().write(file_buffer, 0, bytes_read);
                mSckPlayer.getOutputStream().flush();
                fileBufferSize += bytes_read;//成功发送才计算
            }


            long costTime = (System.currentTimeMillis() - startTimeMills);
            Log.i(TAG, ">>>读取预加载耗时:" + costTime);
            Log.i(TAG, ">>>读取完毕...下载:" + file.length() + ",读取:" + fileBufferSize);
        } catch (Throwable t) {
            Log.i(TAG, ">>>读取预加载出错:" + fileBufferSize);
            Log.e(TAG, t.toString());
            throw new WriteBufferException(t);
        } finally {
            try {
                if (fInputStream != null)
                    fInputStream.close();
            } catch (IOException e) {
            }
        }
        return fileBufferSize;
    }

    /**
     * 把服务器的Response的Header去掉
     *
     * @throws java.io.IOException
     */
    public ProxyResponse removeResponseHeader(Socket sckServer, HttpParser httpParser) throws IOException {
        ProxyResponse result = null;
        int bytes_read;
        byte[] tmp_buffer = new byte[1024];
        while ((bytes_read = sckServer.getInputStream().read(tmp_buffer)) != -1) {
            result = httpParser.getProxyResponse(tmp_buffer, bytes_read);
            if (result == null)
                continue;// 没Header则退出本次循环

            // 接收到Response的Header
            if (result._other != null) {// 发送剩余数据
                sendToMP(result._other);
            }
            break;
        }
        return result;
    }

    public void sendToMP(byte[] bytes) throws IOException {
        if (bytes.length == 0)
            return;
        mSckPlayer.getOutputStream().write(bytes);
        mSckPlayer.getOutputStream().flush();
    }

    public void sendToMP(byte[] bytes, int length) throws IOException {
        mSckPlayer.getOutputStream().write(bytes, 0, length);
        mSckPlayer.getOutputStream().flush();
    }

    public Socket sentToServer(String requestStr) throws IOException {
        Socket sckServer = new Socket();
        sckServer.connect(mServerAddress);
        sckServer.getOutputStream().write(requestStr.getBytes());// 发送MediaPlayer的请求
        sckServer.getOutputStream().flush();
        return sckServer;
    }
}
