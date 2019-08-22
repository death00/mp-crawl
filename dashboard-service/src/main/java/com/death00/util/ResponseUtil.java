package com.death00.util;

import com.google.common.collect.Maps;
import java.util.Map;

/**
 * @author death00
 * @date 2019/8/21 14:30
 */
public class ResponseUtil {

    private static final String RESULT_CODE = "resultCode";

    private static final String DATA = "data";

    private static final String MESSAGE = "message";

    private static final int OK = 0;

    private static final int FAILED = 400;

    /**
     * 成功
     */
    public static Map<String, Object> success(String message, Object data) {
        return send(OK, message, data);
    }

    /**
     * 失败
     */
    public static Map<String, Object> fail(String message, Object data) {
        return send(FAILED, message, data);
    }

    private static Map<String, Object> send(int resultCode, String message, Object data) {
        Map<String, Object> map = Maps.newHashMapWithExpectedSize(3);
        map.put(RESULT_CODE, resultCode);
        map.put(DATA, data);
        map.put(MESSAGE, message);
        return map;
    }
}
