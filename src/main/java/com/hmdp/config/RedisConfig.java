package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @Author: 小雯
 * @Date: 2026/6/2
 * @Description:
 */

@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient getRedissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }

//    @Bean
//    public RedissonClient getRedissonClient8080(){
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://127.0.0.1:6380");
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient getRedissonClient8081(){
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://127.0.0.1:6381");
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient getRedissonClient8082(){
//        // 配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://127.0.0.1:6382");
//        return Redisson.create(config);
//    }
}
