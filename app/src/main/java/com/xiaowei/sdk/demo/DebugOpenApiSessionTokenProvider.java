package com.xiaowei.sdk.demo;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowei.sdk.sessioncore.SessionTokenProvider;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 仅供 Demo 示例使用的 token provider。
 * 它会直接调用 OpenAPI 用 access_key 换取 ws_session_token，不应复制到生产宿主。
 * Demo 当前不显式透传 end_user_id，统一由服务端按需要决定是否附带。
 */
final class DebugOpenApiSessionTokenProvider implements SessionTokenProvider {
    private static final String WS_SESSION_TOKEN_PATH = "/api/open/v1/ws-session-tokens";

    /**
     * 供 Demo 把关键动作输出到日志面板。
     */
    interface Logger {
        /**
         * 输出一条调试日志。
         */
        void log(String message);
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final String openApiBaseUrl;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String integrationAppId;
    private final String soulId;
    private final Logger logger;

    /**
     * 保存调试所需的 OpenAPI 参数。
     */
    DebugOpenApiSessionTokenProvider(
            @NonNull String openApiBaseUrl,
            @NonNull String accessKeyId,
            @NonNull String accessKeySecret,
            @NonNull String integrationAppId,
            @NonNull String soulId,
            @NonNull Logger logger
    ) {
        this.httpClient = new OkHttpClient();
        this.openApiBaseUrl = requireNonBlank(trimTrailingSlash(openApiBaseUrl), "openApiBaseUrl");
        this.accessKeyId = requireNonBlank(accessKeyId, "accessKeyId");
        this.accessKeySecret = requireNonBlank(accessKeySecret, "accessKeySecret");
        this.integrationAppId = requireNonBlank(integrationAppId, "integrationAppId");
        this.soulId = requireNonBlank(soulId, "soulId");
        this.logger = logger;
    }

    /**
     * 每次 connect() 前调用 OpenAPI 换取本次握手使用的短期 token。
     */
    @Override
    public String getSessionToken() throws Exception {
        String requestUrl = buildWsSessionTokenUrl();
        logger.log("[Token] 向 OpenAPI 请求 ws_session_token url=" + requestUrl);

        // 构造与 OpenAPI 冻结合同一致的请求体。
        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.addProperty("integration_app_id", integrationAppId);
        requestBodyJson.addProperty("soul_id", soulId);
        requestBodyJson.addProperty("trace_id", "demo-" + System.currentTimeMillis());

        Request request = new Request.Builder()
                // 这里直接用 access_key 调 OpenAPI，仅限 Demo 示例使用。
                .url(requestUrl)
                .header("Content-Type", "application/json")
                .header("X-Access-Key-Id", accessKeyId)
                .header("X-Access-Key-Secret", accessKeySecret)
                .post(RequestBody.create(requestBodyJson.toString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            // 先校验 HTTP 层，再校验业务 code / data 字段。
            if (!response.isSuccessful()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                throw new IOException("OpenAPI 返回失败: url=" + requestUrl + " HTTP " + response.code() + " body=" + responseBody);
            }

            String body = response.body() == null ? "" : response.body().string();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int code = root.has("code") && !root.get("code").isJsonNull() ? root.get("code").getAsInt() : -1;
            if (code != 0) {
                throw new IOException("OpenAPI 返回业务错误: code=" + code + " message=" + optString(root, "message"));
            }
            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                throw new IOException("OpenAPI 返回缺少 data");
            }

            JsonObject data = root.getAsJsonObject("data");
            String token = optString(data, "ws_session_token").trim();
            if (token.isEmpty()) {
                throw new IOException("OpenAPI 未返回 ws_session_token");
            }
            logger.log("[Token] 获取成功 expires_at=" + optString(data, "expires_at"));
            return token;
        }
    }

    @NonNull
    private String buildWsSessionTokenUrl() {
        return openApiBaseUrl + WS_SESSION_TOKEN_PATH;
    }

    /**
     * 从 JSON 中安全读取字符串字段。
     */
    @NonNull
    private static String optString(@NonNull JsonObject root, @NonNull String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return "";
        }
        return root.get(key).getAsString();
    }

    /**
     * 校验字符串参数非空。
     */
    @NonNull
    private static String requireNonBlank(@NonNull String value, @NonNull String fieldName) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return normalized;
    }

    /**
     * 去掉 OpenAPI Base URL 末尾多余的斜杠，避免重复拼接路径。
     */
    @NonNull
    private static String trimTrailingSlash(@NonNull String rawUrl) {
        String normalized = rawUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
