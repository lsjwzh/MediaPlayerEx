package com.lsjwzh.mediaproxy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * $CLASS_NAME
 *
 * @author Green
 * @since: 15/3/26 下午12:14
 */
public class NetworkUtil {
    public static boolean isWifiConnecting(Context pContext) {
        try {
            WifiManager wifi = (WifiManager) pContext.getSystemService(Context.WIFI_SERVICE);
            return wifi.isWifiEnabled();//返回true时表示存在，
        } catch (Exception e) {
            Log.e("error", e.toString());
            return false;
        }
    }

    public static boolean isNetConnecting(Context pContext) {
        // 获取手机所有连接管理对象（包括对wi-fi,net等连接的管理）
        try {
            ConnectivityManager connectivity = (ConnectivityManager)pContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                // 获取网络连接管理的对象
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    // 判断当前网络是否已经连接
                    if (info.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("error", e.toString());
        }
        return false;
    }}
