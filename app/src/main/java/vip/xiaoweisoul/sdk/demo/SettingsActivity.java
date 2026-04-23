package vip.xiaoweisoul.sdk.demo;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Demo 的独立设置页。
 * 所有 OpenAPI / WebSocket / 会话参数都在这里维护，不再堆在主窗口里。
 */
public class SettingsActivity extends AppCompatActivity {
    private EditText openApiBaseUrlEdit;
    private EditText wsUrlEdit;
    private EditText accessKeyIdEdit;
    private EditText accessKeySecretEdit;
    private EditText integrationAppIdEdit;
    private EditText soulIdEdit;
    private EditText protocolVersionEdit;
    private EditText logicalDeviceIdEdit;
    private EditText logicalClientIdEdit;
    private RadioGroup ttsPlaybackStrategyGroup;
    private Button backButton;
    private Button saveButton;
    private Button restoreDefaultsButton;

    /**
     * 初始化设置页并回填当前保存的配置。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        fillCurrentValues();
        bindActions();
    }

    /**
     * 绑定设置页控件引用。
     */
    private void bindViews() {
        openApiBaseUrlEdit = findViewById(R.id.edit_setting_open_api_base_url);
        wsUrlEdit = findViewById(R.id.edit_setting_ws_url);
        accessKeyIdEdit = findViewById(R.id.edit_setting_access_key_id);
        accessKeySecretEdit = findViewById(R.id.edit_setting_access_key_secret);
        integrationAppIdEdit = findViewById(R.id.edit_setting_integration_app_id);
        soulIdEdit = findViewById(R.id.edit_setting_soul_id);
        protocolVersionEdit = findViewById(R.id.edit_setting_protocol_version);
        logicalDeviceIdEdit = findViewById(R.id.edit_setting_logical_device_id);
        logicalClientIdEdit = findViewById(R.id.edit_setting_logical_client_id);
        ttsPlaybackStrategyGroup = findViewById(R.id.group_tts_playback_strategy);
        backButton = findViewById(R.id.button_settings_back);
        saveButton = findViewById(R.id.button_settings_save);
        restoreDefaultsButton = findViewById(R.id.button_settings_restore_defaults);
    }

    /**
     * 把当前 AppPrefs 中的值回填到表单。
     */
    private void fillCurrentValues() {
        openApiBaseUrlEdit.setText(AppPrefs.getOpenApiBaseUrl(this));
        wsUrlEdit.setText(AppPrefs.getWsUrl(this));
        accessKeyIdEdit.setText(AppPrefs.getAccessKeyId(this));
        accessKeySecretEdit.setText(AppPrefs.getAccessKeySecret(this));
        integrationAppIdEdit.setText(AppPrefs.getIntegrationAppId(this));
        soulIdEdit.setText(AppPrefs.getSoulId(this));
        protocolVersionEdit.setText(AppPrefs.getProtocolVersion(this));
        logicalDeviceIdEdit.setText(AppPrefs.getLogicalDeviceId(this));
        logicalClientIdEdit.setText(AppPrefs.getLogicalClientId(this));
        String strategyPreference = AppPrefs.getTtsPlaybackStrategyPreference(this);
        if (AppPrefs.TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS.equals(strategyPreference)) {
            ttsPlaybackStrategyGroup.check(R.id.radio_tts_playback_strategy_pause_others);
        } else if (AppPrefs.TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS.equals(strategyPreference)) {
            ttsPlaybackStrategyGroup.check(R.id.radio_tts_playback_strategy_mix_with_others);
        } else {
            ttsPlaybackStrategyGroup.check(R.id.radio_tts_playback_strategy_duck_others);
        }
    }

    /**
     * 绑定保存、恢复默认和返回动作。
     */
    private void bindActions() {
        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveSettings());
        restoreDefaultsButton.setOnClickListener(v -> restoreDefaults());
    }

    /**
     * 保存当前页面中的设置参数。
     */
    private void saveSettings() {
        AppPrefs.setOpenApiBaseUrl(this, openApiBaseUrlEdit.getText().toString());
        AppPrefs.setWsUrl(this, wsUrlEdit.getText().toString());
        AppPrefs.setAccessKeyId(this, accessKeyIdEdit.getText().toString());
        AppPrefs.setAccessKeySecret(this, accessKeySecretEdit.getText().toString());
        AppPrefs.setIntegrationAppId(this, integrationAppIdEdit.getText().toString());
        AppPrefs.setSoulId(this, soulIdEdit.getText().toString());
        AppPrefs.setProtocolVersion(this, protocolVersionEdit.getText().toString());
        AppPrefs.setLogicalDeviceId(this, logicalDeviceIdEdit.getText().toString());
        AppPrefs.setLogicalClientId(this, logicalClientIdEdit.getText().toString());
        int selectedStrategyId = ttsPlaybackStrategyGroup.getCheckedRadioButtonId();
        String strategyPreference;
        if (selectedStrategyId == R.id.radio_tts_playback_strategy_pause_others) {
            strategyPreference = AppPrefs.TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS;
        } else if (selectedStrategyId == R.id.radio_tts_playback_strategy_mix_with_others) {
            strategyPreference = AppPrefs.TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS;
        } else {
            strategyPreference = AppPrefs.TTS_PLAYBACK_STRATEGY_DUCK_OTHERS;
        }
        AppPrefs.setTtsPlaybackStrategyPreference(this, strategyPreference);
        showToast("设置已保存");
        finish();
    }

    /**
     * 把连接设置恢复到 AppPrefs 中定义的公开默认值。
     */
    private void restoreDefaults() {
        AppPrefs.resetConnectionSettings(this);
        fillCurrentValues();
        showToast("已恢复默认配置");
    }

    /**
     * 在主线程显示一条提示。
     */
    private void showToast(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
