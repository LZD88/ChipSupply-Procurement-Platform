package com.chipsupply.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import com.chipsupply.common.Result;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint point, Auditable auditable) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String resourceId = resolveResourceId(point);
        try {
            Object result = point.proceed();
            boolean success = !(result instanceof Result<?> apiResult) || Integer.valueOf(200).equals(apiResult.getCode());
            auditService.record(auditable.action(), auditable.resourceType(), resourceId,
                    success ? "SUCCESS" : "FAILURE",
                    success ? "业务操作成功" : "业务拒绝: " + ((Result<?>) result).getMessage(), request);
            return result;
        } catch (Throwable throwable) {
            auditService.record(auditable.action(), auditable.resourceType(), resourceId,
                    "FAILURE", throwable.getClass().getSimpleName() + ": " + throwable.getMessage(), request);
            throw throwable;
        }
    }

    private String resolveResourceId(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = point.getArgs();
        if (names == null) return null;
        for (int i = 0; i < names.length; i++) {
            if (Arrays.asList("id", "orderId", "partId", "supplierId").contains(names[i])) {
                return args[i] == null ? null : String.valueOf(args[i]);
            }
        }
        return null;
    }
}
