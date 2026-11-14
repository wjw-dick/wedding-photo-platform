package com.wedding.open;

import com.wedding.common.api.BizException;
import com.wedding.common.security.JwtService;
import com.wedding.common.security.PermissionService;
import com.wedding.platform.entity.PlatformTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 租户 CAS 2.0 单点登录：发起跳转、ticket 校验、账号映射、JWT 签发。
 * 配置来源于 sso_config（按 tenant_id 唯一），协议固定走 CAS 2.0 的 /login 与 /serviceValidate。
 */
@Service
@RequiredArgsConstructor
public class CasSsoService {
    private final TenantResolver tenantResolver;
    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final PermissionService permissionService;

    private static final String SUCCESS_PATH = "/#/sso-login";
    private static final String FAIL_PATH = "/#/login";
    private static final List<String> MATCH_COLUMNS = List.of("username", "email", "phone");

    public Map<String, Object> config(String tenantCode) {
        PlatformTenant tenant = tenantResolver.byCode(tenantCode);
        Map<String, Object> cfg = loadConfig(tenant.getTenantId());
        boolean ready = isReady(cfg);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", ready ? 1 : 0);
        out.put("protocol", cfg == null ? null : cfg.get("protocol"));
        out.put("serverUrl", cfg == null ? null : cfg.get("server_url"));
        out.put("buttonText", "统一认证登录（CAS）");
        return out;
    }

    public String loginUrl(String tenantCode) {
        PlatformTenant tenant = tenantResolver.byCode(tenantCode);
        Map<String, Object> cfg = requireReady(tenant);
        return trimSlash(String.valueOf(cfg.get("server_url"))) + "/login?service=" + enc(serviceUrl(tenant, cfg));
    }

    public String callback(String tenantCode, String ticket) {
        PlatformTenant tenant = tenantResolver.byCode(tenantCode);
        Map<String, Object> cfg = requireReady(tenant);
        String base = frontendBase(cfg);
        if (ticket == null || ticket.isBlank()) {
            return fail(base, "缺少 CAS ticket");
        }
        String validateUrl = trimSlash(String.valueOf(cfg.get("server_url")))
                + "/serviceValidate?service=" + enc(serviceUrl(tenant, cfg))
                + "&ticket=" + enc(ticket);
        CasResult result;
        try {
            result = parseCasResponse(httpGet(validateUrl));
        } catch (Exception e) {
            return fail(base, "CAS 校验请求失败：" + e.getMessage());
        }
        if (result.user() == null) {
            return fail(base, result.message());
        }
        try {
            Long userId = resolveUserId(tenant.getTenantId(), result, cfg.get("account_mapping"));
            if (userId == null) {
                return fail(base, "统一认证账号 " + result.user() + " 未在本系统开通，请联系管理员");
            }
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, username, user_type, store_id FROM sys_user WHERE tenant_id=? AND id=? AND status=1 AND deleted=0",
                    tenant.getTenantId(), userId);
            String username = String.valueOf(row.get("username"));
            String userType = String.valueOf(row.get("user_type"));
            Long storeId = toLong(row.get("store_id"));
            String token = jwtService.issue(userId, tenant.getTenantId(), tenantCode,
                    username, userType, storeId, null);
            jdbc.update("INSERT INTO sys_login_log(tenant_id,user_id,username,user_type,success,created_at) VALUES (?,?,?,?,1,NOW())",
                    tenant.getTenantId(), userId, username, userType);
            return base + SUCCESS_PATH + "?token=" + enc(token) + "&tenantCode=" + enc(tenantCode);
        } catch (Exception e) {
            return fail(base, "SSO 登录失败：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 配置

    private Map<String, Object> loadConfig(Long tenantId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled, protocol, server_url, service_callback, condition_field, account_mapping FROM sso_config WHERE tenant_id=?",
                tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean isReady(Map<String, Object> cfg) {
        if (cfg == null) return false;
        Object enabled = cfg.get("enabled");
        if (enabled == null || ((Number) enabled).intValue() != 1) return false;
        return notBlank(cfg.get("server_url")) && notBlank(cfg.get("service_callback"));
    }

    private Map<String, Object> requireReady(PlatformTenant tenant) {
        Map<String, Object> cfg = loadConfig(tenant.getTenantId());
        if (!isReady(cfg)) {
            throw new BizException(400, "本租户未启用 SSO，或 SSO 服务器地址/回调地址未配置");
        }
        return cfg;
    }

    /** service = 配置的回调地址 + 租户参数（CAS 回跳时会把 ticket 追加到同一 service 上） */
    private String serviceUrl(PlatformTenant tenant, Map<String, Object> cfg) {
        String callback = String.valueOf(cfg.get("service_callback")).trim();
        String joiner = callback.contains("?") ? "&" : "?";
        return callback + joiner + "tenantCode=" + enc(tenant.getTenantCode());
    }

    private String frontendBase(Map<String, Object> cfg) {
        try {
            URI uri = URI.create(String.valueOf(cfg.get("service_callback")).trim());
            String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
            return uri.getScheme() + "://" + uri.getHost() + port;
        } catch (Exception e) {
            throw new BizException(400, "SSO 回调地址格式不正确");
        }
    }

    // ---------------------------------------------------------------- 账号映射

    private Long resolveUserId(Long tenantId, CasResult result, Object mapping) {
        List<String> columns = matchColumns(mapping);
        List<String> values = candidateValues(result);
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            for (String column : columns) {
                List<Long> ids = jdbc.queryForList(
                        "SELECT id FROM sys_user WHERE tenant_id=? AND deleted=0 AND status=1 AND " + column + "=? LIMIT 1",
                        Long.class, tenantId, value);
                if (!ids.isEmpty()) return ids.get(0);
            }
        }
        return null;
    }

    /** account_mapping 支持 username / email / phone，留空则依次尝试三者 */
    private List<String> matchColumns(Object mapping) {
        String field = mapping == null ? "" : String.valueOf(mapping).trim().toLowerCase(Locale.ROOT);
        if (field.isBlank()) return MATCH_COLUMNS;
        for (String column : MATCH_COLUMNS) {
            if (field.contains(column)) return List.of(column);
        }
        return MATCH_COLUMNS;
    }

    private List<String> candidateValues(CasResult result) {
        List<String> values = new ArrayList<>();
        values.add(result.user());
        String email = result.attributes().get("email");
        if (email != null) values.add(email);
        if (result.user() != null && result.user().contains("@")) {
            values.add(result.user().substring(0, result.user().indexOf('@')));
        }
        return values;
    }

    // ---------------------------------------------------------------- CAS 协议

    private String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/xml,text/xml,*/*")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new BizException(502, "CAS 服务返回 HTTP " + response.statusCode());
        }
        return response.body();
    }

    private CasResult parseCasResponse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList failures = doc.getElementsByTagNameNS("*", "authenticationFailure");
        if (failures.getLength() > 0) {
            Element failure = (Element) failures.item(0);
            String code = failure.getAttribute("code");
            String text = failure.getTextContent() == null ? "" : failure.getTextContent().trim();
            return new CasResult(null, "CAS 校验失败（" + code + "）：" + text, Map.of());
        }
        NodeList users = doc.getElementsByTagNameNS("*", "user");
        if (users.getLength() == 0) {
            return new CasResult(null, "CAS 未返回用户标识", Map.of());
        }
        String user = users.item(0).getTextContent().trim();
        Map<String, String> attributes = new LinkedHashMap<>();
        NodeList attrHolders = doc.getElementsByTagNameNS("*", "attributes");
        if (attrHolders.getLength() > 0) {
            NodeList children = ((Element) attrHolders.item(0)).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                String name = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
                attributes.put(name, node.getTextContent() == null ? "" : node.getTextContent().trim());
            }
        }
        return new CasResult(user, null, attributes);
    }

    // ---------------------------------------------------------------- 工具

    private String fail(String base, String message) {
        return base + FAIL_PATH + "?sso_error=" + enc(message == null ? "SSO 登录失败" : message);
    }

    private boolean notBlank(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String trimSlash(String url) {
        String trimmed = url == null ? "" : url.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        return ((Number) value).longValue();
    }

    private record CasResult(String user, String message, Map<String, String> attributes) {
    }
}
