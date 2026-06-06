package vip.xiaoweisoul.sdk.demo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 把本机 local.properties 中的联调默认值灌入 SharedPreferences。
 *
 * 这里只在字段还没有被用户保存时才写入，避免覆盖用户已经在设置页调整过的值。
 */
final class LocalDebugDefaults {
    private static final String PREFS = "xiaowei_sdk_demo_prefs";
    private static final String KEY_OPEN_API_BASE_URL = "open_api_base_url";
    private static final String KEY_WS_URL = "ws_url";
    private static final String KEY_ACCESS_KEY_ID = "access_key_id";
    private static final String KEY_ACCESS_KEY_SECRET = "access_key_secret";
    private static final String KEY_INTEGRATION_APP_ID = "integration_app_id";
    private static final String KEY_END_USER_ID = "end_user_id";
    private static final String KEY_SOUL_ID = "soul_id";

    private LocalDebugDefaults() {
    }

    static void applyConnectionDefaultsIfMissing(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        applyOpenApiBaseUrl(context, preferences);
        applyWsUrl(context, preferences);
        applyAccessKeyId(context, preferences);
        applyAccessKeySecret(context, preferences);
        applyIntegrationAppId(context, preferences);
        applyEndUserId(context, preferences);
        applySoulId(context, preferences);
    }

    private static void applyOpenApiBaseUrl(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_OPEN_API_BASE_URL) && hasText(BuildConfig.LOCAL_DEFAULT_OPEN_API_BASE_URL)) {
            AppPrefs.setOpenApiBaseUrl(context, BuildConfig.LOCAL_DEFAULT_OPEN_API_BASE_URL);
        }
    }

    private static void applyWsUrl(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_WS_URL) && hasText(BuildConfig.LOCAL_DEFAULT_WS_URL)) {
            AppPrefs.setWsUrl(context, BuildConfig.LOCAL_DEFAULT_WS_URL);
        }
    }

    private static void applyAccessKeyId(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_ACCESS_KEY_ID) && hasText(BuildConfig.LOCAL_DEFAULT_ACCESS_KEY_ID)) {
            AppPrefs.setAccessKeyId(context, BuildConfig.LOCAL_DEFAULT_ACCESS_KEY_ID);
        }
    }

    private static void applyAccessKeySecret(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_ACCESS_KEY_SECRET) && hasText(BuildConfig.LOCAL_DEFAULT_ACCESS_KEY_SECRET)) {
            AppPrefs.setAccessKeySecret(context, BuildConfig.LOCAL_DEFAULT_ACCESS_KEY_SECRET);
        }
    }

    private static void applyIntegrationAppId(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_INTEGRATION_APP_ID) && hasText(BuildConfig.LOCAL_DEFAULT_INTEGRATION_APP_ID)) {
            AppPrefs.setIntegrationAppId(context, BuildConfig.LOCAL_DEFAULT_INTEGRATION_APP_ID);
        }
    }

    private static void applyEndUserId(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_END_USER_ID) && hasText(BuildConfig.LOCAL_DEFAULT_END_USER_ID)) {
            AppPrefs.setEndUserId(context, BuildConfig.LOCAL_DEFAULT_END_USER_ID);
        }
    }

    private static void applySoulId(@NonNull Context context, @NonNull SharedPreferences preferences) {
        if (!preferences.contains(KEY_SOUL_ID) && hasText(BuildConfig.LOCAL_DEFAULT_SOUL_ID)) {
            AppPrefs.setSoulId(context, BuildConfig.LOCAL_DEFAULT_SOUL_ID);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
