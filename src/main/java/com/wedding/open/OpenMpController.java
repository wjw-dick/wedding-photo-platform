package com.wedding.open;

import com.wedding.biz.mp.WxCrypto;
import com.wedding.common.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 公众号回调：验签、消息解密、事件落库、自动回复。 */
@Slf4j
@RestController
@RequestMapping("/api/open/mp")
@RequiredArgsConstructor
public class OpenMpController {
    private final JdbcTemplate jdbc;

    @GetMapping("/{appId}")
    public ResponseEntity<String> verify(@PathVariable String appId,
                                         @RequestParam String signature,
                                         @RequestParam String timestamp,
                                         @RequestParam String nonce,
                                         @RequestParam String echostr) {
        Map<String, Object> account = account(appId);
        if (account == null) return ResponseEntity.notFound().build();
        boolean ok = WxCrypto.checkSignature(String.valueOf(account.get("token")), timestamp, nonce, signature);
        return ok ? ResponseEntity.ok(echostr) : ResponseEntity.status(403).body("invalid signature");
    }

    @PostMapping("/{appId}")
    public ResponseEntity<String> receive(@PathVariable String appId,
                                          @RequestParam String signature,
                                          @RequestParam String timestamp,
                                          @RequestParam String nonce,
                                          @RequestBody(required = false) String body) {
        Map<String, Object> account = account(appId);
        if (account == null) return ResponseEntity.notFound().build();
        String token = String.valueOf(account.get("token"));
        if (!WxCrypto.checkSignature(token, timestamp, nonce, signature)) {
            return ResponseEntity.status(403).body("invalid signature");
        }
        TenantContext.set(((Number) account.get("tenant_id")).longValue(), null);
        try {
            String xml = body == null ? "" : body;
            if (xml.contains("<Encrypt>")) {
                String encrypted = extract(xml, "Encrypt");
                String aesKey = (String) account.get("encoding_aes_key");
                if (aesKey == null || aesKey.isBlank()) return ResponseEntity.ok("success");
                xml = WxCrypto.decrypt(aesKey, appId, encrypted);
            }
            String openId = extract(xml, "FromUserName");
            String msgType = extract(xml, "MsgType");
            String event = extract(xml, "Event");
            String content = extract(xml, "Content");
            Long tid = ((Number) account.get("tenant_id")).longValue();
            Long accountId = ((Number) account.get("id")).longValue();
            jdbc.update("""
                    INSERT INTO mp_message(tenant_id, account_id, open_id, direction, msg_type, content, raw_payload)
                    VALUES (?,?,?, 'IN', ?,?,?)
                    """, tid, accountId, openId, event == null ? msgType : msgType + ":" + event, content, xml);
            if (openId != null) {
                String unionId = null;
                if ("event".equalsIgnoreCase(msgType) && "subscribe".equalsIgnoreCase(event)) {
                    jdbc.update("""
                            INSERT INTO mp_fan(tenant_id, account_id, open_id, subscribe_status, subscribe_at)
                            VALUES (?,?,?,1,NOW())
                            ON DUPLICATE KEY UPDATE subscribe_status=1, subscribe_at=NOW()
                            """, tid, accountId, openId);
                } else if ("event".equalsIgnoreCase(msgType) && "unsubscribe".equalsIgnoreCase(event)) {
                    jdbc.update("UPDATE mp_fan SET subscribe_status=0, unsubscribe_at=NOW() WHERE tenant_id=? AND account_id=? AND open_id=?",
                            tid, accountId, openId);
                }
                List<Map<String, Object>> fans = jdbc.queryForList(
                        "SELECT union_id FROM mp_fan WHERE tenant_id=? AND account_id=? AND open_id=?", tid, accountId, openId);
                if (!fans.isEmpty() && fans.get(0).get("union_id") != null) {
                    unionId = String.valueOf(fans.get(0).get("union_id"));
                }
                if (unionId != null) {
                    jdbc.update("""
                            INSERT INTO member(tenant_id, union_id, open_id, source, status) VALUES (?,?,?, 'MP', 1)
                            ON DUPLICATE KEY UPDATE open_id=VALUES(open_id)
                            """, tid, unionId, openId);
                }
            }
            String reply = autoReply(tid, accountId, msgType, event, content);
            if (reply == null) return ResponseEntity.ok("success");
            String out = """
                    <xml><ToUserName><![CDATA[%s]]></ToUserName><FromUserName><![CDATA[%s]]></FromUserName>
                    <CreateTime>%d</CreateTime><MsgType><![CDATA[text]]></MsgType>
                    <Content><![CDATA[%s]]></Content></xml>
                    """.formatted(openId, appId, System.currentTimeMillis() / 1000, reply);
            jdbc.update("""
                    INSERT INTO mp_message(tenant_id, account_id, open_id, direction, msg_type, content)
                    VALUES (?,?,?, 'OUT', 'text', ?)
                    """, tid, accountId, openId, reply);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(out);
        } catch (Exception e) {
            log.warn("公众号回调处理失败: {}", e.getMessage());
            return ResponseEntity.ok("success");
        } finally {
            TenantContext.clear();
        }
    }

    private Map<String, Object> account(String appId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM mp_account WHERE app_id=? AND deleted=0 LIMIT 1", appId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String autoReply(Long tid, Long accountId, String msgType, String event, String content) {
        List<Map<String, Object>> replies = jdbc.queryForList("""
                SELECT * FROM mp_auto_reply WHERE tenant_id=? AND account_id=? AND enabled=1 ORDER BY id
                """, tid, accountId);
        for (Map<String, Object> r : replies) {
            String scene = String.valueOf(r.get("scene"));
            if ("SUBSCRIBE".equals(scene) && "event".equalsIgnoreCase(msgType) && "subscribe".equalsIgnoreCase(event)) {
                return String.valueOf(r.get("content"));
            }
            if ("KEYWORD".equals(scene) && "text".equalsIgnoreCase(msgType) && content != null) {
                String keyword = String.valueOf(r.get("keyword"));
                boolean match = "FULL".equals(r.get("match_type")) ? keyword.equals(content) : content.contains(keyword);
                if (match) return String.valueOf(r.get("content"));
            }
            if ("MESSAGE".equals(scene) && "text".equalsIgnoreCase(msgType)) {
                return String.valueOf(r.get("content"));
            }
        }
        return null;
    }

    private String extract(String xml, String tag) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var nodes = doc.getElementsByTagName(tag);
            return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
        } catch (Exception e) {
            return null;
        }
    }
}
