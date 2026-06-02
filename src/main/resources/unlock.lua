if(redis.call("GET", KEYS[1]) == ARGV[1]) then
    -- 如果相等，删除锁（删除key）
    return redis.call("DEL", KEYS[1])
end
return 0