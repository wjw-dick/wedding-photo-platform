package com.wedding.biz.mp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/** 微信接入层：access_token 租户维度缓存、错误码原样落库。 */
@Service
@RequiredArgsConstructor
public class WxMpClient {
    private static final String BASE = "https://api.weixin.qq.com";
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final AesUtil aes;
    private final ObjectMapper objectMapper;
    private final RestClient rest = RestClient.create();

    public Map<String, Object> account(Long tenantId, Long accountId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM mp_account WHERE tenant_id=? AND id=? AND deleted=0", tenantId, accountId);
        return row;
    }

    public String accessToken(Long tenantId, Long accountId) {
        Map<String, Object> account = account(tenantId, accountId);
        String appId = String.valueOf(account.get("app_id"));
        String key = "tenant:" + tenantId + ":mp:token:" + appId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return cached;
        String secretEnc = (String) account.get("app_secret_encrypted");
        if (secretEnc == null || secretEnc.isBlank()) throw new BizException("请先完善公众号 AppSecret");
        String secret = aes.decrypt(secretEnc);
        try {
            JsonNode node = rest.get()
                    .uri(BASE + "/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}", appId, secret)
                    .retrieve().body(JsonNode.class);
            if (node == null || node.path("access_token").isMissingNode()) {
                throw new BizException("获取 access_token 失败：" + (node == null ? "" : node.toString()));
            }
            String token = node.get("access_token").asText();
            redis.opsForValue().set(key, token, Duration.ofSeconds(Math.max(60, node.path("expires_in").asInt(7200) - 300)));
            return token;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信接口不可达：" + e.getMessage());
        }
    }

    public JsonNode get(Long tenantId, Long accountId, String path, Map<String, ?> params) {
        String token = accessToken(tenantId, accountId);
        try {
            return rest.get().uri(BASE + path + "?access_token=" + token, params).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new BizException("微信接口调用失败：" + e.getMessage());
        }
    }

    public JsonNode post(Long tenantId, Long accountId, String path, Object body) {
        String token = accessToken(tenantId, accountId);
        try {
            return rest.post().uri(BASE + path + "?access_token=" + token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new BizException("微信接口调用失败：" + e.getMessage());
        }
    }

    public void logError(Long tenantId, Long accountId, String scene, JsonNode node) {
        if (node == null) return;
        int errCode = node.path("errcode").asInt(0);
        if (errCode == 0) return;
        jdbc.update("""
                INSERT INTO mp_message(tenant_id, account_id, direction, msg_type, content, err_code, err_msg)
                VALUES (?,?, 'OUT', 'API_ERROR', ?,?,?)
                """, tenantId, accountId, scene, errCode, node.path("errmsg").asText());
    }
}
