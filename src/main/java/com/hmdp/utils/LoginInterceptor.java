package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @Author: 小雯
 * @Date: 2026/5/19
 * @Description: 登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 前置校验
        // 1.从Threadlocal中获取用户
        UserDTO userDTO = UserHolder.getUser();

        // 2.用户不存在，拦截
        if (userDTO == null) {
            response.setStatus(401);
            return false;
        }

        // 3.用户存在，放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 后置销毁用户信息，防止内存泄漏。
        UserHolder.removeUser();
    }
}
