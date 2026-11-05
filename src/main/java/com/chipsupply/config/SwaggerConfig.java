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
                        .title("芯链 · 芯片采购协同平台 API")
                        .description("面向芯片供应链采购、库存与履约协同的 RESTful API")
                        .version("3.0.0")
                        .contact(new Contact()
                                .name("芯链供应链平台")
                                .email("support@chipsupply.example")
                                .url("http://localhost:8082"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
