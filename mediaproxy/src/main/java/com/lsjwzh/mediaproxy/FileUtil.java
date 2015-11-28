package com.lsjwzh.mediaproxy;

/**
 * $CLASS_NAME
 *
 * @author Green
 * @since: 15/3/26 上午2:07
 */
public class FileUtil {
    public static String extractFileNameFromURI(String uri) {
        if (uri == null) {
            return "";
        }
        String[] nodes = uri.split("/");
        String lastNode = nodes[nodes.length - 1];
        if(!lastNode.contains(".")){
            return "";
        }
        if(lastNode.contains("?")){
            return lastNode.substring(0,lastNode.indexOf("?"));
        }
        return lastNode;
    }
}
