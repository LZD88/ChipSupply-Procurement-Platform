package com.chipsupply.init;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

/** 初始化独立的芯片供应链业务库。结构和导出 SQL 共用同一来源，不覆盖已有业务数据。 */
@Component
@RequiredArgsConstructor
@Order(10)
public class DatabaseInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    @Value("${chipsupply.init.enabled:true}")
    private boolean enabled;
    @Value("${chipsupply.init.insert-seed-data:true}")
    private boolean seedData;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        ResourceDatabasePopulator schema = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        schema.setSqlScriptEncoding("UTF-8");
        schema.execute(dataSource);
        if (!seedData) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM chip_dataset WHERE version='chip-supply-v3'", Integer.class) > 0) return;
            for (String table : new String[]{"part", "supplier", "purchase_order", "inventory", "user", "customer"}) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class) > 0) {
                    throw new IllegalStateException("目标数据库已含其他数据。请使用空的业务库，或设置 CHIP_SEED_DATA=false。");
                }
            }
            ResourceDatabasePopulator seed = new ResourceDatabasePopulator(new ClassPathResource("db/seed.sql"));
            seed.setSqlScriptEncoding("UTF-8");
            seed.execute(dataSource);
        });
    }
}
