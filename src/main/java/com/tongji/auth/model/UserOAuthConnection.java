package com.tongji.auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOAuthConnection {
    private Long id;
    private Long userId;
    private String provider;
    private String openId;
    private String unionId;
    private String nickname;
    private String avatar;
    private Instant createdAt;
    private Instant updatedAt;
}
