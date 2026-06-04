package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 小雯
 * @Date: 2026/6/1
 * @Description:
 */
public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;


    /**
     * 锁的名称，需用户传递
     */
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 锁的前缀
     */
    private static final String KEY_PREFIX = "lock:";

    /**
     * 锁标识前缀
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * RedisScript类，用于读取lua文件。使用静态代码块，类加载时就加载。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获得线程表示
        long threadId = Thread.currentThread().getId();
        // 获得锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        // 获取锁的标识
//        String threadId= stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 获取当前线程的锁标识
//        String lockId = ID_PREFIX + Thread.currentThread().getId();
//        if (threadId.equals(lockId)){
//            // 一致，则释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
