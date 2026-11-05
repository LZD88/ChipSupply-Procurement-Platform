package com.chipsupply;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = StaticPageTest.TestApplication.class)
@AutoConfigureMockMvc
class StaticPageTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class TestApplication {
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void rootUsesChipWorkspace() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8).contains("芯链 · 芯片采购协同平台")));
    }

    @Test
    void productPagesAreAvailable() throws Exception {
        mvc.perform(get("/quotes/CS-MCU-01-01.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void buildScriptIsNotPublished() throws Exception {
        mvc.perform(get("/generate-pages.js"))
                .andExpect(status().isNotFound());
    }
}
