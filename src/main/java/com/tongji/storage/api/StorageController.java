package com.tongji.storage.api;

import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import com.tongji.auth.token.JwtService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.storage.OssStorageService;
import com.tongji.storage.api.dto.StoragePresignRequest;
import com.tongji.storage.api.dto.StoragePresignResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storage")
@Validated
@RequiredArgsConstructor
public class StorageController {

    private final OssStorageService ossStorageService;
    private final JwtService jwtService;
    private final KnowPostMapper knowPostMapper;
    private final StringRedisTemplate redis;
    private static final String LOCAL_UPLOAD_TOKEN_PREFIX = "storage:local-upload:";
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /**
     * 获取用于直传的 PUT 预签名 URL。
     */
    @PostMapping("/presign")
    public StoragePresignResponse presign(@Valid @RequestBody StoragePresignRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);

        long postId;
        try {
            postId = Long.parseLong(request.postId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "postId 非法");
        }

        // 权限校验：postId 必须属于当前用户
        KnowPost post = knowPostMapper.findById(postId);
        if (post == null || post.getCreatorId() == null || post.getCreatorId() != userId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }

        String scene = request.scene();
        String objectKey;
        String ext = normalizeExt(request.ext(), request.contentType(), scene);

        if ("knowpost_content".equals(scene)) {
            objectKey = "posts/" + postId + "/content" + ext;
        } else if ("knowpost_image".equals(scene)) {
            if (!isAllowedImageContentType(request.contentType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPG、PNG、WEBP 图片");
            }
            String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now());
            String rand = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
            objectKey = "posts/" + postId + "/images/" + date + "/" + rand + ext;
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的上传场景");
        }

        int expiresIn = 600; // 10 分钟
        String putUrl;
        String publicUrl = ossStorageService.publicUrl(objectKey);
        if (ossStorageService.isLocalMode()) {
            String token = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set(LOCAL_UPLOAD_TOKEN_PREFIX + token, scene + "|" + request.contentType() + "|" + objectKey, Duration.ofSeconds(expiresIn));
            putUrl = "/api/v1/storage/local?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        } else {
            putUrl = ossStorageService.generatePresignedPutUrl(objectKey, request.contentType(), expiresIn);
        }
        Map<String, String> headers = Map.of("Content-Type", request.contentType());
        return new StoragePresignResponse(objectKey, putUrl, publicUrl, headers, expiresIn);
    }

    @PutMapping("/local")
    public ResponseEntity<Void> putLocal(@RequestParam("token") String token,
                                         @RequestBody byte[] body) {
        String key = LOCAL_UPLOAD_TOKEN_PREFIX + token;
        String stored = redis.opsForValue().get(key);
        if (stored == null || stored.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传地址已过期，请重新选择文件");
        }
        redis.delete(key);
        String[] parts = stored.split("\\|", 3);
        String scene = parts.length == 3 ? parts[0] : "";
        String contentType = parts.length == 3 ? parts[1] : "";
        String objectKey = parts.length == 3 ? parts[2] : stored;
        if ("knowpost_image".equals(scene)) {
            if (!isAllowedImageContentType(contentType)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 JPG、PNG、WEBP 图片");
            }
            if (body != null && body.length > MAX_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "单张图片不能超过5MB");
            }
        }
        String etag = ossStorageService.putObject(objectKey, body);
        return ResponseEntity.noContent()
                .header(HttpHeaders.ETAG, "\"" + etag + "\"")
                .build();
    }

    private String normalizeExt(String ext, String contentType, String scene) {
        if (ext != null && !ext.isBlank()) {
            return ext.startsWith(".") ? ext : "." + ext;
        }
        if ("knowpost_content".equals(scene)) {
            return switch (contentType) {
                case "text/markdown" -> ".md";
                case "text/html" -> ".html";
                case "text/plain" -> ".txt";
                case "application/json" -> ".json";
                default -> ".bin";
            };
        } else {
            return switch (contentType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".img";
            };
        }
    }

    private boolean isAllowedImageContentType(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
    }
}
