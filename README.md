# wechat-mp-data-crawl

微信公众平台数据爬取

## 介绍

用于抓取微信公众平台内部数据的spring项目，模拟从扫描二微信登录微信公众平台到最后获取到各部分统计数据的功能。

## 功能

- [x] [添加抓取任务](#添加抓取任务)
- [x] [检查任务爬取的状态](#检查任务爬取的状态)
- [ ] 重新启动抓取任务

## 安装

进入下载好的文件目录下，运行命令：
```
gradlew clean build
```

命令运行完成后，`dashboard-service`的`build/distributions`目录下会生成相应的打包文件

## 测试网站地址

http://www.death00.top/mp-crawl

## API列表

### 添加抓取任务

请求url `/dashboard-service/crawl/add`

传入参数
```
mpAccount 微信公众平台账号
mpPassword 微信公众平台密码
startDateStr 查询的起始日期
endDateStr 查询的结束日期
```
### 检查任务爬取的状态

请求url `/dashboard-service/crawl/check`

传入参数
```
mpAccount 微信公众平台账号
```

## 微信公众平台登录过程

### Headers中默认内容

| 属性 | 内容 |
|:---: |:---: |
|Referer|`https://mp.weixin.qq.com/`|
|Host|`mp.weixin.qq.com`|
|Origin|`https://mp.weixin.qq.com`|
|User-Agent|`Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36`|

### 首页请求

1. 目的：获取请求头中相关的cookie

2. 请求内容
```
url : https://mp.weixin.qq.com
method : GET
```
3. 将response的header中所有`Set-Cookie`设置到请求头中

4. 此时任务状态变为`HOME`

### 发送登陆请求

1. 目的：验证用户名和密码

2. 请求内容
```
url : https://mp.weixin.qq.com/cgi-bin/bizlogin?action=startlogin
method : POST
请求头增加内容：
  Content-Type : application/x-www-form-urlencoded
  Accept-Encoding : gzip,deflate
传入参数：
  username : 你的微信公众平台账号
  pwd : 你的微信公众平台密码（MD5加密）
  imgcode : 
  f : json
```

3. 如果返回的值中有`redirect_url`，说明验证成功

4. 将response的header中所有`Set-Cookie`设置到请求头中

5. 此时任务状态变为`LOGIN`

### 获取登录二维码

1. 目的：获取需要扫描的二维码

2. 请求内容
```
url : https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=getqrcode&param=4300
method : GET
请求头增加内容：
  Content-Type : image/jpeg
```

3. 将获得返回值转换成Base64格式的字符串，赋值给`result`中的属性`qrCodeImgStr`

4. 将response的header中所有`Set-Cookie`设置到请求头中

5. 此时任务状态变为`REQUEST_QR_CODE`

6. 此时你可以发起请求[检查任务爬取的状态](#检查任务爬取的状态)，获取二维码的字符串，转化为图片进行扫描（我用的是`http://www.vgot.net/test/image2base64.php?`）

### 检查是否扫描了登录二维码

1. 目的：检查是否扫描了登录二维码，如果超过30秒，则直接失败

2. 请求过程
```java
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
```

3. 特殊参数讲解
```
MpCrawlUrl.LOGIN_ASK_URL https://mp.weixin.qq.com/cgi-bin/loginqrcode?action=ask&token=&lang=zh_CN&f=json&ajax=1
MpCrawlUrl.LOGIN_AUTH_URL https://mp.weixin.qq.com/cgi-bin/loginauth?action=ask&token=&lang=zh_CN&f=json&ajax=1
```

4. 将response的header中所有`Set-Cookie`设置到请求头中

### 发送bizLogin请求

1. 目的：获得urlToken(url上的token值)

2. 请求内容
```
url : https://mp.weixin.qq.com/cgi-bin/bizlogin?action=login&lang=zh_CN
method : POST
请求头增加内容：
  Content-Type : application/x-www-form-urlencoded
传入参数：
  lang : zh_CN
  ajax : 1
  f : json
```

3. 从返回值中获取`redirect_url`包含`token`的值，赋值给`result`中的属性`urlToken`

4. 将response的header中所有`Set-Cookie`设置到请求头中

5. 此时任务状态变为`SEND_BIZ_LOGIN`

### 获取thirdUrl

1. 目的：用于获取能得到mp_session的网址

2. 请求过程
```java
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
```

3. 此时任务状态变为`GET_THIRD_URL`

### 获取mp_session

1. 目的：用于后续获取统计数据

2. 请求内容
```
url : https://game.weixin.qq.com/cgi-bin/gamewxagdatawap/mpadminlogin
method : POST
请求头增加内容：
  Content-Type : application/x-www-form-urlencoded
传入参数：
  plugin_id : game
  appid : 从thirdUrl中获取
  openid : 从thirdUrl中获取
  plugin_token : 从thirdUrl中获取
```

3. 获取response的header中所有`Set-Cookie`，找出`mp_session`

4. 此时任务状态变为`SET_MP_SESSION`

