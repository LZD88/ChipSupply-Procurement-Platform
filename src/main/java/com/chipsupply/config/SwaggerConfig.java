package com.chipsupply.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 配置类
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("芯链 · 芯片采购实验室 API")
                        .description("芯链 · 芯片采购实验室RESTful API文档")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("芯链教学工作空间")
                                .email("support@chip-lab.example")
                                .url("http://localhost:8082"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
