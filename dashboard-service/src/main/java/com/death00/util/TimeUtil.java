package com.death00.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author death00
 * @date 2019/8/21 16:35
 */
public class TimeUtil {

    /**
     * 线程安全的日期formatter
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormat.forPattern("yyyy-MM-dd")
                    .withZone(DateTimeZone.getDefault());

    public static long getCurTime() {
        return System.currentTimeMillis();
    }

    /**
     * 根据dateStr，获取当天最开始的时间戳
     */
    public static long getStartTimestampByDateStr(String dateStr) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(dateStr));

        DateTime dateTime = new DateTime(dateStr);
        return dateTime.millisOfDay().withMinimumValue().getMillis();
    }

    /**
     * 根据dateStr，获取当天结束时的时间戳
     */
    public static long getEndTimestampByDateStr(String dateStr) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(dateStr));

        DateTime dateTime = new DateTime(dateStr);
        return dateTime.millisOfDay().withMaximumValue().getMillis();
    }


    /**
     * 根据date，获取所属日期
     */
    public static String getDateStrByDate(Date date) {
        Preconditions.checkNotNull(date);

        DateTime dateTime = new DateTime(date);
        return dateTime.toString(DATE_TIME_FORMATTER);
    }

    /**
     * 获得当前日期date
     */
    public static Date getCurDate() {
        return new Date();
    }

    /**
     * 计算当前日期减去days后的日期
     */
    public static String minusDays(String dateStr, int days) {
        Preconditions.checkNotNull(dateStr);
        Preconditions.checkArgument(days >= 0);

        DateTime dateTime = new DateTime(dateStr);
        dateTime = dateTime.minusDays(days);
        return dateTime.toString(DATE_TIME_FORMATTER);
    }
}
