package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    public ShopServiceImpl shopService;

    @Resource
    public CacheClient cacheClient;

    @Resource
    public RedisIdWorker redisIdWorker;

    public static ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWork() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        // 每个任务的工作
        Runnable task = () -> {
            for(int i = 0; i < 100; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id:" + id);
            }
            countDownLatch.countDown();
        };

        long startTime = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            es.submit(task);
        }
        countDownLatch.await();
        // endTime 是一个 long 类型的数字 = 当前时间的【毫秒级时间戳】
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - startTime));

    }

    @Test
    void testSaveShop() throws InterruptedException{
        Shop shop = shopService.getById(1L);
        cacheClient.saveWithLogicalExpire(CACHE_SHOP_KEY + 1, shop, 10L, TimeUnit.SECONDS);
    }

}
