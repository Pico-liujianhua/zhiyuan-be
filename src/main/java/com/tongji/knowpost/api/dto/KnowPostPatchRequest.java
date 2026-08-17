package com.tongji.knowpost.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 帖子元数据更新请求（部分字段可选）。
 */
public record KnowPostPatchRequest(
        @Size(max = 120, message = "标题不能超过120字") String title,
        Long tagId,
        @Size(max = 20) List<String> tags,
        @Size(max = 9, message = "图片最多9张") List<String> imgUrls,
        String visible,
        Boolean isTop,
        @Size(max = 50, message = "摘要不能超过50字") String description
) {}
