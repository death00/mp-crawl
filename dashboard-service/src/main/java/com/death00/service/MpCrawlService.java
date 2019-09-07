package com.death00.service;

import com.death00.command.CrawlResult;
import com.death00.command.CrawlState;
import com.death00.constant.MpCrawlUrl;
import com.death00.interceptor.LoggingClientHttpRequestInterceptor;
import com.death00.param.AdInfoQueryParam;
import com.death00.param.TimeRangeParam;
import com.death00.util.LogUtil;
import com.death00.util.TimeUtil;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * @author death00
 * @date 2019/8/21 14:35
 */
@Service
public class MpCrawlService extends Thread {

    //region init

    private final Logger logger = LoggerFactory.getLogger(MpCrawlService.class);

    private final Gson gson = new Gson();

    private final RestTemplate restTemplate;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private final CloseableHttpClient httpClient;

    @Autowired
    public MpCrawlService(
            HttpClientService httpClientService) {
        Preconditions.checkNotNull(httpClientService);
        httpClient = httpClientService.createHttpClient(300, 300, 2000);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // 设置超时
        requestFactory.setConnectTimeout(15 * 1000);
        requestFactory.setReadTimeout(15 * 1000);
        restTemplate = new RestTemplate(
                new BufferingClientHttpRequestFactory(requestFactory));
        restTemplate.setInterceptors(
                Collections.singletonList(new LoggingClientHttpRequestInterceptor()));
    }

    //endregion

    //region task

    /**
     * 所有待执行的任务
     */
    private final BlockingQueue<CrawlResult> tasks = new LinkedBlockingQueue<>();

    /**
     * 存储爬取的结果，key为mpAccount
     */
    private final ConcurrentHashMap<String, CrawlResult> all = new ConcurrentHashMap<>(10);

    /**
     * 添加任务
     */
    public boolean addTask(CrawlResult crawlResult) {
        Preconditions.checkNotNull(crawlResult);

        CrawlResult currentValue = all.putIfAbsent(crawlResult.getMpAccount(), crawlResult);
        if (currentValue != null) {
            return false;
        }

        // 添加这个任务
        tasks.add(crawlResult);
        return true;
    }

    /**
     * 根据mpAccount，获取相应的crawlResult
     */
    public CrawlResult getCrawlResult(String mpAccount) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mpAccount));

        return this.all.get(mpAccount);
    }
    //endregion

    //region thread

    public void startServer() {
        super.start();
    }

    private volatile boolean running;

    @Override
    public void run() {
        logger.info("MpCrawlService start");
        this.running = true;
        while (this.running) {
            CrawlResult task = null;
            try {
                task = tasks.poll(60 * 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }

            if (task != null) {
                try {
                    processTask(task);
                } catch (Exception e) {
                    logger.error(
                            "processTask fail, task : {} , exception : {}",
                            gson.toJson(task),
                            LogUtil.extractStackTrace(e)
                    );
                } finally {
                    task.setFinish(true);
                }
            }
        }

        logger.info("MpCrawlService end");
    }

    //region processTask

    private void processTask(CrawlResult task) throws IOException {
        Preconditions.checkNotNull(task);

        // 向首页发送请求
        sendHomeRequest(task);
        task.setState(CrawlState.HOME);

        // 发送登陆请求
        boolean sendLoginRequestResult = sendLoginRequest(task);
        if (!sendLoginRequestResult) {
            task.setErrorMsg("发送登陆请求失败");
            task.setFinish(true);
            return;
        }
        task.setState(CrawlState.LOGIN);

        // 获取登录二维码
        String imgStr = sendQrCodeRequest(task);
        if (Strings.isNullOrEmpty(imgStr)) {
            task.setErrorMsg("发送请求二维码失败");
            task.setFinish(true);
            return;
        }
        task.setState(CrawlState.REQUEST_QR_CODE);
        task.setQrCodeImgStr(imgStr);

        // 检查是否扫描了登录二维码
        boolean result = checkScanQrCode(task);
        if (!result) {
            task.setFinish(true);
            return;
        }
        task.setState(CrawlState.CHECK_QR_CODE);

        // 发送bizLogin请求，获得urlToken(url上的token值)
        result = sendBizLogin(task);
        if (!result) {
            task.setFinish(true);
            return;
        }
        task.setState(CrawlState.SEND_BIZ_LOGIN);

        // 获取thirdUrl
        String thirdUrl = getThirdUrl(task);
        if (Strings.isNullOrEmpty(thirdUrl)) {
            task.setFinish(true);
            return;
        }
        task.setThirdUrl(thirdUrl);
        task.setState(CrawlState.GET_THIRD_URL);

        // 设置mp_session
        result = setMpSession(task);
        if (!result) {
            task.setFinish(true);
            return;
        }
        task.setState(CrawlState.SET_MP_SESSION);

        // 获取广告收入
        getAdIncome(task);

        // 获取统计数据
        getStatisticData(task);

        // 获取广告投放数据
        getAdInfo(task);
    }

    /**
     * 获取广告投放相关的数据
     */
    private void getAdInfo(CrawlResult result) {
        Preconditions.checkNotNull(result);

        Date date = TimeUtil.getCurDate();
        String dateStr = TimeUtil.getDateStrByDate(date);
        // 三个月之前
        String threeMonthAgo = TimeUtil.minusDays(dateStr, 3 * 30);

        List<String> query_index = Lists.newArrayList(
                "material_preview",
                "day_budget",
                "cname",
                "product_type",
                "contract_flag",
                "status",
                "exposure_score",
                "budget",
                "bid_action_type",
                "bid",
                "bid_avg",
                "paid",
                "exp_pv",
                "clk_pv",
                "ctr",
                "comindex_name",
                "conv_index",
                "conv_index_cpa",
                "conv_index_cvr",
                "comindex",
                "cpa",
                "begin_time",
                "end_time",
                "auto_compensate_money"
        );

        AdInfoQueryParam param = AdInfoQueryParam.builder()
                .op_type(1)
                .where(new Object())
                .page(1)
                .page_size(20)
                .pos_type(997)
                .advanced(true)
                .create_time_range(
                        TimeRangeParam.builder()
                                .start_time(
                                        TimeUtil.getStartTimestampByDateStr(threeMonthAgo) / 1000)
                                .last_time(TimeUtil.getEndTimestampByDateStr(dateStr) / 1000)
                                .build()
                )
                .query_index(gson.toJson(query_index))
                .time_range(
                        TimeRangeParam.builder()
                                .start_time(TimeUtil.getStartTimestampByDateStr(dateStr) / 1000)
                                .last_time(TimeUtil.getEndTimestampByDateStr(dateStr) / 1000)
                                .build()
                ).build();

        // 构造请求头
        HttpHeaders httpHeader = getHeader(result.getCookies());
        // 构造请求
        HttpEntity<Map> requestEntity = new HttpEntity<>(httpHeader);
        // 构造uri，因为可以直接对url中的参数进行编码
        URI uri;
        try {
            uri = new URIBuilder()
                    .setScheme("https")
                    .setHost("mp.weixin.qq.com")
                    .setPath("/promotion/as_rock")
                    .addParameter("action", "get_campaign_data")
                    .addParameter("args", gson.toJson(param))
                    .addParameter("token", result.getUrlToken())
                    .addParameter("appid", "")
                    .addParameter("spid", "")
                    .addParameter("_", String.valueOf(TimeUtil.getCurTime()))
                    .build();
        } catch (Exception e) {
            logger.error(
                    "getAdInfo new URIBuilder() fail, exception : {}",
                    LogUtil.extractStackTrace(e)
            );
            return;
        }

        // 发送请求
        ResponseEntity<Map> forEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                requestEntity,
                Map.class
        );
        // 获得返回值
        Map body = forEntity.getBody();
        if (body == null) {
            return;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        logger.info("getAdInfo : {}", gson.toJson(body));
    }

    /**
     * 获取统计数据
     */
    private void getStatisticData(CrawlResult result) throws IOException {
        Preconditions.checkNotNull(result);

        String paramStr = "{\n"
                + "  \"need_app_info\": true,   \n"
                + "  \"appid\": \"" + result.getAppId() + "\",   \n"
                + "  \"sequence_index_list\": [  \n"
                + "    {\n"
                + "      \"size_type\":24,\n"
                + "      \"stat_type\":1000001,\n"
                + "      \"data_field_id\":5,\n"
                + "      \"time_period\":{\n"
                + "          \"start_time\": 1561392000,   \n"
                + "          \"duration_seconds\": 2592000   \n"
                + "      },\n"
                + "      \"requestType\":\"sequence\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"group_index_list\": [   \n"
                + "    {\n"
                + "      \"size_type\":24,\n"
                + "      \"data_field_id\":4,\n"
                + "      \"group_id\":2,\n"
                + "      \"stat_type\":1000012,\n"
                + "      \"limit\":50,   \n"
                + "      \"time_period\":{\n"
                + "          \"start_time\":1563897600,    \n"
                + "          \"duration_seconds\":86400    \n"
                + "      },\n"
                + "      \"is_stat_order_asc\":false,\n"
                + "      \"requestType\":\"group\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"rank_index_list\": [],\n"
                + "  \"version\": 0\n"
                + "}";
        HttpPost httpPost = new HttpPost(
                "https://game.weixin.qq.com/cgi-bin/gamewxagbdatawap/getwxagstatmp"
        );
        httpPost.addHeader("Cookie", result.getMpSession());
        httpPost.addHeader(HTTP.CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");
        StringEntity entity = new StringEntity(paramStr, "utf-8");
        httpPost.setEntity(entity);

        CloseableHttpResponse response = httpClient.execute(httpPost, new HttpClientContext());
        try (InputStream inputStream = response.getEntity().getContent()) {
            byte[] data;
            // 读取返回信息字节数组
            ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int rc;
            // 每次读1024位
            while ((rc = inputStream.read(buff, 0, 1024)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            data = swapStream.toByteArray();

            String resultStr = new String(data);
            logger.info(resultStr);
        } catch (Exception e) {
            logger.error(
                    "getStatisticData fail, parse response fail, appId : {} , exception : {}",
                    result.getAppId(),
                    LogUtil.extractStackTrace(e)
            );
        }
    }

    /**
     * 获取广告收入
     */
    private void getAdIncome(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 构造请求头
        HttpHeaders httpHeader = getHeader(result.getCookies());
        // 构造请求
        HttpEntity<Map> requestEntity = new HttpEntity<>(httpHeader);
        // 发送请求
        ResponseEntity<Map> forEntity = restTemplate.exchange(
                "https://mp.weixin.qq.com/wxopen/weapp_publisher_stat?action=overview"
                        + "&start_date=" + result.getStartDateStr()
                        + "&end_date=" + result.getEndDateStr()
                        + "&token=" + result.getUrlToken()
                        + "&appid=&spid=&_=" + TimeUtil.getCurTime(),
                HttpMethod.GET,
                requestEntity,
                Map.class
        );
        // 获得返回值
        Map body = forEntity.getBody();
        if (body == null) {
            return;
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        logger.info("adIncome : {}", gson.toJson(body));
    }

    /**
     * 设置mp_session
     */
    private boolean setMpSession(CrawlResult result) throws IOException {
        Preconditions.checkNotNull(result);

        // 解析url中的参数
        Map<String, String> paramMap = parseUrl(result.getThirdUrl());

        // 设置请求参数
        Map<String, String> params = Maps.newHashMapWithExpectedSize(1);
        params.put("plugin_id", "game");
        params.put("appid", paramMap.get("appid"));
        params.put("openid", paramMap.get("openid"));
        params.put("plugin_token", paramMap.get("plugin_token"));

        HttpPost httpPost = new HttpPost(MpCrawlUrl.MP_ADMIN_LOGIN);
        httpPost.addHeader(HTTP.CONTENT_TYPE, "application/x-www-form-urlencoded; charset=UTF-8");
        String body = gson.toJson(params);
        StringEntity entity = new StringEntity(body, "utf-8");
        httpPost.setEntity(entity);

        CloseableHttpResponse response = httpClient.execute(httpPost, new HttpClientContext());
        try (InputStream inputStream = response.getEntity().getContent()) {
            byte[] data;
            // 读取返回信息字节数组
            ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            byte[] buff = new byte[100];
            int rc;
            // 每次读1024位
            while ((rc = inputStream.read(buff, 0, 1024)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            data = swapStream.toByteArray();

            String resultStr = new String(data);
            Map<String, Object> resultMap = gson.fromJson(
                    resultStr,
                    new TypeToken<Map<String, Object>>() {
                    }.getType()
            );
            if (resultMap == null) {
                result.setErrorMsg("获取mpSession失败，resultMap是null");
                return false;
            }

            double errCode = (double) resultMap.get("errcode");
            if (errCode != 0) {
                result.setErrorMsg(
                        "获取mpSession失败，errcode是" + errCode + "，errmsg是" + resultMap.get("errmsg")
                );
                return false;
            }

            // 检查是否有cookie需要设置
            Header cookieHeadList = response.getFirstHeader(HttpHeaders.SET_COOKIE);
            if (cookieHeadList == null) {
                result.setErrorMsg("获取mpSession失败，返回的cookies是null");
                return false;
            }

            // 检查cookies中是否有mp_session
            boolean hasMpSession = false;
            for (HeaderElement cookie : cookieHeadList.getElements()) {
                if (cookie == null) {
                    continue;
                }

                String name = cookie.getName();
                if (Strings.isNullOrEmpty(name)) {
                    continue;
                }

                String value = cookie.getValue();
                if (Strings.isNullOrEmpty(value)) {
                    continue;
                }

                if (Objects.equals(name, "mp_session")) {
                    hasMpSession = true;
                    result.setMpSession(name + "=" + value + ";");
                }
            }

            if (!hasMpSession) {
                result.setErrorMsg("获取mpSession失败，返回的cookies中并没有mp_session");
                return false;
            }

            return true;
        } catch (Exception e) {
            result.setErrorMsg("获取mpSession失败，抛出异常，请查看后台日志");
            logger.error(
                    "setMpSession fail, parse response fail, appId : {} , exception : {}",
                    result.getMpAccount(),
                    LogUtil.extractStackTrace(e)
            );
            return false;
        }
    }

    /**
     * 解析url中的参数
     */
    private Map<String, String> parseUrl(String url) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(url));

        // 存放结果
        Map<String, String> result = Maps.newHashMap();

        // 按照"?"进行切割
        String[] urlParts = url.split("\\?");
        if (urlParts.length == 1) {
            return result;
        }

        // 按照"&"进行切割
        String[] params = urlParts[1].split("&");
        for (String param : params) {
            if (Strings.isNullOrEmpty(param)) {
                continue;
            }

            String[] keyValue = param.split("=");
            if (keyValue.length != 2) {
                continue;
            }

            String key = keyValue[0];
            if (Strings.isNullOrEmpty(key)) {
                continue;
            }

            String value = keyValue[1];
            result.put(key, value);
        }

        return result;
    }

    /**
     * 获取thirdUrl
     */
    private String getThirdUrl(CrawlResult result) throws UnsupportedEncodingException {
        Preconditions.checkNotNull(result);

        // 构造请求头
        HttpHeaders httpHeader = getHeader(result.getCookies());
        // 构造请求
        HttpEntity<Map> requestEntity = new HttpEntity<>(httpHeader);
        // 前缀
        String prefix = "https://mp.weixin.qq.com/wxamp/cgi/route?path=";
        // 后缀
        String suffix =
                "&token=" + result.getUrlToken() + "&lang=zh_CN&random=0." + TimeUtil.getCurTime();
        // 需要encode的部分
        String encodeUrl = URLEncoder
                .encode("/wxopen/frame?action=plugin_redirect&plugin_uin=1006&f=json&token="
                        + result.getUrlToken() + "&lang=zh_CN", "UTF-8");
        // 发送请求
        ResponseEntity<String> forEntity = restTemplate.exchange(
                prefix + encodeUrl + suffix,
                HttpMethod.GET,
                requestEntity,
                String.class
        );
        // 获得返回值
        String body = forEntity.getBody();
        if (body == null) {
            result.setErrorMsg("获取thirdUrl失败，返回的body为null");
            return null;
        }

        // 转换成map
        Map<String, Object> map = gson.fromJson(
                body,
                new TypeToken<Map<String, Object>>() {
                }.getType()
        );
        if (map == null) {
            result.setErrorMsg("获取thirdUrl失败，返回的body转化成的map是null");
            return null;
        }

        // 检测返回值
        double ret = (double) map.get("ret");
        if (ret != 0) {
            result.setErrorMsg("获取thirdUrl失败，ret值为" + ret + "，不是0");
            return null;
        }

        // 获得plugin_login_info
        Map<String, String> pluginLoginInfo = (Map<String, String>) map.get("plugin_login_info");
        if (pluginLoginInfo == null) {
            result.setErrorMsg("获取thirdUrl失败，plugin_login_info是null");
            return null;
        }

        // 获得third_url
        return pluginLoginInfo.get("third_url");
    }

    /**
     * 获得urlToken(url上的token值)
     */
    private boolean sendBizLogin(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 设置请求参数
        MultiValueMap<String, String> bizBodyMap = new LinkedMultiValueMap<>();
        bizBodyMap.add("lang", "zh_CN");
        bizBodyMap.add("ajax", "1");
        bizBodyMap.add("f", "json");

        // 构造请求头
        HttpHeaders bizHttpHeader = getHeader(result.getCookies());
        bizHttpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构造请求
        HttpEntity<Map> bizMapHttpEntity = new HttpEntity<>(bizBodyMap, bizHttpHeader);
        // 发送请求
        ResponseEntity<Map> biExchange = restTemplate.exchange(
                MpCrawlUrl.BIZ_LOGIN,
                HttpMethod.POST,
                bizMapHttpEntity,
                Map.class
        );
        // 获得返回值
        Map body = biExchange.getBody();
        if (body == null) {
            result.setErrorMsg("发送bizLogin并没有收到响应");
            return false;
        }

        // 获得REDIRECT_URL
        String redirectUrl = (String) body.get(MpCrawlUrl.REDIRECT_URL);
        if (Strings.isNullOrEmpty(redirectUrl)) {
            result.setErrorMsg("发送bizLogin并没有收到redirectUrl");
            return false;
        }

        // 获取urlToken
        String urlToken = getUrlTokenFromUrl(redirectUrl);
        if (Strings.isNullOrEmpty(urlToken)) {
            result.setErrorMsg("从返回的redirectUrl中，并没有获得urlToken, redirectUrl : " + redirectUrl);
            return false;
        }

        result.setUrlToken(urlToken);
        // 设置cookie
        List<String> bizCookies = biExchange.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (bizCookies != null) {
            saveCookies(bizCookies, result.getCookies());
        }

        return true;
    }

    /**
     * 从返回的url中获得urlToken
     */
    private String getUrlTokenFromUrl(String tokenRedirectUrl) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(tokenRedirectUrl));

        String[] params = tokenRedirectUrl.split("&");
        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            if ("token".equals(name)) {
                return value;
            }
        }

        return null;
    }

    /**
     * 检查是否扫描了登录二维码
     */
    private boolean checkScanQrCode(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 轮询的次数
        int count = 0, pollCount = 30;
        String url = MpCrawlUrl.LOGIN_ASK_URL;
        // 最多请求30次
        while (count < pollCount) {
            count++;
            lock.lock();
            try {
                // 停顿1秒
                condition.await(1000, TimeUnit.MILLISECONDS);

                // 构造请求头
                HttpHeaders askHeader = getHeader(result.getCookies());
                // 发送请求
                ResponseEntity<Map> askExchange = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(askHeader),
                        Map.class
                );
                // 获得返回值
                Map askBody = askExchange.getBody();
                if (askBody == null) {
                    continue;
                }

                Integer status = (Integer) askBody.get("status");
                if (status == 1) {
                    if (askBody.get("user_category") != null
                            && (Integer) askBody.get("user_category") == 1) {
                        url = MpCrawlUrl.LOGIN_AUTH_URL;
                    } else {
                        // 登录成功
                        return true;
                    }
                } else if (status == 2) {
                    // 失败
                    result.setErrorMsg("扫描二维码：管理员拒绝");
                    result.setFinish(true);
                    break;
                } else if (status == 3) {
                    // 失败
                    result.setErrorMsg("扫描二维码：登录超时");
                    result.setFinish(true);
                    break;
                } else if (status == 4) {
                    // 扫描二维码：已经扫码
                } else {
                    if (MpCrawlUrl.LOGIN_ASK_URL.equals(url)) {
                        // 扫描二维码：等待扫码
                    } else {
                        // 扫描二维码：等待确认
                    }
                }
            } catch (Exception e) {
                logger.error(
                        "checkScanQrCode fail, crawlResult : {} , exception : {}",
                        gson.toJson(result),
                        LogUtil.extractStackTrace(e)
                );
            } finally {
                lock.unlock();
            }
        }

        result.setErrorMsg("扫描二维码：超过" + pollCount + "秒未扫描");
        return false;
    }

    /**
     * 请求二维码
     */
    private String sendQrCodeRequest(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 构造请求头
        HttpHeaders httpHeader = getHeader(result.getCookies());
        httpHeader.setContentType(MediaType.IMAGE_JPEG);
        // 构造请求
        HttpEntity<Map> requestEntity = new HttpEntity<>(httpHeader);
        // 发送请求
        ResponseEntity<byte[]> forEntity = restTemplate.exchange(
                MpCrawlUrl.QR_CODE_URL,
                HttpMethod.GET,
                requestEntity,
                byte[].class
        );
        return Base64.getEncoder().encodeToString(forEntity.getBody());
    }

    /**
     * 发送登陆请求
     */
    private boolean sendLoginRequest(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 构造请求参数
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", result.getMpAccount());
        map.add("pwd", DigestUtils.md5DigestAsHex(result.getMpPassword().getBytes()));
        map.add("imgcode", "");
        map.add("f", "json");

        // 构造请求头
        HttpHeaders formHttpHeader = getHeader(result.getCookies());
        formHttpHeader.add("Accept-Encoding", "gzip,deflate");
        formHttpHeader.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // 构造请求
        HttpEntity<Map> requestEntity = new HttpEntity<>(map, formHttpHeader);
        // 发送请求
        ResponseEntity<Map> exchange = restTemplate.exchange(
                MpCrawlUrl.LOGIN_URL,
                HttpMethod.POST,
                requestEntity,
                Map.class
        );
        // 获得response
        Map response = exchange.getBody();
        // 有REDIRECT_URL字段，说明校验用户名和密码成功
        if (response == null || response.get(MpCrawlUrl.REDIRECT_URL) == null) {
            return false;
        }

        // 获得cookie
        List<String> cookies = exchange.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            saveCookies(cookies, result.getCookies());
        }

        return true;
    }

    /**
     * 向首页发送请求
     */
    private void sendHomeRequest(CrawlResult result) {
        Preconditions.checkNotNull(result);

        // 获取请求头
        HttpHeaders httpHeaders = getHeader(result.getCookies());
        // 发送请求
        ResponseEntity<String> exchange = restTemplate.exchange(
                MpCrawlUrl.HOME_URL,
                HttpMethod.GET,
                new HttpEntity<>(httpHeaders),
                String.class
        );
        // 获取repose的header
        HttpHeaders headers = exchange.getHeaders();
        // 获得header中的cookie
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            // 存储cookie
            saveCookies(cookies, result.getCookies());
        }
    }

    // basic

    /**
     * 保存cookies
     */
    private void saveCookies(List<String> newCookies, List<HttpCookie> cookies) {
        Preconditions.checkNotNull(newCookies);
        Preconditions.checkNotNull(cookies);

        // 选取cookie中不含有EXPIRED的部分
        newCookies.stream().map(HttpCookie::parse).forEachOrdered(
                (cookie) -> cookie.forEach((httpCookie) -> {
                    if (!httpCookie.getValue().contains(MpCrawlUrl.EXPIRED)) {
                        cookies.add(httpCookie);
                    } else {
                        cookies.stream().filter(
                                x -> httpCookie.getName().equals(x.getName())
                        ).findAny().ifPresent(cookies::remove);
                    }
                }));
    }

    /**
     * 获得请求头
     */
    private HttpHeaders getHeader(List<HttpCookie> cookies) {
        Preconditions.checkNotNull(cookies);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Referer", "https://mp.weixin.qq.com/");
        httpHeaders.add("Host", "mp.weixin.qq.com");
        httpHeaders.setOrigin("https://mp.weixin.qq.com");
        httpHeaders.add(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36"
        );

        // 放入cookie
        if (cookies.size() > 0) {
            List<String> newCookies = new ArrayList<>();
            for (HttpCookie cookie : cookies) {
                String str = cookie.getName() + "=" + cookie.getValue();
                newCookies.add(str);
            }
            httpHeaders.put(HttpHeaders.COOKIE, newCookies);
        }
        return httpHeaders;
    }

    //endregion

    //endregion

    //endregion
}
