package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: 小雯
 * @Date: 2026/5/28
 * @Description:
 */

@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    public static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号位数
     */
    public static final int COUNT_BIT = 32;

    public StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        // 1.生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号，用Redis自增长
        // 2.1 获取当前日期，精确到天。格式化，得到"2025:05:20"
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长。例子：key为"icr:order:2025:05:20"，今天下了5个单子，count为5.
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回。左移，或运算。
        return timeStamp << COUNT_BIT | count;
    }

//   //  把「2022-01-01 00:00:00 UTC 时间」转换成从 1970-01-01 开始的秒级时间戳。
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }
}
