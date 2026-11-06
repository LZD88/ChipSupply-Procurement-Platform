package com.chipsupply.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.chipsupply.tracing.TraceContext;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** 记录业务接口调用，不读取请求体或响应体。 */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI().replace('\r', '_').replace('\n', '_');
        String traceId = TraceContext.start(request.getHeader("X-Trace-Id"));
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);
        long start = System.nanoTime();
        log.info("[请求开始] {} {} traceId={}", method, path, traceId);
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            log.error("[请求异常] {} {} traceId={} 耗时={}ms", method, path, traceId, elapsedMillis(start), ex);
            throw ex;
        } finally {
            log.info("[请求结束] {} {} HTTP状态={} 耗时={}ms traceId={}",
                    method, path, response.getStatus(), elapsedMillis(start), traceId);
            MDC.remove("traceId");
            TraceContext.clear();
        }
    }

    private static long elapsedMillis(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
