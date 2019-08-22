package com.death00.command;

import java.net.HttpCookie;
import java.util.LinkedList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author death00
 * @date 2019/8/21 14:56
 */
@Getter
@Setter
@Builder
public class CrawlResult {

    /**
     * 微信平台账号
     */
    private String mpAccount;

    /**
     * 微信平台密码
     */
    private String mpPassword;

    /**
     * 开始日期
     */
    private String startDateStr;

    /**
     * 结束日期
     */
    private String endDateStr;

    /**
     * 小游戏appId
     */
    private String appId;

    /**
     * 当前所属状态
     */
    private CrawlState state;

    /**
     * 是否结束
     */
    private boolean finish;

    /**
     * 本次爬取所用到的cookie
     */
    private final List<HttpCookie> cookies = new LinkedList<>();

    /**
     * 错误原因
     */
    private String errorMsg;

    /**
     * 扫描登录的二维码(Base64格式)
     */
    private String qrCodeImgStr;

    /**
     * url中的token值
     */
    private volatile String urlToken;

    /**
     * 用于获取mp_session用到的thirdUrl
     */
    private String thirdUrl;

    /**
     * mpSession值
     */
    private String mpSession;
}
