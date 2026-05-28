package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * @Author: 小雯
 * @Date: 2026/5/28
 * @Description:
 */

@Component
public class CacheClient {

//    @Resource 不推荐使用Resource方式注入
//    public StringRedisTemplate stringRedisTemplate;

    public final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // - 存储数据：将任意Java对象序列化为json并存储在string类型的key中，并且设置TTL过期时间
    public void save(String key, Object object, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set( key, JSONUtil.toJsonStr(object), time, unit);
    }

    // - 存储数据：将任意Java对象序列化为json并存储在string类型的key中，并且设置逻辑过期时间，用于处理缓存击穿问题。
    public void saveWithLogicalExpire(String key, Object object, Long time, TimeUnit unit){

        // 1.封装成RedisData
        RedisData redisData = new RedisData();
        // 时间转化为秒
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(object);

        // 2.存储到Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // - 查询数据：根据指定key查询缓存，并反序列化为指定类型（参数传递），缓存空值解决缓存穿透。
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        // 1.查Redis缓存
        String key = keyPrefix + id;
        String dataJson = stringRedisTemplate.opsForValue().get(key);

        // 2.命中，返回商户信息
        if (StrUtil.isNotBlank(dataJson)) {
            R r = JSONUtil.toBean(dataJson, type);
            return r;
        }

        // isNotBlank 判断命中是否是空值
        if (dataJson != null) {
            // 返回错误信息
            return null;
        }

        // 3.未命中，根据id查数据库
        R r = dbFallback.apply(id);

        // 4.未存在，返回404
        if (r == null) {
            // 将空值写入redis 防止穿透
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5.存在，写入Redis，返回商户信息
        this.save(key, r, time, unit);

        return r;
    }

    // - 查询数据：根据指定key查询缓存，并反序列化为指定类型（参数传递），利用逻辑过期解决缓存击穿问题。
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        // 1.查Redis缓存
        String key = keyPrefix + id;
        String dataJson = stringRedisTemplate.opsForValue().get(key);

        // 2.未命中，返回商户信息
        if (StrUtil.isBlank(dataJson)) {
            return null;
        }

        // 3. 命中
        // 3.1 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(dataJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 3.2 未过期，返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 3.3 过期，尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = trylock(lockKey);

        // 3.4 获取到锁，开启独立线程，根据ID查数据库，写入Redis，并设置过期时间
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 先查数据库
                    R r1 = dbFallback.apply(id);
                    // 模拟缓存重建
                    Thread.sleep(200);
                    // 重建缓存
                    this.saveWithLogicalExpire(key, r1 ,time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally{
                    // 3.5 释放互斥锁
                    unlock(lockKey);
                }
            });

        }
        // 不管是否获取到锁，最后都要返回店铺信息
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag); // 返回的是Boolean对象，自动拆箱会出现NPE情况。
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
