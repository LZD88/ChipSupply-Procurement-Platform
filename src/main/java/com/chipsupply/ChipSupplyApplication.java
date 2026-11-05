package com.chipsupply;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 芯链 · 芯片采购协同平台 - 主启动类
 *
 * @author System
 * @version 1.0.0
 */
@SpringBootApplication
@MapperScan("com.chipsupply.mapper")
@EnableTransactionManagement
public class ChipSupplyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChipSupplyApplication.class, args);
        System.out.println("==========================================");
        System.out.println("芯链 · 芯片采购协同平台启动成功！");
        System.out.println("系统访问地址: http://localhost:8082");
        System.out.println("API文档地址: http://localhost:8082/swagger-ui.html");
        System.out.println("==========================================");
    }
}
