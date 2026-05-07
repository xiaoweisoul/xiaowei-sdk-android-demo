package vip.xiaoweisoul.sdk.demo;

import androidx.annotation.NonNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 仅供 Demo 示例使用的 OpenAPI 角色列表客户端。
 * 它会直接使用 access_key 查询当前用户可用的 user_soul_profile，不应复制到生产宿主。
 */
final class DebugOpenApiSoulProfileClient {
    private static final String SOUL_PROFILES_PATH = "/api/open/v1/soul-profiles";

    private final OkHttpClient httpClient;
    private final String openApiBaseUrl;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final DebugOpenApiSessionTokenProvider.Logger logger;

    /**
     * 保存查询角色列表所需的 OpenAPI 参数。
     */
    DebugOpenApiSoulProfileClient(
            @NonNull String openApiBaseUrl,
            @NonNull String accessKeyId,
            @NonNull String accessKeySecret,
            @NonNull DebugOpenApiSessionTokenProvider.Logger logger
    ) {
        this.httpClient = new OkHttpClient();
        this.openApiBaseUrl = requireNonBlank(trimTrailingSlash(openApiBaseUrl), "openApiBaseUrl");
        this.accessKeyId = requireNonBlank(accessKeyId, "accessKeyId");
        this.accessKeySecret = requireNonBlank(accessKeySecret, "accessKeySecret");
        this.logger = logger;
    }

    /**
     * 查询当前 access_key 所属用户下的可用角色列表。
     */
    @NonNull
    List<SoulProfileOption> listSoulProfiles() throws IOException {
        String requestUrl = openApiBaseUrl + SOUL_PROFILES_PATH;
        logger.log("[SoulProfile] 向 OpenAPI 请求角色列表 url=" + requestUrl);

        Request request = new Request.Builder()
                .url(requestUrl)
                .header("X-Access-Key-Id", accessKeyId)
                .header("X-Access-Key-Secret", accessKeySecret)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
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
            JsonArray items = data.has("items") && data.get("items").isJsonArray()
                    ? data.getAsJsonArray("items")
                    : new JsonArray();
            List<SoulProfileOption> out = new ArrayList<>();
            for (JsonElement item : items) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject row = item.getAsJsonObject();
                String soulId = optString(row, "soul_id").trim();
                if (soulId.isEmpty()) {
                    continue;
                }
                out.add(new SoulProfileOption(optString(row, "name"), soulId));
            }
            logger.log("[SoulProfile] 获取成功 count=" + out.size());
            return out;
        }
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
