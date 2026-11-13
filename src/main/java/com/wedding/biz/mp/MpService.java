package com.wedding.biz.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedding.common.api.BizException;
import com.wedding.common.context.TenantContext;
import com.wedding.common.util.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MpService {
    private final JdbcTemplate jdbc;
    private final AesUtil aes;
    private final ObjectMapper objectMapper;
    private final WxMpClient wx;
    private final StringRedisTemplate redis;
    private final RestTemplate rest = new RestTemplate();

    @Value("${wedding.mp.callback-base:http://localhost:8080}")
    private String callbackBase;

    public List<Map<String, Object>> accounts() {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM mp_account WHERE tenant_id=? AND deleted=0 ORDER BY id", tid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>(row);
            m.put("app_secret_encrypted", mask());
            m.put("mini_app_secret_encrypted", mask());
            m.put("server_url", callbackBase + "/api/open/mp/" + row.get("app_id"));
            result.add(m);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> saveAccount(Map<String, Object> body) {
        Long tid = TenantContext.requireTenantId();
        String appId = String.valueOf(body.getOrDefault("appId", ""));
        if (appId.isBlank()) throw new BizException("AppID 不能为空");
        String secret = (String) body.get("appSecret");
        String miniSecret = (String) body.get("miniAppSecret");
        List<Map<String, Object>> exists = jdbc.queryForList(
                "SELECT * FROM mp_account WHERE tenant_id=? AND app_id=? AND deleted=0", tid, appId);
        if (exists.isEmpty()) {
            jdbc.update("""
                    INSERT INTO mp_account(tenant_id, account_name, app_id, app_secret_encrypted, server_url, token,
                        encoding_aes_key, oauth_domain, js_domain, mini_app_id, mini_app_secret_encrypted,
                        mini_request_domain, mini_upload_domain, mini_download_domain, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)
                    """, tid, body.getOrDefault("accountName", appId), appId,
                    secret == null || secret.isBlank() ? null : aes.encrypt(secret),
                    callbackBase + "/api/open/mp/" + appId, body.get("token"), body.get("encodingAesKey"),
                    body.get("oauthDomain"), body.get("jsDomain"), body.get("miniAppId"),
                    miniSecret == null || miniSecret.isBlank() ? null : aes.encrypt(miniSecret),
                    body.get("miniRequestDomain"), body.get("miniUploadDomain"), body.get("miniDownloadDomain"));
        } else {
            Long id = ((Number) exists.get(0).get("id")).longValue();
            jdbc.update("""
                    UPDATE mp_account SET account_name=?, server_url=?, token=?, encoding_aes_key=?, oauth_domain=?,
                        js_domain=?, mini_app_id=?, mini_request_domain=?, mini_upload_domain=?, mini_download_domain=?,
                        app_secret_encrypted=COALESCE(?, app_secret_encrypted),
                        mini_app_secret_encrypted=COALESCE(?, mini_app_secret_encrypted)
                    WHERE tenant_id=? AND id=?
                    """, body.getOrDefault("accountName", exists.get(0).get("account_name")),
                    callbackBase + "/api/open/mp/" + appId, body.get("token"), body.get("encodingAesKey"),
                    body.get("oauthDomain"), body.get("jsDomain"), body.get("miniAppId"),
                    body.get("miniRequestDomain"), body.get("miniUploadDomain"), body.get("miniDownloadDomain"),
                    secret == null || secret.isBlank() ? null : aes.encrypt(secret),
                    miniSecret == null || miniSecret.isBlank() ? null : aes.encrypt(miniSecret), tid, id);
        }
        return Map.of("appId", appId, "serverUrl", callbackBase + "/api/open/mp/" + appId);
    }

    public void clearQuota(Long accountId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> acc = wx.account(tid, accountId);
        Set<String> keys = redis.keys("tenant:" + tid + ":mp:*" + acc.get("app_id") + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Transactional
    public Map<String, Object> syncTags(Long accountId) {
        Long tid = TenantContext.requireTenantId();
        JsonNode node = wx.get(tid, accountId, "/cgi-bin/tags/get", Map.of());
        int count = 0;
        for (JsonNode tag : node.path("tags")) {
            jdbc.update("""
                    INSERT INTO mp_tag(tenant_id, account_id, tag_id, tag_name, sync_status)
                    VALUES (?,?,?,?, 'SYNCED')
                    ON DUPLICATE KEY UPDATE tag_id=VALUES(tag_id), sync_status='SYNCED'
                    """, tid, accountId, tag.path("id").asLong(), tag.path("name").asText());
            count++;
        }
        wx.logError(tid, accountId, "syncTags", node);
        return Map.of("synced", count);
    }

    @Transactional
    public Map<String, Object> syncFans(Long accountId) {
        Long tid = TenantContext.requireTenantId();
        JsonNode node = wx.get(tid, accountId, "/cgi-bin/user/get", Map.of());
        int count = 0;
        for (JsonNode openid : node.path("data").path("openid")) {
            JsonNode info = wx.get(tid, accountId, "/cgi-bin/user/info",
                    Map.of("openid", openid.asText(), "lang", "zh_CN"));
            String unionId = info.path("unionid").isMissingNode() ? null : info.path("unionid").asText();
            jdbc.update("""
                    INSERT INTO mp_fan(tenant_id, account_id, open_id, union_id, nickname, avatar,
                                       subscribe_status, subscribe_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar=VALUES(avatar),
                        union_id=VALUES(union_id), subscribe_status=VALUES(subscribe_status)
                    """, tid, accountId, openid.asText(), unionId, info.path("nickname").asText(),
                    info.path("headimgurl").asText(), info.path("subscribe").asInt(1),
                    info.path("subscribe_time").asLong() > 0
                            ? new java.sql.Timestamp(info.path("subscribe_time").asLong() * 1000L).toLocalDateTime() : null);
            if (unionId != null && !unionId.isBlank()) {
                jdbc.update("""
                        INSERT INTO member(tenant_id, union_id, open_id, nickname, avatar, source, status)
                        VALUES (?,?,?,?,?, 'MP', 1)
                        ON DUPLICATE KEY UPDATE nickname=VALUES(nickname), avatar=VALUES(avatar)
                        """, tid, unionId, openid.asText(), info.path("nickname").asText(), info.path("headimgurl").asText());
            }
            count++;
        }
        wx.logError(tid, accountId, "syncFans", node);
        return Map.of("synced", count, "total", node.path("total").asInt(count));
    }

    @Transactional
    public Map<String, Object> syncTemplates(Long accountId) {
        Long tid = TenantContext.requireTenantId();
        JsonNode node = wx.get(tid, accountId, "/cgi-bin/template/get_all_private_template", Map.of());
        int count = 0;
        for (JsonNode t : node.path("template_list")) {
            jdbc.update("""
                    INSERT INTO mp_template(tenant_id, account_id, template_id, title, content, example, industry)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), example=VALUES(example)
                    """, tid, accountId, t.path("template_id").asText(), t.path("title").asText(),
                    t.path("content").asText(), t.path("example").asText(), "");
            count++;
        }
        wx.logError(tid, accountId, "syncTemplates", node);
        return Map.of("synced", count);
    }

    @Transactional
    public Map<String, Object> publishMenu(Long accountId, Long configId) {
        Long tid = TenantContext.requireTenantId();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT * FROM sys_menu_item WHERE tenant_id=? AND config_id=? ORDER BY sort_no, id
                """, tid, configId);
        List<Map<String, Object>> buttons = new ArrayList<>();
        Map<Long, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> btn = new LinkedHashMap<>();
            btn.put("name", item.get("title"));
            String type = String.valueOf(item.get("item_type"));
            String path = String.valueOf(item.get("path"));
            if ("LINK".equals(type)) {
                btn.put("type", "view");
                btn.put("url", path);
            } else if (path != null && path.startsWith("/")) {
                btn.put("type", "miniprogram");
                btn.put("url", callbackBase);
                btn.put("appid", "");
                btn.put("pagepath", path);
            } else {
                btn.put("type", "click");
                btn.put("key", "MENU_" + item.get("id"));
            }
            index.put(((Number) item.get("id")).longValue(), btn);
            Long parentId = ((Number) item.get("parent_id")).longValue();
            if (parentId == 0) {
                btn.put("sub_button", new ArrayList<>());
                buttons.add(btn);
            } else {
                Map<String, Object> parent = index.get(parentId);
                if (parent != null) ((List<Map<String, Object>>) parent.get("sub_button")).add(btn);
                else buttons.add(btn);
            }
        }
        JsonNode node = wx.post(tid, accountId, "/cgi-bin/menu/create", Map.of("button", buttons));
        boolean ok = node != null && node.path("errcode").asInt(-1) == 0;
        jdbc.update("""
                INSERT INTO mp_menu_publish(tenant_id, account_id, config_id, status, err_code, err_msg, published_at)
                VALUES (?,?,?,?,?,?,?)
                """, tid, accountId, configId, ok ? "PUBLISHED" : "FAILED",
                node == null ? null : node.path("errcode").asInt(), node == null ? null : node.path("errmsg").asText(),
                ok ? LocalDateTime.now() : null);
        if (!ok) throw new BizException("菜单发布失败：" + (node == null ? "未知错误" : node.path("errmsg").asText()));
        return Map.of("published", true, "notice", "需重新进入公众号才可见");
    }

    @Transactional
    public Map<String, Object> uploadMaterial(Long accountId, String type, MultipartFile file) {
        Long tid = TenantContext.requireTenantId();
        String token = wx.accessToken(tid, accountId);
        try {
            byte[] content = file.getBytes();
            com.wedding.common.util.FileTypeValidator.requireAllowed(
                    file.getOriginalFilename(), file.getContentType(), new java.io.ByteArrayInputStream(content));
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            ByteArrayResource resource = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            form.add("media", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<String> resp = rest.postForEntity(
                    "https://api.weixin.qq.com/cgi-bin/media/upload?access_token=" + token + "&type=" + type.toLowerCase(),
                    new HttpEntity<>(form, headers), String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            if (node.path("errcode").asInt(0) != 0) throw new BizException("素材上传失败：" + node.path("errmsg").asText());
            String mediaId = node.path("media_id").asText();
            jdbc.update("""
                    INSERT INTO mp_material(tenant_id, account_id, media_type, media_id, file_name, size_bytes, thumb_media_id)
                    VALUES (?,?,?,?,?,?,?)
                    """, tid, accountId, type.toUpperCase(), mediaId, file.getOriginalFilename(),
                    file.getSize(), node.path("thumb_media_id").asText(null));
            return Map.of("mediaId", mediaId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("素材上传失败：" + e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> publishDraft(Long draftId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> draft = jdbc.queryForMap("SELECT * FROM mp_draft WHERE tenant_id=? AND id=?", tid, draftId);
        Object mediaId = draft.get("media_id");
        if (mediaId == null || String.valueOf(mediaId).isBlank()) {
            throw new BizException("草稿尚未提交到微信，请先保存草稿");
        }
        JsonNode node = wx.post(tid, Long.valueOf(String.valueOf(draft.get("account_id"))),
                "/cgi-bin/freepublish/submit", Map.of("media_id", mediaId));
        boolean ok = node != null && node.path("errcode").asInt(-1) == 0;
        jdbc.update("""
                INSERT INTO mp_publish(tenant_id, account_id, draft_id, publish_id, status, fail_reason, published_at)
                VALUES (?,?,?,?,?,?,?)
                """, tid, draft.get("account_id"), draftId,
                node == null ? null : node.path("publish_id").asText(),
                ok ? "PUBLISHED" : "FAILED",
                node == null ? null : node.path("errmsg").asText(),
                ok ? LocalDateTime.now() : null);
        jdbc.update("UPDATE mp_draft SET status=? WHERE tenant_id=? AND id=?",
                ok ? "SUBMITTED" : "FAILED", tid, draftId);
        if (!ok) throw new BizException("发表失败：" + (node == null ? "未知错误" : node.path("errmsg").asText()));
        return Map.of("published", true, "publishId", node.path("publish_id").asText());
    }

    @Transactional
    public Map<String, Object> saveDraftToWx(Long draftId) {
        Long tid = TenantContext.requireTenantId();
        Map<String, Object> draft = jdbc.queryForMap("SELECT * FROM mp_draft WHERE tenant_id=? AND id=?", tid, draftId);
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", draft.get("title"));
        article.put("author", draft.get("author"));
        article.put("digest", draft.get("digest"));
        article.put("content", draft.get("content"));
        article.put("content_source_url", draft.get("source_url"));
        article.put("thumb_media_id", draft.get("cover_url"));
        article.put("need_open_comment", 0);
        article.put("only_fans_can_comment", 0);
        JsonNode node = wx.post(tid, Long.valueOf(String.valueOf(draft.get("account_id"))),
                "/cgi-bin/draft/add", Map.of("articles", List.of(article)));
        if (node == null || node.path("media_id").isMissingNode()) {
            throw new BizException("草稿提交失败：" + (node == null ? "未知错误" : node.path("errmsg").asText()));
        }
        jdbc.update("UPDATE mp_draft SET media_id=?, status='DRAFT' WHERE tenant_id=? AND id=?",
                node.path("media_id").asText(), tid, draftId);
        return Map.of("mediaId", node.path("media_id").asText());
    }

    private String mask() {
        return "******";
    }
}
