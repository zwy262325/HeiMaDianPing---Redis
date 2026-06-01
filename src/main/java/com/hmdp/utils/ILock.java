package com.hmdp.utils;

/**
 * @Author: 小雯
 * @Date: 2026/6/1
 * @Description:
 */
public interface ILock {
    /**
     * 获取锁
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
