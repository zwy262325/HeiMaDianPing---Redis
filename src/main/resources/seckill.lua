-- 1.参数列表
-- 1.1 库存
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]


-- 2.业务Key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1 获取库存，判断是否充足
local stock = tonumber(redis.call('get', stockKey) or 0)
-- 关键修复：stock 不存在 或 stock <=0 都返回1
if (stock <= 0) then
    return 1
end
-- 3.2 判断用户是否下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 已经下单 返回2
    return 2
end
-- 3.3 扣减库存
redis.call('incrby', stockKey, -1)
-- 3.4 将用户id存入当前优惠券集合
redis.call('sadd', orderKey, userId)
-- 3.5 发生消息到Stream队列中
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId);
return 0