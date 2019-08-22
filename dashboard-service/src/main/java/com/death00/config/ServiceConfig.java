package com.death00.config;

import com.death00.service.HttpClientService;
import com.death00.service.MpCrawlService;
import com.google.common.base.Preconditions;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.http.nio.reactor.IOReactorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * @author death00
 * @date 2019/8/21 14:45
 */
@Configuration
public class ServiceConfig {

    private final MpCrawlService mpCrawlService;

    private final HttpClientService httpClientService;

    @Autowired
    public ServiceConfig(
            MpCrawlService mpCrawlService,
            HttpClientService httpClientService) {
        Preconditions.checkNotNull(mpCrawlService);
        this.mpCrawlService = mpCrawlService;

        Preconditions.checkNotNull(httpClientService);
        this.httpClientService = httpClientService;
    }

    @PostConstruct
    public void init() throws IOReactorException {
        if (mpCrawlService != null) {
            mpCrawlService.startServer();
        }

        if (httpClientService != null) {
            httpClientService.startServer();
        }
    }

    @PreDestroy
    public void preDestroy() throws IOException {
        if (httpClientService != null) {
            httpClientService.shutdownServer();
        }
    }
}
