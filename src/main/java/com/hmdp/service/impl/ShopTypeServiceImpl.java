package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String typeKey = RedisConstants.CACHE_TYPE_KEY;
        // 1.查询Redis缓存
        String typeJson = stringRedisTemplate.opsForValue().get(typeKey);

        // 2.命中，返回
        if (StrUtil.isNotBlank(typeJson)) {
            return Result.ok(JSONUtil.toList(typeJson, ShopType.class));
        }
        // 3.未命中，查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();

        // 4.将数据库数据存储到redis中
        stringRedisTemplate.opsForValue().set(typeKey, JSONUtil.toJsonStr(shopTypeList));

        // 5.存在，返回
       return Result.ok(shopTypeList);
    }
}
