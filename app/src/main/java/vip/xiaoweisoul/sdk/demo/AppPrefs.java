package vip.xiaoweisoul.sdk.demo;

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
  // private static final String DEFAULT_OPEN_API_BASE_URL = "http://192.168.31.108:8080";
      private static final String DEFAULT_OPEN_API_BASE_URL = "http://api.xiaoweisoul.vip";

    private static final String KEY_WS_URL = "ws_url";
  // private static final String DEFAULT_WS_URL = "ws://192.168.31.108:8000/";
      private static final String DEFAULT_WS_URL = "ws://soul.xiaoweisoul.vip";

    private static final String KEY_ACCESS_KEY_ID = "access_key_id";
    private static final String DEFAULT_ACCESS_KEY_ID = "ak_be60d1530176d7e4b915ed9c";

    private static final String KEY_ACCESS_KEY_SECRET = "access_key_secret";
    private static final String DEFAULT_ACCESS_KEY_SECRET = "sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7";

    private static final String KEY_INTEGRATION_APP_ID = "integration_app_id";
    private static final String DEFAULT_INTEGRATION_APP_ID = "app_remav935";

    private static final String KEY_END_USER_ID = "end_user_id";
    private static final String DEFAULT_END_USER_ID = "app_demo_end_user_001";

    private static final String KEY_SOUL_ID = "soul_id";
    private static final String DEFAULT_SOUL_ID = "soul_demo_chinese_general_ai_assistant_v1";

    private static final String KEY_PROTOCOL_VERSION = "protocol_version";
    private static final String DEFAULT_PROTOCOL_VERSION = "1";

    private static final String KEY_LOGICAL_DEVICE_ID = "logical_device_id";
    private static final String DEFAULT_LOGICAL_DEVICE_ID = "app.demo.device-001";

    private static final String KEY_LOGICAL_CLIENT_ID = "logical_client_id";
    private static final String DEFAULT_LOGICAL_CLIENT_ID = "sdk.demo.client-001";

    private static final String KEY_LAST_SEND_TEXT = "last_send_text";
    private static final String KEY_LAST_SEND_TEXT_ZH = "last_send_text_zh";
    private static final String KEY_LAST_SEND_TEXT_JA = "last_send_text_ja";
    private static final String LEGACY_KEY_SEND_TEXT = "send_text";
    private static final String DEFAULT_LAST_SEND_TEXT_ZH = "你好，讲个故事。";
    private static final String DEFAULT_LAST_SEND_TEXT_JA = "こんにちは。物語をひとつ聞かせてください。";

    private static final String KEY_TTS_PLAYBACK_STRATEGY = "tts_playback_strategy";
    private static final String KEY_DEMO_LANGUAGE = "demo_language";
    private static final String KEY_SESSION_PROMPT_ENABLED = "session_prompt_enabled";
    private static final String KEY_SESSION_PROMPT_TEXT = "session_prompt_text";
    private static final String KEY_WELCOME_MESSAGE_ENABLED = "welcome_message_enabled";
    private static final String KEY_WELCOME_MESSAGE_TEXT = "welcome_message_text";
    private static final String KEY_VOICE_INPUT_MODE = "voice_input_mode";
    static final String DEMO_LANGUAGE_ZH = "zh";
    static final String DEMO_LANGUAGE_JA = "ja";
    static final String TTS_PLAYBACK_STRATEGY_DUCK_OTHERS = "duck_others";
    static final String TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS = "pause_others";
    static final String TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS = "mix_with_others";
    static final String VOICE_INPUT_MODE_MANUAL = "manual";
    static final String VOICE_INPUT_MODE_REALTIME = "realtime";
    private static final String DEFAULT_TTS_PLAYBACK_STRATEGY = TTS_PLAYBACK_STRATEGY_DUCK_OTHERS;
    private static final String DEFAULT_VOICE_INPUT_MODE = VOICE_INPUT_MODE_REALTIME;
    private static final boolean DEFAULT_SESSION_PROMPT_ENABLED = false;
    // 首次安装只预置可编辑文案，不主动播放；用户明确勾选后，下一次连接才会上报。
    private static final boolean DEFAULT_WELCOME_MESSAGE_ENABLED = false;
    private static final String DEFAULT_WELCOME_MESSAGE = "你好，欢迎使用小微。";
    private static final String DEFAULT_SESSION_PROMPT = "你是一位专业的产品解说员。请根据以下广告内容进行生动、自然的口语化讲解：\n"
            + "\n"
            + "【广告内容】\n"
            + "这是 Fire Suppressor Pro 便携式灭火器的核心规格参数。产品采用固体药柱和常压储存设计，无需维护，可在紧急情况下快速投入使用。通过先进的纳米粒子气溶胶灭火技术，实现更广范围和更高效率的灭火效果。产品支持十秒极速灭火，喷射距离大于三米，喷射时间超过八秒，并能够在零下三十度到七十度环境下稳定工作。适用于A类、B类、C类、E类以及厨房火灾等多种火情，广泛应用于办公室、家庭、工厂、车辆、船舶、飞机和实验室等场景。\n"
            + "\n"
            + "【讲解要求】\n"
            + "1. 围绕上述内容进行讲解，可以适当扩展，但不要偏离主题\n"
            + "2. 使用自然流畅的口语表达\n"
            + "3. 语气亲切、专业、有吸引力\n"
            + "4. 长度适中，3-5句话";

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
                getEndUserId(context),
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
                .remove(KEY_END_USER_ID)
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
     * 读取 end_user_id。
     */
    @NonNull
    static String getEndUserId(@NonNull Context context) {
        return normalizeConfigText(getConfigString(context, KEY_END_USER_ID, DEFAULT_END_USER_ID), DEFAULT_END_USER_ID);
    }

    /**
     * 保存 end_user_id。
     */
    static void setEndUserId(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_END_USER_ID, normalizeConfigText(value, DEFAULT_END_USER_ID));
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
     * 读取 Demo 主页面展示语言；默认中文。
     */
    @NonNull
    static String getDemoLanguage(@NonNull Context context) {
        return normalizeDemoLanguage(getConfigString(context, KEY_DEMO_LANGUAGE, DEMO_LANGUAGE_ZH));
    }

    /**
     * 保存 Demo 主页面展示语言。
     */
    static void setDemoLanguage(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_DEMO_LANGUAGE, normalizeDemoLanguage(value));
    }

    /**
     * 读取主页面当前选择的语音输入模式；默认 realtime。
     */
    @NonNull
    static String getVoiceInputMode(@NonNull Context context) {
        return normalizeVoiceInputMode(getConfigString(context, KEY_VOICE_INPUT_MODE, DEFAULT_VOICE_INPUT_MODE));
    }

    /**
     * 保存主页面当前选择的语音输入模式。
     */
    static void setVoiceInputMode(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_VOICE_INPUT_MODE, normalizeVoiceInputMode(value));
    }

    /**
     * 读取连接时是否显式携带 hello.session_config.prompt。
     */
    static boolean isSessionPromptEnabled(@NonNull Context context) {
        return getPreferences(context).getBoolean(KEY_SESSION_PROMPT_ENABLED, DEFAULT_SESSION_PROMPT_ENABLED);
    }

    /**
     * 保存连接时是否显式携带 hello.session_config.prompt。
     */
    static void setSessionPromptEnabled(@NonNull Context context, boolean enabled) {
        putBoolean(context, KEY_SESSION_PROMPT_ENABLED, enabled);
    }

    /**
     * 读取当前保存的 Session Prompt 文本。
     *
     * 不存在时才回退到公开默认值；如果用户明确清空并保存，则保留空串。
     */
    @NonNull
    static String getSessionPrompt(@NonNull Context context) {
        return getDraftString(context, KEY_SESSION_PROMPT_TEXT, DEFAULT_SESSION_PROMPT);
    }

    /**
     * 保存当前 Session Prompt 文本。
     */
    static void setSessionPrompt(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_SESSION_PROMPT_TEXT, normalizeDraftText(value));
    }

    /**
     * 读取下一次连接是否携带 hello.session_config.welcome_message；首次安装默认关闭。
     */
    static boolean isWelcomeMessageEnabled(@NonNull Context context) {
        return getPreferences(context).getBoolean(KEY_WELCOME_MESSAGE_ENABLED, DEFAULT_WELCOME_MESSAGE_ENABLED);
    }

    /**
     * 保存欢迎语开关。关闭只停止上报，不清除文本，方便用户以后重新启用。
     * 例如用户临时不想播放欢迎语时取消勾选，再次勾选仍能继续使用上次编辑的内容。
     */
    static void setWelcomeMessageEnabled(@NonNull Context context, boolean enabled) {
        putBoolean(context, KEY_WELCOME_MESSAGE_ENABLED, enabled);
    }

    /**
     * 读取当前保存的欢迎语；用户从未编辑时返回公开默认文案。
     */
    @NonNull
    static String getWelcomeMessage(@NonNull Context context) {
        return getDraftString(context, KEY_WELCOME_MESSAGE_TEXT, DEFAULT_WELCOME_MESSAGE);
    }

    /**
     * 保存欢迎语文本；允许保存空串，由主页面明确提示启用但内容为空时不会播放。
     */
    static void setWelcomeMessage(@NonNull Context context, @NonNull String value) {
        putString(context, KEY_WELCOME_MESSAGE_TEXT, normalizeDraftText(value));
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
    static String getLastSendText(@NonNull Context context, boolean japanese) {
        migrateLegacyLastSendTextIfNeeded(context);
        return getDraftString(
                context,
                japanese ? KEY_LAST_SEND_TEXT_JA : KEY_LAST_SEND_TEXT_ZH,
                japanese ? DEFAULT_LAST_SEND_TEXT_JA : DEFAULT_LAST_SEND_TEXT_ZH
        );
    }

    /**
     * 暴露当前默认文本，供历史遗留代码复用同一份默认值。
     */
    @NonNull
    static String defaultLastSendText(boolean japanese) {
        return japanese ? DEFAULT_LAST_SEND_TEXT_JA : DEFAULT_LAST_SEND_TEXT_ZH;
    }

    /**
     * 保存主页面最后一次文本输入。
     */
    static void setLastSendText(@NonNull Context context, boolean japanese, @NonNull String value) {
        putString(context, japanese ? KEY_LAST_SEND_TEXT_JA : KEY_LAST_SEND_TEXT_ZH, normalizeDraftText(value));
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
        if (preferences.contains(KEY_LAST_SEND_TEXT_ZH) || preferences.contains(KEY_LAST_SEND_TEXT_JA)) {
            return;
        }
        if (preferences.contains(KEY_LAST_SEND_TEXT)) {
            String value = preferences.getString(KEY_LAST_SEND_TEXT, DEFAULT_LAST_SEND_TEXT_ZH);
            preferences.edit()
                    .putString(KEY_LAST_SEND_TEXT_ZH, value == null ? "" : value)
                    .remove(KEY_LAST_SEND_TEXT)
                    .apply();
            return;
        }
        SharedPreferences legacyPreferences = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        if (!legacyPreferences.contains(LEGACY_KEY_SEND_TEXT)) {
            return;
        }
        String legacyValue = legacyPreferences.getString(LEGACY_KEY_SEND_TEXT, DEFAULT_LAST_SEND_TEXT_ZH);
        preferences.edit().putString(KEY_LAST_SEND_TEXT_ZH, legacyValue == null ? "" : legacyValue).apply();
    }

    /**
     * 持久化一个字符串字段。
     */
    private static void putString(@NonNull Context context, @NonNull String key, @NonNull String value) {
        getPreferences(context).edit().putString(key, value).apply();
    }

    /**
     * 持久化一个布尔字段。
     */
    private static void putBoolean(@NonNull Context context, @NonNull String key, boolean value) {
        getPreferences(context).edit().putBoolean(key, value).apply();
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
     * 归一化 Demo 展示语言，当前只支持中文和日文。
     */
    @NonNull
    private static String normalizeDemoLanguage(@NonNull String value) {
        return DEMO_LANGUAGE_JA.equals(value.trim()) ? DEMO_LANGUAGE_JA : DEMO_LANGUAGE_ZH;
    }

    /**
     * 归一化主页面语音输入模式；当前只支持 manual / realtime。
     */
    @NonNull
    private static String normalizeVoiceInputMode(@NonNull String value) {
        return VOICE_INPUT_MODE_MANUAL.equals(value.trim()) ? VOICE_INPUT_MODE_MANUAL : VOICE_INPUT_MODE_REALTIME;
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
        final String endUserId;
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
                @NonNull String endUserId,
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
            this.endUserId = endUserId;
            this.soulId = soulId;
            this.protocolVersion = protocolVersion;
            this.logicalDeviceId = logicalDeviceId;
            this.logicalClientId = logicalClientId;
        }
    }
}
