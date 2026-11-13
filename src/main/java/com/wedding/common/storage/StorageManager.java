package com.wedding.common.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.util.AesUtil;
import com.wedding.common.util.BizNo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 对象存储统一入口：驱动可切、密钥加密、路径带租户前缀、历史文件按原驱动签名。 */
@Service
@RequiredArgsConstructor
public class StorageManager {
    private final JdbcTemplate jdbc;
    private final AesUtil aes;
    private final ObjectMapper objectMapper;
    private final LocalStorageProvider local;
    private final BizNo bizNo;
    private final Map<String, StorageProvider> providers = new ConcurrentHashMap<>();

    @Value("${wedding.storage.default-provider:LOCAL}")
    private String defaultProvider;

    public StorageConfig activeConfig(Long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM storage_config WHERE tenant_id=? AND active_flag=1 LIMIT 1", tenantId);
        if (rows.isEmpty()) {
            StorageConfig cfg = new StorageConfig();
            cfg.setProvider(defaultProvider);
            return cfg;
        }
        return decode(rows.get(0));
    }

    public StorageConfig configOf(Long tenantId, String provider) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM storage_config WHERE tenant_id=? AND provider=? LIMIT 1", tenantId, provider);
        if (rows.isEmpty()) {
            if ("LOCAL".equals(provider)) {
                StorageConfig cfg = new StorageConfig();
                cfg.setProvider("LOCAL");
                return cfg;
            }
            throw new BizException("存储驱动未配置：" + provider);
        }
        return decode(rows.get(0));
    }

    public StorageProvider provider(Long tenantId, String name) {
        StorageConfig cfg = configOf(tenantId, name);
        String hash = String.valueOf(cfg.hashCode());
        return providers.computeIfAbsent(tenantId + ":" + name + ":" + hash, k -> build(cfg));
    }

    public StorageProvider activeProvider(Long tenantId) {
        return provider(tenantId, activeConfig(tenantId).getProvider());
    }

    public StorageProvider build(StorageConfig cfg) {
        return switch (cfg.getProvider()) {
            case "LOCAL" -> local;
            case "S3" -> new S3StorageProvider(cfg);
            case "OSS" -> new OssStorageProvider(cfg);
            case "COS" -> new CosStorageProvider(cfg);
            case "MINIO" -> new MinioStorageProvider(cfg);
            default -> throw new BizException("不支持的存储驱动：" + cfg.getProvider());
        };
    }

    public String objectKey(Long tenantId, String category, String orderNo, String fileName) {
        String ext = "";
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String cat = category == null ? "misc" : category.toLowerCase();
        if (orderNo != null && !orderNo.isBlank() && List.of("raw", "retouched", "print").contains(cat)) {
            return "tenants/" + tenantId + "/orders/" + orderNo + "/" + cat + "/" + uuid + ext;
        }
        return "tenants/" + tenantId + "/" + cat + "/"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "/" + uuid + ext;
    }

    public UploadResult upload(Long tenantId, String category, String orderNo, String fileName,
                               InputStream in, long size, String contentType) {
        StorageConfig cfg = activeConfig(tenantId);
        StorageProvider p = provider(tenantId, cfg.getProvider());
        String key = objectKey(tenantId, category, orderNo, fileName);
        p.upload(key, in, size, contentType);
        jdbc.update("""
                INSERT INTO media_file(tenant_id, order_no, category, file_name, object_key, provider, bucket,
                                       size_bytes, content_type, created_by, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,NOW())
                """, tenantId, orderNo, category, fileName, key, cfg.getProvider(), cfg.getBucket(),
                size, contentType, null);
        return new UploadResult(key, cfg.getProvider(), signedUrl(tenantId, cfg.getProvider(), key, 30));
    }

    public String signedUrl(Long tenantId, String provider, String key, int minutes) {
        int safeMinutes = Math.min(Math.max(minutes, 1), 30);
        return provider(tenantId, provider).signedUrl(key, safeMinutes);
    }

    public void saveConfig(Long tenantId, StorageConfig cfg, boolean activate) {
        try {
            String json = objectMapper.writeValueAsString(cfg);
            String encrypted = aes.encrypt(json);
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM storage_config WHERE tenant_id=? AND provider=?",
                    Integer.class, tenantId, cfg.getProvider());
            if (count == null || count == 0) {
                jdbc.update("INSERT INTO storage_config(tenant_id, provider, config_json, active_flag) VALUES (?,?,?,?)",
                        tenantId, cfg.getProvider(), encrypted, activate ? 1 : 0);
            } else {
                jdbc.update("UPDATE storage_config SET config_json=?, active_flag=? WHERE tenant_id=? AND provider=?",
                        encrypted, activate ? 1 : 0, tenantId, cfg.getProvider());
            }
            if (activate) {
                jdbc.update("UPDATE storage_config SET active_flag=0 WHERE tenant_id=? AND provider<>?",
                        tenantId, cfg.getProvider());
            }
            providers.keySet().removeIf(k -> k.startsWith(tenantId + ":"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "存储配置保存失败");
        }
    }

    public void testConnection(StorageConfig cfg) {
        StorageProvider p = build(cfg);
        String key = "tenants/0/_connect_test/" + UUID.randomUUID() + ".txt";
        byte[] bytes = "wedding-storage-test".getBytes();
        p.upload(key, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
        byte[] read = p.read(key);
        p.delete(key);
        if (read.length != bytes.length) {
            throw new BizException("连接测试失败：回读内容不一致");
        }
    }

    @SuppressWarnings("unchecked")
    private StorageConfig decode(Map<String, Object> row) {
        try {
            String json = aes.decrypt((String) row.get("config_json"));
            StorageConfig cfg = objectMapper.readValue(json, StorageConfig.class);
            if (cfg.getProvider() == null) cfg.setProvider((String) row.get("provider"));
            return cfg;
        } catch (Exception e) {
            throw new BizException(500, "存储配置解析失败");
        }
    }

    public record UploadResult(String objectKey, String provider, String url) {
    }
}
