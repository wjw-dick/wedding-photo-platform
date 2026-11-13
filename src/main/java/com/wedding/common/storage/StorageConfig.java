package com.wedding.common.storage;

import lombok.Data;

@Data
public class StorageConfig {
    private String provider = "LOCAL";
    private String endpoint;
    private String region = "us-east-1";
    private String bucket = "wedding";
    private String accessKey;
    private String secretKey;
    private String domain;
    private boolean pathStyle = true;
}
