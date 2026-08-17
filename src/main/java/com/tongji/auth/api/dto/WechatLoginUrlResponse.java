package com.tongji.auth.api.dto;

public record WechatLoginUrlResponse(
        boolean enabled,
        String loginUrl,
        String state,
        String message
) {
}
