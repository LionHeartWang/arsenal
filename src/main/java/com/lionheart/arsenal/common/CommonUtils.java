package com.lionheart.arsenal.common;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by wangyiguang on 17/8/3.
 */
public class CommonUtils {
    public static String dateNDaysBeforeNow(String format, int n) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        calendar.add(Calendar.DATE, -1 * n);
        return sdf.format(calendar.getTime());
    }

    public static String dateNDaysAfterNow(String format, int n) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        calendar.add(Calendar.DATE, n);
        return sdf.format(calendar.getTime());
    }
}
