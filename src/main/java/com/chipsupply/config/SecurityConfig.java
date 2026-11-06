package com.chipsupply.config;

import com.chipsupply.common.Result;
import com.chipsupply.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/portal.js", "/portal.css", "/chip.svg",
                                "/quotes/**", "/favicon.ico", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/orders/create").hasAnyRole("ADMIN", "PURCHASE", "AGENT")
                        .requestMatchers(HttpMethod.PUT, "/api/orders/update/**").hasAnyRole("ADMIN", "PURCHASE", "AGENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/update-status/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.DELETE, "/api/orders/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.PATCH, "/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.DELETE, "/api/inventory/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/api/parts/**", "/api/suppliers/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.PUT, "/api/parts/**", "/api/suppliers/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.PATCH, "/api/parts/**", "/api/suppliers/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.DELETE, "/api/parts/**", "/api/suppliers/**").hasAnyRole("ADMIN", "PURCHASE")
                        .requestMatchers(HttpMethod.POST, "/api/logistics/**").hasAnyRole("ADMIN", "PURCHASE", "WAREHOUSE")
                        .requestMatchers(HttpMethod.PUT, "/api/logistics/**").hasAnyRole("ADMIN", "PURCHASE", "WAREHOUSE")
                        .requestMatchers(HttpMethod.PATCH, "/api/logistics/**").hasAnyRole("ADMIN", "PURCHASE", "WAREHOUSE")
                        .requestMatchers(HttpMethod.DELETE, "/api/logistics/**").hasAnyRole("ADMIN", "PURCHASE", "WAREHOUSE")
                        .requestMatchers(HttpMethod.POST, "/api/customers/**").hasAnyRole("ADMIN", "SALES")
                        .requestMatchers(HttpMethod.PUT, "/api/customers/**").hasAnyRole("ADMIN", "SALES")
                        .requestMatchers(HttpMethod.PATCH, "/api/customers/**").hasAnyRole("ADMIN", "SALES")
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasAnyRole("ADMIN", "SALES")
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized()))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, Result<?> result) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
