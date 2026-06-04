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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
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
        // TODO 保存阻塞队列

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
    public Result createVoucherOrder(Long voucherId, Long userId) {
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次了！");
        }

        // 4.扣减库存 MyBatis‑Plus 链式更新
        boolean success = iSeckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock", 0).
                update();

        if (!success) {
            return Result.fail("库存不足！");
        }

        // 5.创建订单，"订单id"、"用户id"和"优惠券id"
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 6.返回
        return Result.ok(orderId);
    }
}
