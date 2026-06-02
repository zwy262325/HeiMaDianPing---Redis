package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class RedissonTest {

    @Resource(name = "getRedissonClient8080")
    private RedissonClient redissonClient8080;

    @Resource(name = "getRedissonClient8081")
    private RedissonClient redissonClient8081;

    @Resource(name = "getRedissonClient8082")
    private RedissonClient redissonClient8082;

    private RLock lock;

    @BeforeEach
    void setUp() {
        // 单独测试每个客户端是否正常工作
        System.out.println("8080 连通性：" + redissonClient8080.getLock("test").isLocked());
        System.out.println("8081 连通性：" + redissonClient8081.getLock("test").isLocked());
        System.out.println("8082 连通性：" + redissonClient8082.getLock("test").isLocked());

        RLock lock1 = redissonClient8080.getLock("order");
        RLock lock2 = redissonClient8081.getLock("order");
        RLock lock3 = redissonClient8082.getLock("order");
        lock = redissonClient8080.getMultiLock(lock1, lock2, lock3);
    }

    @Test
    void method1() throws InterruptedException {
        // 尝试获取锁
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败 .... 1");
            return;
        }
        try {
            log.info("获取锁成功 .... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 .... 1");
            lock.unlock();
        }
    }
    void method2() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败 .... 2");
            return;
        }
        try {
            log.info("获取锁成功 .... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 .... 2");
            lock.unlock();
        }
    }
}
