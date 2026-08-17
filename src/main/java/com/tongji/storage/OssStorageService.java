package com.tongji.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.tongji.storage.config.OssProperties;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.net.URL;
import java.util.Date;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class OssStorageService {

    private final OssProperties props;

    public String uploadAvatar(long userId, MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String objectKey = props.getFolder() + "/" + userId + "-" + Instant.now().toEpochMilli() + ext;
        if (isLocalMode()) {
            try {
                putObject(objectKey, file.getBytes());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
            }
            return publicUrl(objectKey);
        }

        ensureConfigured();

        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());

        try {
            PutObjectRequest request = new PutObjectRequest(props.getBucket(), objectKey, file.getInputStream());
            client.putObject(request);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        } finally {
            client.shutdown();
        }

        return publicUrl(objectKey);
    }

    public String publicUrl(String objectKey) {
        if (isLocalMode()) {
            return props.getLocalPublicBaseUrl().replaceAll("/$", "") + "/" + sanitizeObjectKey(objectKey);
        }
        if (props.getPublicDomain() != null && !props.getPublicDomain().isBlank()) {
            return props.getPublicDomain().replaceAll("/$", "") + "/" + objectKey;
        }
        return "https://" + props.getBucket() + "." + props.getEndpoint() + "/" + objectKey;
    }

    /**
     * 生成用于直传的 PUT 预签名 URL。
     * 客户端必须在上传时设置与签名一致的 Content-Type。
     *
     * @param objectKey 目标对象键
     * @param contentType 上传内容类型（如 text/markdown, image/png）
     * @param expiresInSeconds 有效期秒数（建议 300-900）
     * @return 可直接用于 PUT 上传的预签名 URL
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expiresInSeconds) {
        if (isLocalMode()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "本地存储不支持 OSS 预签名地址");
        }
        ensureConfigured();
        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            Date expiration = new Date(System.currentTimeMillis() + expiresInSeconds * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(props.getBucket(), objectKey, HttpMethod.PUT);
            request.setExpiration(expiration);
            if (contentType != null && !contentType.isBlank()) {
                request.setContentType(contentType);
            }
            URL url = client.generatePresignedUrl(request);
            return url.toString();
        } finally {
            client.shutdown();
        }
    }

    public String putObject(String objectKey, byte[] bytes) {
        String safeKey = sanitizeObjectKey(objectKey);
        Path root = Path.of(props.getLocalRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(safeKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径非法");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return md5Hex(bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    public boolean isLocalMode() {
        return props.getMode() == null || "local".equalsIgnoreCase(props.getMode());
    }

    private String sanitizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径不能为空");
        }
        String normalized = objectKey.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("/..") || normalized.equals("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件路径非法");
        }
        return normalized;
    }

    private String md5Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件校验失败");
        }
    }

    private void ensureConfigured() {
        if (props.getEndpoint() == null || props.getAccessKeyId() == null || props.getAccessKeySecret() == null || props.getBucket() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象存储未配置");
        }
    }
}
