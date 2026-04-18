package com.xiaowei.sdk.demo;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/**
 * Demo 的统一配置中心。
 *
 * 参考主客户端工程中的 AppPrefs 设计：
 * 1. 所有联调参数都通过 SharedPreferences 持久化。
 * 2. 每个字段都有明确的默认值，便于真机安装后直接调整。
 * 3. connect() 读取的是这里的当前配置，而不是主页面上的临时输入框。
 */
final class AppPrefs {
    private static final String PREFS = "xiaowei_sdk_demo_prefs";
    private static final String LEGACY_PREFS = "xiaowei_sdk_demo";


    // 下面这组 DEFAULT_* 常量就是 Demo 的默认配置入口。
    private static final String KEY_OPEN_API_BASE_URL = "open_api_base_url";
    private static final String DEFAULT_OPEN_API_BASE_URL = "http://192.168.31.108:8080";
    // private static final String DEFAULT_OPEN_API_BASE_URL = "http://api.xiaoweisoul.vip";

    private static final String KEY_WS_URL = "ws_url";
    private static final String DEFAULT_WS_URL = "ws://192.168.31.108:8000/";
    // private static final String DEFAULT_WS_URL = "ws://soul.xiaoweisoul.vip";

    private static final String KEY_ACCESS_KEY_ID = "access_key_id";
    private static final String DEFAULT_ACCESS_KEY_ID = "ak_be60d1530176d7e4b915ed9c";

    private static final String KEY_ACCESS_KEY_SECRET = "access_key_secret";
    private static final String DEFAULT_ACCESS_KEY_SECRET = "sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7";

    private static final String KEY_INTEGRATION_APP_ID = "integration_app_id";
    private static final String DEFAULT_INTEGRATION_APP_ID = "app_remav935";

    private static final String KEY_SOUL_ID = "soul_id";
    private static final String DEFAULT_SOUL_ID = "soul_demo_game_npc_shopkeeper_v1";

    private static final String KEY_PROTOCOL_VERSION = "protocol_version";
    private static final String DEFAULT_PROTOCOL_VERSION = "1";

    private static final String KEY_LOGICAL_DEVICE_ID = "logical_device_id";
    private static final String DEFAULT_LOGICAL_DEVICE_ID = "app.demo.device-001";

    private static final String KEY_LOGICAL_CLIENT_ID = "logical_client_id";
    private static final String DEFAULT_LOGICAL_CLIENT_ID = "sdk.demo.client-001";

    private static final String KEY_LAST_SEND_TEXT = "last_send_text";
    private static final String LEGACY_KEY_SEND_TEXT = "send_text";
    private static final String DEFAULT_LAST_SEND_TEXT = "你好，讲个故事。";

    private static final String KEY_TTS_PLAYBACK_STRATEGY = "tts_playback_strategy";
    static final String TTS_PLAYBACK_STRATEGY_DUCK_OTHERS = "duck_others";
    static final String TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS = "pause_others";
    static final String TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS = "mix_with_others";
    private static final String DEFAULT_TTS_PLAYBACK_STRATEGY = TTS_PLAYBACK_STRATEGY_DUCK_OTHERS;

    /**
     * 工具类不允许实例化。
     */
    private AppPrefs() {
    }

    /**
     * 一次性读取 connect() 所需的完整配置快照。
     */
    @NonNull
    static ConnectionSettings loadConnectionSettings(@NonNull Context context) {
        return new ConnectionSettings(
                getOpenApiBaseUrl(context),
                getWsUrl(context),
                getAccessKeyId(context),
                getAccessKeySecret(context),
                getIntegrationAppId(context),
                getSoulId(context),
                getProtocolVersion(context),
                getLogicalDeviceId(context),
                getLogicalClientId(context)
        );
    }

    /**
     * 恢复设置页中的 DEFAULT_* 默认值。
     *
     * 这里只重置设置页里的联调参数，不动主页面草稿文本，避免用户正在联调的输入被误清空。
     */
    static void resetConnectionSettings(@NonNull Context context) {
        getPreferences(context).edit()
                .remove(KEY_OPEN_API_BASE_URL)
                .remove(KEY_WS_URL)
                .remove(KEY_ACCESS_KEY_ID)
                .remove(KEY_ACCESS_KEY_SECRET)
                .remove(KEY_INTEGRATION_APP_ID)
                .remove(KEY_SOUL_ID)
                .remove(KEY_PROTOCOL_VERSION)
                .remove(KEY_LOGICAL_DEVICE_ID)
                .remove(KEY_LOGICAL_CLIENT_ID)
                .remove(KEY_TTS_PLAYBACK_STRATEGY)
                .apply();
    }

    /**
     * 读取 OpenAPI Base URL，并做尾部斜杠归一化。
     */
    @NonNull
    static String getOpenApiBaseUrl(@NonNull Context context) {
        return normalizeBaseUrl(getConfigString(context, KEY_OPEN_API_BASE_URL, DEFAULT_OPEN_API_BASE_URL), DEFAULT_OPEN_API_BASE_URL);
    }

    /**
     * 保存 OpenAPI Base URL。
     */
    static void setOpenApiBaseUrl(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_OPEN_API_BASE_URL, normalizeBaseUrl(value, DEFAULT_OPEN_API_BASE_URL));
    }

    /**
     * 读取 WebSocket URL。
     */
    @NonNull
    static String getWsUrl(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_WS_URL, DEFAULT_WS_URL), DEFAULT_WS_URL);
    }

    /**
     * 保存 WebSocket URL。
     */
    static void setWsUrl(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_WS_URL, normalizeConfigText(value, DEFAULT_WS_URL));
    }

    /**
     * 读取 Access Key ID。
     */
    @NonNull
    static String getAccessKeyId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID), DEFAULT_ACCESS_KEY_ID);
    }

    /**
     * 保存 Access Key ID。
     */
    static void setAccessKeyId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_ACCESS_KEY_ID, normalizeConfigText(value, DEFAULT_ACCESS_KEY_ID));
    }

    /**
     * 读取 Access Key Secret。
     */
    @NonNull
    static String getAccessKeySecret(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_ACCESS_KEY_SECRET, DEFAULT_ACCESS_KEY_SECRET), DEFAULT_ACCESS_KEY_SECRET);
    }

    /**
     * 保存 Access Key Secret。
     */
    static void setAccessKeySecret(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_ACCESS_KEY_SECRET, normalizeConfigText(value, DEFAULT_ACCESS_KEY_SECRET));
    }

    /**
     * 读取 integration_app_id。
     */
    @NonNull
    static String getIntegrationAppId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_INTEGRATION_APP_ID, DEFAULT_INTEGRATION_APP_ID), DEFAULT_INTEGRATION_APP_ID);
    }

    /**
     * 保存 integration_app_id。
     */
    static void setIntegrationAppId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_INTEGRATION_APP_ID, normalizeConfigText(value, DEFAULT_INTEGRATION_APP_ID));
    }

    /**
     * 读取 soul_id。
     */
    @NonNull
    static String getSoulId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_SOUL_ID, DEFAULT_SOUL_ID), DEFAULT_SOUL_ID);
    }

    /**
     * 保存 soul_id。
     */
    static void setSoulId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_SOUL_ID, normalizeConfigText(value, DEFAULT_SOUL_ID));
    }

    /**
     * 读取 protocol_version。
     */
    @NonNull
    static String getProtocolVersion(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_PROTOCOL_VERSION, DEFAULT_PROTOCOL_VERSION), DEFAULT_PROTOCOL_VERSION);
    }

    /**
     * 保存 protocol_version。
     */
    static void setProtocolVersion(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_PROTOCOL_VERSION, normalizeConfigText(value, DEFAULT_PROTOCOL_VERSION));
    }

    /**
     * 读取 logical_device_id。
     */
    @NonNull
    static String getLogicalDeviceId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_LOGICAL_DEVICE_ID, DEFAULT_LOGICAL_DEVICE_ID), DEFAULT_LOGICAL_DEVICE_ID);
    }

    /**
     * 保存 logical_device_id。
     */
    static void setLogicalDeviceId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_LOGICAL_DEVICE_ID, normalizeConfigText(value, DEFAULT_LOGICAL_DEVICE_ID));
    }

    /**
     * 读取 logical_client_id。
     */
    @NonNull
    static String getLogicalClientId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_LOGICAL_CLIENT_ID, DEFAULT_LOGICAL_CLIENT_ID), DEFAULT_LOGICAL_CLIENT_ID);
    }

    /**
     * 保存 logical_client_id。
     */
    static void setLogicalClientId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_LOGICAL_CLIENT_ID, normalizeConfigText(value, DEFAULT_LOGICAL_CLIENT_ID));
    }

    /**
     * 读取 TTS 播放策略配置。
     */
    @NonNull
    static String getTtsPlaybackStrategyPreference(@NonNull Context context) {
        return normalizeTtsPlaybackStrategy(getConfigString(context, KEY_TTS_PLAYBACK_STRATEGY, DEFAULT_TTS_PLAYBACK_STRATEGY));
    }

    /**
     * 保存 TTS 播放策略配置。
     */
    static void setTtsPlaybackStrategyPreference(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_TTS_PLAYBACK_STRATEGY, normalizeTtsPlaybackStrategy(value));
    }

    /**
     * 把持久化值转换成更适合展示和日志排查的策略名称。
     */
    @NonNull
    static String describeTtsPlaybackStrategy(@NonNull String value) {
        String normalized = normalizeTtsPlaybackStrategy(value);
        if (TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS.equals(normalized)) {
            return "pause_others";
        }
        if (TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS.equals(normalized)) {
            return "mix_with_others";
        }
        return "duck_others";
    }

    /**
     * 读取主页面默认文本输入。
     *
     * 第一次启动时使用默认值；一旦用户自己清空并保存，也要保留这个空值，不能再次强行回填默认文案。
     */
    @NonNull
    static String getLastSendText(@NonNull Context context) {
        migrateLegacyLastSendTextIfNeeded(context);
        return getDraftString(context, KEY_LAST_SEND_TEXT, DEFAULT_LAST_SEND_TEXT);
    }

    /**
     * 暴露当前默认文本，供历史遗留代码复用同一份默认值。
     */
    @NonNull
    static String defaultLastSendText() {
        return DEFAULT_LAST_SEND_TEXT;
    }

    /**
     * 保存主页面最后一次文本输入。
     */
    static void setLastSendText(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_LAST_SEND_TEXT, normalizeDraftText(value));
    }

    /**
     * 读取一个连接配置字段。
     */
    @NonNull
    private static String getConfigString(@NonNull Context context, @NonNull String key, @NonNull String defaultValue) {
        String value = getPreferences(context).getString(key, defaultValue);
        return value == null ? defaultValue : value;
    }

    /**
     * 读取一个主页面草稿字段。
     *
     * 不存在时才使用默认值；如果用户明确保存了空串，则返回空串。
     */
    @NonNull
    private static String getDraftString(@NonNull Context context, @NonNull String key, @NonNull String defaultValue) {
        SharedPreferences preferences = getPreferences(context);
        if (!preferences.contains(key)) {
            return defaultValue;
        }
        String value = preferences.getString(key, defaultValue);
        return value == null ? "" : value;
    }

    /**
     * 兼容旧版 Demo 把发送文本保存在 send_text 的历史数据。
     */
    private static void migrateLegacyLastSendTextIfNeeded(@NonNull Context context) {
        SharedPreferences preferences = getPreferences(context);
        if (preferences.contains(KEY_LAST_SEND_TEXT)) {
            return;
        }
        SharedPreferences legacyPreferences = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        if (!legacyPreferences.contains(LEGACY_KEY_SEND_TEXT)) {
            return;
        }
        String legacyValue = legacyPreferences.getString(LEGACY_KEY_SEND_TEXT, DEFAULT_LAST_SEND_TEXT);
        preferences.edit().putString(KEY_LAST_SEND_TEXT, legacyValue == null ? "" : legacyValue).apply();
    }

    /**
     * 持久化一个字符串字段。
     */
    private static void putString(@NonNull Context context, @NonNull String key, @NonNull String value) {
        getPreferences(context).edit().putString(key, value).apply();
    }

    /**
     * 返回 Demo 对应的 SharedPreferences 实例。
     */
    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * 归一化连接配置字段；空串时回退到默认值。
     */
    @NonNull
    private static String normalizeConfigText(@NonNull String value, @NonNull String defaultValue) {
        String normalized = value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    /**
     * 归一化 TTS 播放策略。
     */
    @NonNull
    private static String normalizeTtsPlaybackStrategy(@NonNull String value) {
        String normalized = value.trim();
        if (TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS.equals(normalized)) {
            return TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS;
        }
        if (TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS.equals(normalized)) {
            return TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS;
        }
        return TTS_PLAYBACK_STRATEGY_DUCK_OTHERS;
    }

    /**
     * 归一化 Base URL，去掉末尾多余的斜杠。
     */
    @NonNull
    private static String normalizeBaseUrl(@NonNull String value, @NonNull String defaultValue) {
        String normalized = normalizeConfigText(value, defaultValue);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 归一化主页面文本草稿。
     * 这里不 trim，保留用户自己的输入样式。
     */
    @NonNull
    private static String normalizeDraftText(@NonNull String value) {
        return value;
    }

    /**
     * connect() 使用的不可变配置快照。
     */
    static final class ConnectionSettings {
        final String openApiBaseUrl;
        final String wsUrl;
        final String accessKeyId;
        final String accessKeySecret;
        final String integrationAppId;
        final String soulId;
        final String protocolVersion;
        final String logicalDeviceId;
        final String logicalClientId;

        /**
         * 统一收口当前连接所需的全部配置字段。
         */
        ConnectionSettings(
                @NonNull String openApiBaseUrl,
                @NonNull String wsUrl,
                @NonNull String accessKeyId,
                @NonNull String accessKeySecret,
                @NonNull String integrationAppId,
                @NonNull String soulId,
                @NonNull String protocolVersion,
                @NonNull String logicalDeviceId,
                @NonNull String logicalClientId
        ) {
            this.openApiBaseUrl = openApiBaseUrl;
            this.wsUrl = wsUrl;
            this.accessKeyId = accessKeyId;
            this.accessKeySecret = accessKeySecret;
            this.integrationAppId = integrationAppId;
            this.soulId = soulId;
            this.protocolVersion = protocolVersion;
            this.logicalDeviceId = logicalDeviceId;
            this.logicalClientId = logicalClientId;
        }
    }
}
