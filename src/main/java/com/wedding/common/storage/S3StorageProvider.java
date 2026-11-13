package com.wedding.common.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public class S3StorageProvider implements StorageProvider {
    private final StorageConfig cfg;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageProvider(StorageConfig cfg) {
        this.cfg = cfg;
        S3Configuration s3cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(cfg.isPathStyle()).build();
        var builder = S3Client.builder()
                .region(Region.of(cfg.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())))
                .serviceConfiguration(s3cfg);
        var pBuilder = S3Presigner.builder()
                .region(Region.of(cfg.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())))
                .serviceConfiguration(s3cfg);
        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.getEndpoint()));
            pBuilder.endpointOverride(URI.create(cfg.getEndpoint()));
        }
        this.client = builder.build();
        this.presigner = pBuilder.build();
    }

    @Override
    public String provider() {
        return "S3";
    }

    @Override
    public void upload(String objectKey, InputStream in, long size, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(cfg.getBucket()).key(objectKey)
                .contentType(contentType).build(), software.amazon.awssdk.core.sync.RequestBody.fromInputStream(in, size));
    }

    @Override
    public byte[] read(String objectKey) {
        try (ResponseInputStream<GetObjectResponse> res = client.getObject(
                GetObjectRequest.builder().bucket(cfg.getBucket()).key(objectKey).build())) {
            return res.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("S3 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(cfg.getBucket()).key(objectKey).build());
    }

    @Override
    public String signedUrl(String objectKey, int minutes) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(cfg.getBucket()).key(objectKey).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes)).getObjectRequest(get).build())
                .url().toString();
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(cfg.getBucket()).key(objectKey).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
