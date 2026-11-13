package com.wedding.common.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

import java.io.InputStream;
import java.util.Date;

public class CosStorageProvider implements StorageProvider {
    private final StorageConfig cfg;
    private final COSClient client;

    public CosStorageProvider(StorageConfig cfg) {
        this.cfg = cfg;
        COSCredentials cred = new BasicCOSCredentials(cfg.getAccessKey(), cfg.getSecretKey());
        ClientConfig config = new ClientConfig(new Region(cfg.getRegion()));
        this.client = new COSClient(cred, config);
    }

    @Override
    public String provider() {
        return "COS";
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        client.putObject(new PutObjectRequest(cfg.getBucket(), objectKey, in, null));
    }

    @Override
    public byte[] read(String objectKey) {
        try (COSObject obj = client.getObject(cfg.getBucket(), objectKey)) {
            return obj.getObjectContent().readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("COS 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(cfg.getBucket(), objectKey);
    }

    @Override
    public String signedUrl(String objectKey, int minutes) {
        Date expire = new Date(System.currentTimeMillis() + minutes * 60_000L);
        GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(cfg.getBucket(), objectKey);
        req.setExpiration(expire);
        return client.generatePresignedUrl(req).toString();
    }

    @Override
    public boolean exists(String objectKey) {
        return client.doesObjectExist(cfg.getBucket(), objectKey);
    }
}
