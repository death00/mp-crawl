package com.death00.command;

/**
 * @author death00
 * @date 2019/8/21 14:58
 */
public enum CrawlState {

    // 初始化任务
    INIT,

    // 登录首页
    HOME,

    // 登录账号
    LOGIN,

    // 请求二维码图片
    REQUEST_QR_CODE,

    // 扫描检查二维码
    CHECK_QR_CODE,

    // 发送bizLogin
    SEND_BIZ_LOGIN,

    // 获取thirdUrl
    GET_THIRD_URL,

    // 获取mpSession
    SET_MP_SESSION
}
