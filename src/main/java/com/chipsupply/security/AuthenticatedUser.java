package com.chipsupply.security;

/** 当前请求中已经通过数据库状态复核的用户身份。 */
public record AuthenticatedUser(Long id, String username, String role, String realName) {
}
