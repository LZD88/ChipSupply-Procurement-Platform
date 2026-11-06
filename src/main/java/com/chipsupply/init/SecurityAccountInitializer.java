package com.chipsupply.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chipsupply.entity.User;
import com.chipsupply.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.util.StringUtils;

/** 为本地环境补齐可登录的 RBAC 账号；不会把明文密码写入数据库。 */
@Component
@RequiredArgsConstructor
@Order(20)
public class SecurityAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SecurityAccountInitializer.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${chipsupply.security.bootstrap-password}")
    private String bootstrapPassword;
    @Value("${chipsupply.security.agent-password}")
    private String agentPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapPassword) || !StringUtils.hasText(agentPassword)) {
            throw new IllegalStateException("必须通过本地环境变量配置 CHIP_BOOTSTRAP_PASSWORD 与 CHIP_AGENT_PASSWORD");
        }
        List<AccountSpec> accounts = List.of(
                new AccountSpec("ops_user_1", "系统管理员", "admin", "供应链运营中心", bootstrapPassword, false),
                new AccountSpec("ops_user_2", "采购专员", "purchase", "供应链运营中心", bootstrapPassword, false),
                new AccountSpec("ops_user_3", "仓储专员", "warehouse", "供应链运营中心", bootstrapPassword, false),
                new AccountSpec("ops_auditor", "合规审计员", "auditor", "风险与合规中心", bootstrapPassword, false),
                new AccountSpec("procurement_agent", "采购智能体服务", "agent", "智能采购中台", agentPassword, true)
        );
        accounts.forEach(this::ensureAccount);
        log.info("RBAC 本地账号初始化完成（密码仅以 BCrypt 保存）");
    }

    private void ensureAccount(AccountSpec spec) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, spec.username()));
        if (user == null) {
            user = new User();
            user.setUsername(spec.username());
            user.setPassword(passwordEncoder.encode(spec.password()));
            user.setRealName(spec.realName());
            user.setRole(spec.role());
            user.setDepartment(spec.department());
            user.setStatus(1);
            user.setDeleted(0);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            userMapper.insert(user);
            return;
        }

        boolean disabledSeed = user.getPassword() == null || !user.getPassword().startsWith("$2");
        boolean rotateServicePassword = spec.serviceAccount()
                && !passwordEncoder.matches(spec.password(), user.getPassword());
        if (disabledSeed || rotateServicePassword) {
            user.setPassword(passwordEncoder.encode(spec.password()));
        }
        user.setRealName(spec.realName());
        user.setRole(spec.role());
        user.setDepartment(spec.department());
        user.setStatus(1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private record AccountSpec(String username, String realName, String role, String department,
                               String password, boolean serviceAccount) {
    }
}
