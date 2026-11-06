package com.chipsupply.audit;

import com.chipsupply.entity.AuditLog;
import com.chipsupply.mapper.AuditLogMapper;
import com.chipsupply.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, String resourceId, String result,
                       String detail, HttpServletRequest request) {
        try {
            AuditLog entry = baseEntry(request);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
                entry.setActorId(user.id());
                entry.setActorUsername(user.username());
                entry.setActorRole(user.role());
            }
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setResult(result);
            entry.setDetailJson(sanitize(detail));
            auditLogMapper.insert(entry);
        } catch (Exception ex) {
            // 审计失败不能遮蔽原始业务结果，但必须进入服务日志供告警处理。
            log.error("写入审计日志失败 action={} resourceType={}", action, resourceType, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(String username, Long userId, String role, String result,
                            String detail, HttpServletRequest request) {
        try {
            AuditLog entry = baseEntry(request);
            entry.setActorId(userId);
            entry.setActorUsername(limit(username, 50));
            entry.setActorRole(limit(role, 50));
            entry.setAction("AUTH_LOGIN");
            entry.setResourceType("AUTHENTICATION");
            entry.setResourceId(limit(username, 100));
            entry.setResult(result);
            entry.setDetailJson(sanitize(detail));
            auditLogMapper.insert(entry);
        } catch (Exception ex) {
            log.error("写入登录审计失败", ex);
        }
    }

    private AuditLog baseEntry(HttpServletRequest request) {
        AuditLog entry = new AuditLog();
        entry.setHttpMethod(request.getMethod());
        entry.setRequestPath(limit(request.getRequestURI(), 500));
        String requestId = request.getHeader("X-Request-Id");
        entry.setRequestId(StringUtils.hasText(requestId) ? limit(requestId, 64) : UUID.randomUUID().toString());
        entry.setIpAddress(limit(clientIp(request), 64));
        entry.setUserAgent(limit(request.getHeader("User-Agent"), 500));
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /** 审计摘要禁止持久化密码、Token、Authorization 与 API Key。 */
    public String sanitize(String value) {
        if (!StringUtils.hasText(value)) return null;
        String safe = value
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer-[REDACTED]")
                .replaceAll("(?i)(password|passwd|pwd|token|authorization|api[_-]?key)\\s*[=:]\\s*[^,;\\s}]+", "$1=[REDACTED]");
        return limit(safe, 4000);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
