package com.wedding.open;

import com.wedding.common.storage.StorageManager;
import com.wedding.common.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** 本地存储签名访问；云存储直接返回云端签名 URL。 */
@RestController
@RequestMapping("/api/open/storage")
@RequiredArgsConstructor
public class StorageFileController {
    private final AesUtil aes;
    private final StorageManager storageManager;

    @GetMapping
    public ResponseEntity<byte[]> get(@RequestParam String token) {
        String decoded;
        try {
            decoded = aes.decrypt(token);
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }
        String[] parts = decoded.split("\\|", 2);
        if (parts.length != 2) return ResponseEntity.status(403).build();
        String key = parts[0];
        long expires = Long.parseLong(parts[1]);
        if (System.currentTimeMillis() / 1000 > expires) return ResponseEntity.status(410).build();
        if (!key.startsWith("tenants/")) return ResponseEntity.status(403).build();
        long tenantId = Long.parseLong(key.split("/")[1]);
        try {
            byte[] bytes = storageManager.provider(tenantId, "LOCAL").read(key);
            String contentType = key.endsWith(".png") ? "image/png"
                    : key.endsWith(".jpg") || key.endsWith(".jpeg") ? "image/jpeg"
                    : key.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .header("Content-Disposition", "inline")
                    .header("X-Robots-Tag", "noindex")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
