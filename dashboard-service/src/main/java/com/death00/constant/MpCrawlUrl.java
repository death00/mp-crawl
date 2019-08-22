package com.death00.constant;

/**
 * @author death00
 * @date 2019/8/21 16:04
 */
public class MpCrawlUrl {

    /**
     * 主页
     */
    public static final String HOME_URL = "https://mp.weixin.qq.com";

    /**
     * cookie中的失效字段值
     */
    public static final String EXPIRED = "EXPIRED";

    /**
     * 登录链接
     */
    public static final String LOGIN_URL = "https://mp.weixin.qq.com/cgi-bin/bizlogin?action=startlogin";

    /**
     * 登录请求返回的一个字段，通过redirectUrl可以获取token
     */
    public static final String REDIRECT_URL = "redirect_url";

    /**
     * 获取二维码的地址
     */
    public static final String QR_CODE_URL = "https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=getqrcode&param=4300";

    /**
     * 是否扫描了登录二维码
     */
    public static final String LOGIN_ASK_URL = "https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=ask&token=&lang=zh_CN&f=json&ajax=1";

    /**
     * 扫描二维码时需要更换的请求
     */
    public static final String LOGIN_AUTH_URL = "https://mp.weixin.qq.com/cgi-bin/loginauth?action=ask&token=&lang=zh_CN&f=json&ajax=1";

    /**
     * 业务登录（用来获取url上的token值）
     */
    public static final String BIZ_LOGIN = "https://mp.weixin.qq.com/cgi-bin/bizlogin?action=login&lang=zh_CN";

    /**
     * 用来获取mp_session的请求
     */
    public static final String MP_ADMIN_LOGIN = "https://game.weixin.qq.com/cgi-bin/gamewxagdatawap/mpadminlogin";
}
