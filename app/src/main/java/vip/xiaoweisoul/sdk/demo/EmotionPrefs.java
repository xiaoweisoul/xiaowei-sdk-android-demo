package vip.xiaoweisoul.sdk.demo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 管理 Demo 侧 hello emotion 开关。
 *
 * 单独拆文件的原因：
 * 1. 让 AppPrefs.java 可以回到公开仓库的稳定版本；
 * 2. 保持已有 SharedPreferences key 不变，避免用户本地开关状态丢失。
 */
final class EmotionPrefs {
    private static final String PREFS = "xiaowei_sdk_demo_prefs";
    private static final String KEY_EMOTION_ENABLED = "emotion_enabled";
    private static final boolean DEFAULT_EMOTION_ENABLED = true;

    private EmotionPrefs() {
    }

    static boolean isEnabled(@NonNull Context context) {
        return getPreferences(context).getBoolean(KEY_EMOTION_ENABLED, DEFAULT_EMOTION_ENABLED);
    }

    static void setEnabled(@NonNull Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(KEY_EMOTION_ENABLED, enabled).apply();
    }

    @NonNull
    private static SharedPreferences getPreferences(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
