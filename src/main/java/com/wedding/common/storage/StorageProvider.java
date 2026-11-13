package com.wedding.common.storage;

import java.io.InputStream;

public interface StorageProvider {
    String provider();

    void upload(String objectKey, InputStream in, long size, String contentType);

    byte[] read(String objectKey);

    void delete(String objectKey);

    /** 私有桶签名 URL，有效期分钟数。 */
    String signedUrl(String objectKey, int minutes);

    boolean exists(String objectKey);
}
