package com.chipsupply.controller;

import com.chipsupply.audit.AuditService;
import com.chipsupply.common.Result;
import com.chipsupply.common.enums.ResultCode;
import com.chipsupply.dto.LoginRequest;
import com.chipsupply.dto.LoginResponse;
import com.chipsupply.entity.User;
import com.chipsupply.security.AuthenticatedUser;
import com.chipsupply.security.JwtService;
import com.chipsupply.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest login, HttpServletRequest request) {
        User user = userService.getUserByUsername(login.getUsername());
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(login.getPassword(), user.getPassword())) {
            auditService.recordLogin(login.getUsername(), null, null, "FAILURE", "账号、密码或状态校验失败", request);
            return ResponseEntity.status(401).body(Result.error(ResultCode.UNAUTHORIZED, "用户名或密码错误"));
        }
        String token = jwtService.createToken(user);
        auditService.recordLogin(user.getUsername(), user.getId(), user.getRole(), "SUCCESS", "登录成功", request);
        return ResponseEntity.ok(Result.success(new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(),
                user.getId(), user.getUsername(), user.getRealName(), user.getRole())));
    }

    @GetMapping("/me")
    public Result<AuthenticatedUser> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(user);
    }
}
