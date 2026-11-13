package com.wedding.common.storage;

import io.minio.*;
import io.minio.http.Method;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class MinioStorageProvider implements StorageProvider {
    private final StorageConfig cfg;
    private final MinioClient client;

    public MinioStorageProvider(StorageConfig cfg) {
        this.cfg = cfg;
        this.client = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .build();
    }

    @Override
    public String provider() {
        return "MINIO";
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(cfg.getBucket()).object(objectKey)
                    .stream(in, size, -1).contentType(contentType).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try (GetObjectResponse res = client.getObject(
                GetObjectArgs.builder().bucket(cfg.getBucket()).object(objectKey).build())) {
            return res.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(cfg.getBucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String signedUrl(String objectKey, int minutes) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(cfg.getBucket()).object(objectKey)
                    .expiry(minutes, TimeUnit.MINUTES).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 签名失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(cfg.getBucket()).object(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
