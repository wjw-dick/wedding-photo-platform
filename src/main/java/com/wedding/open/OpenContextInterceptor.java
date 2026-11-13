package com.wedding.open;

import com.wedding.common.context.LoginContext;
import com.wedding.common.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 开放接口不走登录拦截器，但必须清理租户上下文，避免线程复用串租户。 */
@Component
public class OpenContextInterceptor implements HandlerInterceptor {
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
        LoginContext.clear();
    }
}
