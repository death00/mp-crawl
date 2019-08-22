package com.death00.service;

import com.google.common.base.Charsets;
import java.io.IOException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.springframework.stereotype.Service;

/**
 * @author death00
 * @date 2019/8/21 14:50
 */
@Service
public class HttpClientService {

    private volatile CloseableHttpAsyncClient httpAsyncClient;

    public CloseableHttpAsyncClient getHttpAsyncClient() {
        return httpAsyncClient;
    }

    public void startServer() throws IOReactorException {
        this.httpAsyncClient = createHttpAsyncClient(512, 300, 15000);
        this.httpAsyncClient.start();
    }

    public void shutdownServer() throws IOException {
        httpAsyncClient.close();
    }

    /**
     * 创建一个异步请求
     *
     * @param maxTotal 连接池最大连接数
     * @param defaultMaxPerRoute 每个主机的并发数
     * @param socketTimeout 等待数据超时时间
     */
    private CloseableHttpAsyncClient createHttpAsyncClient(
            int maxTotal,
            int defaultMaxPerRoute,
            int socketTimeout) throws IOReactorException {
        // 配置io线程
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom().
                setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .build();
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

        // 设置连接池大小
        PoolingNHttpClientConnectionManager connectionManager =
                new PoolingNHttpClientConnectionManager(ioReactor);
        connectionManager.setMaxTotal(maxTotal);
        connectionManager.setDefaultMaxPerRoute(defaultMaxPerRoute);

        // 请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(socketTimeout)
                .build();

        return HttpAsyncClients.custom().
                setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    //region httpClient

    public CloseableHttpClient createHttpClient(
            int maxTotal,
            int defaultMaxPerRoute,
            int soTimeout) {

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();

        SocketConfig socket = SocketConfig.custom()
                .setTcpNoDelay(true)
                .setSoLinger(-1)
                .setSoTimeout(soTimeout)
                .build();

        ConnectionConfig connection =
                ConnectionConfig.custom().setCharset(Charsets.UTF_8).build();

        cm.setDefaultSocketConfig(socket);
        cm.setDefaultConnectionConfig(connection);

        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(defaultMaxPerRoute);

        return HttpClients.custom()
                .setConnectionManager(cm)
                .disableAuthCaching()
                .disableCookieManagement()
                .build();
    }
}
