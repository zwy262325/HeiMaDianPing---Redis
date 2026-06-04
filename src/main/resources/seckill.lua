-- 1.参数列表
-- 1.1 库存
local voucherId = ARGV[1]
local userId = ARGV[2]


-- 2.业务Key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足 返回1
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
return 0