package com.tongji.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.tongji.auth.model.IdentifierType;

/**
 * 注册请求。
 * <p>
 * 字段：账号类型与值、密码、是否同意服务条款。
 */
public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,
        String password,
        boolean agreeTerms
) {
}
