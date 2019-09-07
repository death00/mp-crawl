package com.death00.param;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author death00
 * @date 2019/9/5 17:21
 */
@Getter
@Setter
@Builder
public class TimeRangeParam {

    private long start_time;

    private long last_time;
}
