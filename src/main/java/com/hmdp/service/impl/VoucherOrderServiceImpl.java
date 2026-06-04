package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "getRedissonClient")
    private RedissonClient redissonClient;

    /**
     * RedisScript类，用于读取lua文件。使用静态代码块，类加载时就加载。
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 阻塞队列存储下单信息，因为是类的成员变量，不会在每个线程来的时候创建一个新的阻塞队列。
     */
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    /**
     * 创建线程池，线程任务，用于实现下单。单个核心线程，按照顺序执行。
     */
    private ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * 创建内部类，执行线程任务
     */
    private class VoucherOrderHandler implements Runnable{


        @Override
        public void run() {
            while (true){
                // 1.获得队列中的信息
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    // 异常，记录日志
                    log.error("处理订单异常", e);
                }
            }

        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1.使用Redisson的可重入锁
        RLock lock = redissonClient.getLock("order" + voucherOrder.getUserId());
        // 2.获取锁
        boolean isLock = lock.tryLock();
        // 3.获取锁失败，非阻塞式，异步下单，记录日志，不需要返回
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        // 4.创建订单
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 5.释放锁
              lock.unlock();
        }
    }

    /**
     * 在当前类初始化完以后，执行该方法，初始化内部类。
     */
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 定义代理对象
     */
    private IVoucherOrderService proxy;

    /**
     * 使用lua脚本实现库存判断，一人一单
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());

        int r = result.intValue();
        // 2.是否为0
        // 2.1 不为0，没有购买资格
        if(r != 0){
            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
        }
        // 2.2 为0，有购买资格，把下单信息，保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        // 2.3 创建订单，放到阻塞队列中，"订单id"、"用户id"和"优惠券id"
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);
        // 主线程获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.返回订单id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询订单
//        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
//
//        // 2.判断是否在秒杀范围内
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//
//        // 3.判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//        // 一人一单
//        Long userId = UserHolder.getUser().getId();
//
////      // 自己创建锁对象
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
////
////        boolean isLock = simpleRedisLock.tryLock(1200);
//
//        // 使用Redisson的可重入锁
//        RLock lock = redissonClient.getLock("order" + userId);
//        boolean isLock = lock.tryLock();
//
//        // 获取锁失败，非阻塞式，直接返回错误信息
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            // 获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId, userId);
//        } finally {
//            // 释放锁
////            simpleRedisLock.unlock();
//              lock.unlock();
//        }
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        int count = query().eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        if (count > 0) {
            log.error("不允许重复下单");
        }

        // 扣减库存 MyBatis‑Plus 链式更新
        boolean success = iSeckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).
                update();

        if (!success) {
            log.error("库存不足");
        }

        // 创建订单，"订单id"、"用户id"和"优惠券id"
        save(voucherOrder);
    }
}
