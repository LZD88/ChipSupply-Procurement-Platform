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
        return Result.success(Map.of("name", "芯链 · 芯片采购实验室", "version", "2.0.0",
                "dataNotice", "供应商、料号与交易数据均为课堂虚构样本", "agentUrl", agentUrl,
                "categories", Arrays.stream(PartCategoryEnum.values()).map(PartCategoryEnum::getCode).toList()));
    }
}
