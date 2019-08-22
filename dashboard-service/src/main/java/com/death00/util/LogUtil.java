package com.death00.util;

/**
 * @author death00
 * @date 2019/8/21 14:30
 */
public class LogUtil {

    /**
     * 生成错误信息
     */
    public static String extractStackTrace(Throwable e) {
        if (e == null) {
            return "";
        }

        StackTraceElement[] list = e.getStackTrace();
        if (list == null || list.length <= 0) {
            return e.getMessage();
        }

        StringBuilder buf = new StringBuilder();
        buf.append(e.getClass()).append('\n');
        buf.append("message: ").append(e.getMessage()).append('\n');

        for (StackTraceElement s : list) {
            if (s == null) {
                continue;
            }

            buf.append(s).append('\n');
        }

        return buf.toString();
    }
}
