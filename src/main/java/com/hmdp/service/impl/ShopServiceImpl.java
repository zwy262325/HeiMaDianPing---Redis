package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

         // 逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }


    public Shop queryWithPassThrough(Long id) {
        // 1.查Redis缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2.命中，返回商户信息
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // isNotBlank 判断命中是否是空值
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }

        // 3.未命中，根据id查数据库
        Shop shop = getById(id);

        // 4.未存在，返回404
        if (shop == null) {
            // 将空值写入redis 防止穿透
            stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5.存在，写入Redis，返回商户信息
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    public Shop queryWithMutex(Long id) {
        // 1.查Redis缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2.命中，返回商户信息
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // isNotBlank 判断命中是否是空值 是空值表示是穿透的结果
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }

        // 3. 实现缓存重建
        // 3.1 获取互斥锁,是锁的key,不是缓存的key
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        Shop shop = null;
        try {
            boolean islock = trylock(lockKey);

            // 3.2 判断是否获取成功
            if (!islock) {
                // 3.3 失败，休眠，重试
                Thread.sleep(50);
                // 重试 重新调用该函数
                return queryWithMutex(id);
            }

            // 3.4 成功，根据id查数据库，将数据写入Redis
            shop = getById(id);

            // 模拟数据库查询时间很长的延时
            Thread.sleep(200);

            // 未存在，返回404
            if (shop == null) {
                // 将空值写入redis 防止穿透
                stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 存在，写入Redis，返回商户信息
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 3.5 释放互斥锁
            unlock(lockKey);
        }

        return shop;
    }

    public Shop queryWithLogicalExpire(Long id) {
        // 1.查Redis缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2.未命中，返回商户信息
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 3. 命中
        // 3.1 判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 3.2 未过期，返回商铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 3.3 过期，尝试获取锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = trylock(lockKey);

        // 3.4 获取到锁，开启独立线程，根据ID查数据库，写入Redis，并设置过期时间
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally{
                    // 3.5 释放互斥锁
                    unlock(lockKey);
                }
            });

        }
        // 不管是否获取到锁，最后都要返回店铺信息
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean trylock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag); // 返回的是Boolean对象，自动拆箱会出现NPE情况。
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);

        // 模拟缓存重建
        Thread.sleep(200);

        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(shop);

        // 3.写入Redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
