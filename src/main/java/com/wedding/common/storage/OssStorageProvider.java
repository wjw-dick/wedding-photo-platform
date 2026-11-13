package com.wedding.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;

import java.io.InputStream;
import java.util.Date;

public class OssStorageProvider implements StorageProvider {
    private final StorageConfig cfg;
    private final OSS client;

    public OssStorageProvider(StorageConfig cfg) {
        this.cfg = cfg;
        this.client = new OSSClientBuilder().build(cfg.getEndpoint(), cfg.getAccessKey(), cfg.getSecretKey());
    }

    @Override
    public String provider() {
        return "OSS";
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        client.putObject(cfg.getBucket(), objectKey, in);
    }

    @Override
    public byte[] read(String objectKey) {
        try (OSSObject obj = client.getObject(cfg.getBucket(), objectKey)) {
            return obj.getObjectContent().readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("OSS 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(cfg.getBucket(), objectKey);
    }

    @Override
    public String signedUrl(String objectKey, int minutes) {
        Date expire = new Date(System.currentTimeMillis() + minutes * 60_000L);
        return client.generatePresignedUrl(cfg.getBucket(), objectKey, expire).toString();
    }

    @Override
    public boolean exists(String objectKey) {
        return client.doesObjectExist(cfg.getBucket(), objectKey);
    }
}
