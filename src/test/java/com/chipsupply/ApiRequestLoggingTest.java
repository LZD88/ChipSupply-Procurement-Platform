package com.chipsupply;

import com.chipsupply.common.Result;
import com.chipsupply.common.exception.GlobalExceptionHandler;
import com.chipsupply.config.ApiRequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ApiRequestLoggingTest.TestApplication.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApiRequestLoggingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({ApiRequestLoggingFilter.class, TestController.class, GlobalExceptionHandler.class})
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @PostMapping("/api/logging-test")
        Result<Map<String, String>> echo(@RequestBody Map<String, String> body) {
            return Result.success(body);
        }

        @GetMapping("/api/logging-test/failure")
        Result<Void> fail() {
            throw new IllegalStateException("接口日志测试异常");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void logsApiCallWithoutConsumingOrLoggingItsBody(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/logging-test")
                        .header("Authorization", "Bearer request-header-marker-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"芯片交货\",\"actualDeliveryDate\":\"2026-10-03\",\"token\":\"request-body-marker-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.remark").value("芯片交货"))
                .andExpect(jsonPath("$.data.actualDeliveryDate").value("2026-10-03"))
                .andExpect(jsonPath("$.data.token").value("request-body-marker-123"));

        assertThat(output).contains("[请求开始] POST /api/logging-test")
                .contains("[请求结束] POST /api/logging-test HTTP状态=200 耗时=")
                .doesNotContain("request-header-marker-123", "request-body-marker-123");
    }

    @Test
    void preservesExistingErrorResponseAndLogsTheRequest(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/logging-test/failure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        assertThat(output).contains("[请求开始] GET /api/logging-test/failure")
                .contains("系统异常: 接口日志测试异常")
                .contains("[请求结束] GET /api/logging-test/failure HTTP状态=200 耗时=");
    }

    @Test
    void doesNotLogStaticResourceRequests(CapturedOutput output) throws Exception {
        mvc.perform(get("/portal.css"))
                .andExpect(status().isOk());

        assertThat(output).doesNotContain("[请求开始]", "[请求结束]");
    }
}
