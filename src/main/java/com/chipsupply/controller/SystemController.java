package com.chipsupply.controller;

import com.chipsupply.common.Result;
import com.chipsupply.common.enums.PartCategoryEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.Map;

@RestController
public class SystemController {
    @Value("${chipsupply.system.agent-url:http://localhost:3000}")
    private String agentUrl;

    @GetMapping("/api/system/info")
    public Result<Map<String, Object>> info() {
        return Result.success(Map.of("name", "芯链 · 芯片采购协同平台", "version", "3.0.0",
                "dataNotice", "预置数据已脱敏，仅用于本地初始化与流程验证", "agentUrl", agentUrl,
                "categories", Arrays.stream(PartCategoryEnum.values()).map(PartCategoryEnum::getCode).toList()));
    }
}
