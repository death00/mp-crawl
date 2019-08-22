package com.death00.controller;

import com.death00.command.CrawlResult;
import com.death00.command.CrawlState;
import com.death00.service.MpCrawlService;
import com.death00.util.LogUtil;
import com.death00.util.ResponseUtil;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Map;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author death00
 * @date 2019/8/21 14:26
 */
@Controller
@RequestMapping("/crawl")
public class CrawlController {

    private final Logger logger = LoggerFactory.getLogger(CrawlController.class);

    private final MpCrawlService mpCrawlService;

    @Autowired
    public CrawlController(MpCrawlService mpCrawlService) {
        Preconditions.checkNotNull(mpCrawlService);
        this.mpCrawlService = mpCrawlService;
    }

    /**
     * 开始一个爬取任务
     *
     * @param mpAccount 微信公众平台账号
     * @param mpPassword 微信公众平台密码
     */
    @RequestMapping("/add")
    @ResponseBody
    public Map<String, Object> add(
            @RequestParam String mpAccount,
            @RequestParam String mpPassword,
            @RequestParam String startDateStr,
            @RequestParam String endDateStr,
            @RequestParam String appId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mpAccount));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mpPassword));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(startDateStr));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(endDateStr));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(appId));

        CrawlResult crawlResult = CrawlResult.builder()
                .mpAccount(mpAccount)
                .mpPassword(mpPassword)
                .startDateStr(startDateStr)
                .endDateStr(endDateStr)
                .appId(appId)
                .state(CrawlState.INIT)
                .build();
        boolean result = mpCrawlService.addTask(crawlResult);
        if (result) {
            return ResponseUtil.success("添加任务成功", null);
        } else {
            return ResponseUtil.fail("添加任务失败，该任务已经存在", null);
        }
    }

    /**
     * 检查任务爬取的状态
     *
     * @param mpAccount 微信公众平台账号
     */
    @RequestMapping("/check")
    @ResponseBody
    public Map<String, Object> check(@RequestParam String mpAccount) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mpAccount));

        CrawlResult result = mpCrawlService.getCrawlResult(mpAccount);
        if (result != null) {
            return ResponseUtil.success(null, result);
        } else {
            return ResponseUtil.fail("获取失败", null);
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Map<String, Object> exceptionHandler(Exception e, Request request) {

        HttpURI uri = request.getHttpURI();
        String pathQuery = uri == null ? null : uri.getPathQuery();

        logger.error(
                "CrawlController operation pathQuery: {} throw exception: {}",
                pathQuery,
                LogUtil.extractStackTrace(e)
        );
        return ResponseUtil.fail("异常", null);
    }
}
