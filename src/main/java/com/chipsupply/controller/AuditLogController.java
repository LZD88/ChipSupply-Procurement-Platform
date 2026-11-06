package com.chipsupply.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chipsupply.common.PageResult;
import com.chipsupply.common.Result;
import com.chipsupply.entity.AuditLog;
import com.chipsupply.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogMapper auditLogMapper;

    @GetMapping("/page")
    public Result<PageResult<AuditLog>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String actor,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(required = false) String result) {
        long safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<AuditLog> query = new LambdaQueryWrapper<>();
        query.like(StringUtils.hasText(actor), AuditLog::getActorUsername, actor)
                .eq(StringUtils.hasText(action), AuditLog::getAction, action)
                .eq(StringUtils.hasText(result), AuditLog::getResult, result)
                .orderByDesc(AuditLog::getCreatedAt);
        return Result.success(PageResult.from(auditLogMapper.selectPage(new Page<>(current, safeSize), query)));
    }
}
