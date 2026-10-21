package com.hmdp.common;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 为 /mcp 与 /actuator 增加 API Key 鉴权。
 *
 * <p>这两个端点原来既不登录也能访问：MCP 暴露业务工具、Actuator 暴露健康与指标，
 * 公网部署时属于信息泄露面。这里用最简单的共享密钥方式收口：
 * 请求头 {@code X-API-Key} 必须与环境变量 {@code MCP_API_KEY} 一致。
 *
 * <p>未配置 {@code MCP_API_KEY} 时（本地开发常见）放行并打一条 WARN，避免本地调试被打断；
 * 生产环境务必配置。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    @Value("${MCP_API_KEY:}")
    private String expectedKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/mcp") || path.startsWith("/actuator"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (StrUtil.isBlank(expectedKey)) {
            if (!request.getRequestURI().startsWith("/actuator/health")) {
                log.warn("MCP_API_KEY 未配置，{} 处于无鉴权状态（仅建议本地开发）", request.getRequestURI());
            }
            chain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(HEADER);
        if (expectedKey.equals(provided)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("拒绝未授权访问 path={}, remote={}", request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                "{\"success\":false,\"code\":40100,\"errorMsg\":\"未授权：缺少或错误的 X-API-Key\"}"
                        .getBytes(StandardCharsets.UTF_8));
    }
}
