package com.wedding.common.storage;

import com.wedding.common.util.AesUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalStorageProvider implements StorageProvider {
    private final String root;
    private final AesUtil aes;

    public LocalStorageProvider(@Value("${wedding.storage.local-root}") String root, AesUtil aes) {
        this.root = root;
        this.aes = aes;
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        try {
            Path path = resolve(objectKey);
            Files.createDirectories(path.getParent());
            Files.copy(in, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储写入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(resolve(objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("文件不存在: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new IllegalStateException("本地文件删除失败", e);
        }
    }

    @Override
    public String signedUrl(String objectKey, int minutes) {
        long expires = System.currentTimeMillis() / 1000 + minutes * 60L;
        String token = aes.encrypt(objectKey + "|" + expires);
        return "/api/open/storage?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    Path resolve(String objectKey) {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path path = base.resolve(objectKey).normalize();
        if (!path.startsWith(base)) {
            throw new IllegalArgumentException("非法对象路径");
        }
        return path;
    }
}
