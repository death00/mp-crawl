package com.death00.param;

import com.death00.util.TimeUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author death00
 * @date 2019/9/5 17:18
 */
@Getter
@Setter
@Builder
public class AdInfoQueryParam {

    private int op_type;

    private Object where;

    private int page;

    private int page_size;

    private int pos_type;

    private boolean advanced;

    /**
     * 广告创建日期
     */
    private TimeRangeParam create_time_range;

    private String query_index;

    /**
     * 数据日期
     */
    private TimeRangeParam time_range;
}
