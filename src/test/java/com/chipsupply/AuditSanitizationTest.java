package com.chipsupply;

import com.chipsupply.audit.AuditService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuditSanitizationTest {
    @Test
    void removesSecretsFromAuditDetails() {
        AuditService service = new AuditService(mock(com.chipsupply.mapper.AuditLogMapper.class));
        String sanitized = service.sanitize("password=hunter2 Authorization=Bearer abc.def api_key=secret-value");
        assertFalse(sanitized.contains("hunter2"));
        assertFalse(sanitized.contains("abc.def"));
        assertFalse(sanitized.contains("secret-value"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }
}
