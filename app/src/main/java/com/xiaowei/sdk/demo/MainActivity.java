package com.xiaowei.sdk.demo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowei.sdk.sessioncore.AudioPreprocessStatus;
import com.xiaowei.sdk.sessioncore.AssistantSentenceEvent;
import com.xiaowei.sdk.sessioncore.PcmFrame;
import com.xiaowei.sdk.sessioncore.SessionConfig;
import com.xiaowei.sdk.sessioncore.SessionEventListener;
import com.xiaowei.sdk.sessioncore.SessionState;
import com.xiaowei.sdk.sessioncore.SessionStateEvent;
import com.xiaowei.sdk.sessioncore.SessionTool;
import com.xiaowei.sdk.sessioncore.ToolInvocationEvent;
import com.xiaowei.sdk.sessioncore.UserInputCommittedEvent;
import com.xiaowei.sdk.sessioncore.XiaoweiSessionClient;
import com.xiaowei.sdk.sessioncore.XiaoweiSessionClients;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SDK 接入示例 Demo 主页面。
 * 主窗口只保留连接动作、状态摘要、日志面板；文本输入改为弹框，所有事件统一写入日志。
 */
public class MainActivity extends AppCompatActivity {
    private static final String LOGCAT_TAG = "XWSDKDemo";
    private static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 1001;
    private static final boolean ENABLE_ASSISTANT_PCM_PLAYBACK = true;
    private static final String EMPTY_TOOL_INPUT_SCHEMA_JSON = "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
    private static final boolean LOG_ASSISTANT_PCM_FRAMES = false;
    private static final String SOUL_ID_CHINESE_FEMALE = "soul_demo_chinese_female_chat_assistant_v1";
    private static final String SOUL_ID_CHINESE_MALE = "soul_demo_chinese_male_chat_assistant_v1";
    private static final String SOUL_ID_JAPANESE_FEMALE = "soul_demo_japanese_female_chat_assistant_v1";
    private static final String SOUL_ID_JAPANESE_MALE = "soul_demo_japanese_male_chat_assistant_v1";
    private static final SoulProfile[] BUILT_IN_SOUL_PROFILES = new SoulProfile[]{
            new SoulProfile(R.string.soul_option_chinese_female, SOUL_ID_CHINESE_FEMALE),
            new SoulProfile(R.string.soul_option_chinese_male, SOUL_ID_CHINESE_MALE),
            new SoulProfile(R.string.soul_option_japanese_female, SOUL_ID_JAPANESE_FEMALE),
            new SoulProfile(R.string.soul_option_japanese_male, SOUL_ID_JAPANESE_MALE)
    };
    private static final String[] DEMO_LANGUAGES = new String[]{AppPrefs.DEMO_LANGUAGE_ZH, AppPrefs.DEMO_LANGUAGE_JA};

    // 所有会话动作都串行提交，避免多按钮并发触发状态竞争。
    private final ExecutorService sessionExecutor = Executors.newSingleThreadExecutor();
    // Demo 通过 SDK 对外工厂获取客户端，避免依赖具体实现类。
    private final XiaoweiSessionClient sessionClient = XiaoweiSessionClients.create();

    // Demo 宿主负责正式的下行 PCM 播放链路，SDK 只负责回调统一 PcmFrame。
    private AssistantPcmPlayer assistantPcmPlayer;
    private String assistantPlaybackStrategyPreference;

    // 按文档建议，Demo 在单次连接内用简单自增数字生成 client_input_id。
    private int nextClientInputSequence = 1;

    private LinearLayout languageButtonContainer;
    private ImageButton languageButton;
    private ImageButton openSettingsButton;
    private Button connectButton;
    private Button listenButton;
    private Button sendTextButton;
    private Spinner soulSelectorSpinner;
    private ImageButton clearLogsButton;
    private LottieAnimationView expressionAnimationView;
    private TextView sdkInfoLabelText;
    private TextView sdkInfoValueText;
    private TextView languageValueText;
    private TextView soulSelectorLabelText;
    private TextView logsPanelTitleText;
    private TextView logsText;
    private ScrollView logsScrollView;
    private boolean listening;
    private boolean listenActionRunning;
    private boolean suppressSoulSelectorCallback;
    private SessionState currentSessionState = SessionState.DISCONNECTED;

    /**
     * 初始化页面、绑定会话监听，并渲染初始状态。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_main);
        ensureAssistantPcmPlayerConfigured(false);
        bindViews();
        bindSoulSelector();
        bindSessionListener();
        registerMcpTools();
        bindActions();
        renderIdleState();
        applyDemoLanguageTexts();
        renderSdkInfo();
        renderStartupAudioPreprocessPreview();
        requestRecordAudioPermissionIfNeeded();
        appendLog("[Demo] playback_enabled=" + ENABLE_ASSISTANT_PCM_PLAYBACK
                + " permission_auto_request=true"
                + " audio_usage=USAGE_MEDIA"
                + " playback_strategy=" + AppPrefs.describeTtsPlaybackStrategy(AppPrefs.getTtsPlaybackStrategyPreference(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureAssistantPcmPlayerConfigured(true);
        syncSoulSelectorSelection();
        applyDemoLanguageTexts();
    }

    /**
     * 页面销毁时显式断开会话并停止后台线程。
     */
    @Override
    protected void onDestroy() {
        assistantPlaybackStrategyPreference = null;
        sessionExecutor.execute(sessionClient::disconnect);
        if (assistantPcmPlayer != null) {
            assistantPcmPlayer.release();
            assistantPcmPlayer = null;
        }
        sessionExecutor.shutdownNow();
        super.onDestroy();
    }

    /**
     * 处理麦克风运行时权限结果；这里只更新权限状态，不自动开始收音。
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            appendLog("[Permission] RECORD_AUDIO 被拒绝，无法开始监听");
            return;
        }
        appendLog("[Permission] RECORD_AUDIO 已授权");
        updateActionButtons(currentSessionState);
    }

    /**
     * 绑定主页面控件引用。
     */
    private void bindViews() {
        languageButtonContainer = findViewById(R.id.button_language_container);
        languageButton = findViewById(R.id.button_language);
        openSettingsButton = findViewById(R.id.button_open_settings);
        connectButton = findViewById(R.id.button_connect);
        listenButton = findViewById(R.id.button_listen);
        sendTextButton = findViewById(R.id.button_send_text);
        soulSelectorSpinner = findViewById(R.id.spinner_soul_selector);
        clearLogsButton = findViewById(R.id.button_clear_logs);
        expressionAnimationView = findViewById(R.id.view_expression_animation);
        sdkInfoLabelText = findViewById(R.id.text_sdk_label);
        sdkInfoValueText = findViewById(R.id.text_sdk_value);
        languageValueText = findViewById(R.id.text_language_value);
        soulSelectorLabelText = findViewById(R.id.text_soul_selector_label);
        logsPanelTitleText = findViewById(R.id.text_logs_panel_title);
        logsText = findViewById(R.id.text_logs);
        logsScrollView = findViewById(R.id.scroll_logs);
    }

    /**
     * 绑定主页面内置角色下拉框，方便公开试用时快速切换四个默认元神。
     */
    private void bindSoulSelector() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                buildSoulProfileLabels()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        soulSelectorSpinner.setAdapter(adapter);
        soulSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (suppressSoulSelectorCallback) {
                    return;
                }
                applySoulSelection(position, true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Spinner 始终有默认项，这里无需额外处理。
            }
        });
        syncSoulSelectorSelection();
    }

    /**
     * 绑定 SDK 监听器，把状态变化和 committed input 统一映射到日志面板。
     */
    private void bindSessionListener() {
        sessionClient.setListener(new SessionEventListener() {
            @Override
            public void onSessionStateChanged(SessionStateEvent event) {
                SessionState state = event.getState();
                boolean disconnected = state == SessionState.DISCONNECTED;

                if (disconnected) {
                    resetClientInputSequence();
                    resetListenState();
                    stopAssistantPlayback();
                }
                updateActionButtons(state);
                appendLog("[Session] " + event);
            }

            @Override
            public void onUserInputCommitted(UserInputCommittedEvent event) {
                String source = safe(event.getSource());
                String text = safe(event.getText());
                if ("asr".equals(source)) {
                    appendLog("[语音识别] " + text);
                    return;
                }
                appendLog("[文本已提交] " + text);
            }

            @Override
            public void onAudioPreprocessStatusChanged(AudioPreprocessStatus status) {
                appendLog(formatAudioPreprocessLog(status));
            }

            @Override
            public void onAssistantSentence(AssistantSentenceEvent event) {
                String state = safe(event.getState());
                if (AssistantSentenceEvent.STATE_START.equals(state)) {
                    appendLog("[服务端] [下发] 开始下发"
                            + " index=" + displayValue(event.getIndex())
                            + " turnId=" + displayValue(event.getTurnId())
                            + " responseId=" + displayValue(event.getResponseId())
                            + " text=" + safe(event.getText()));
                    return;
                }
                appendLog("[服务端] [下发] 下发结束"
                        + " turnId=" + displayValue(event.getTurnId())
                        + " responseId=" + displayValue(event.getResponseId()));
            }

            @Override
            public void onToolInvocation(@NonNull ToolInvocationEvent event) {
                String state = safe(event.getState());
                String toolName = safe(event.getToolName());
                if (ToolInvocationEvent.STATE_START.equals(state)) {
                    appendLog("[MCP] [调用开始] tool=" + toolName + " args=" + safe(event.getArgumentsJson()));
                    return;
                }
                if (ToolInvocationEvent.STATE_SUCCESS.equals(state)) {
                    appendLog("[MCP] [调用成功] tool=" + toolName + " result=" + safe(event.getResultText()));
                    return;
                }
                appendLog("[MCP] [调用失败] tool=" + toolName + " error=" + safe(event.getErrorMessage()));
            }

            @Override
            public void onAssistantPcm(PcmFrame frame) {
                AssistantPcmPlayer player = assistantPcmPlayer;
                if (ENABLE_ASSISTANT_PCM_PLAYBACK && player != null) {
                    player.play(frame);
                }
                if (LOG_ASSISTANT_PCM_FRAMES) {
                    appendLog("[AssistantPcm] seq=" + frame.getSeq()
                            + " ptsUs=" + frame.getPtsUs()
                            + " sampleRate=" + frame.getSampleRateHz()
                            + " channels=" + frame.getChannels()
                            + " samplesPerChannel=" + frame.getSamplesPerChannel()
                            + " responseId=" + safe(frame.getResponseId()));
                }
            }
        });
    }

    /**
     * 绑定页面按钮行为。
     */
    private void bindActions() {
        languageButtonContainer.setOnClickListener(v -> showLanguageDialog());
        openSettingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        connectButton.setOnClickListener(v -> {
            if (currentSessionState == SessionState.DISCONNECTED) {
                resetClientInputSequence();
                resetListenState();
                appendLog("[UI] 点击 CONNECT");
                updateActionButtons(SessionState.CONNECTING);
                sessionExecutor.execute(this::runConnect);
                return;
            }
            if (currentSessionState == SessionState.CONNECTED) {
                appendLog("[UI] 点击 DISCONNECT");
                sessionExecutor.execute(sessionClient::disconnect);
            }
        });

        listenButton.setOnClickListener(v -> onListenButtonClicked());
        sendTextButton.setOnClickListener(v -> showSendTextDialog());
        clearLogsButton.setOnClickListener(v -> clearLogs());
    }

    /**
     * 返回四个内置元神的展示名称。
     */
    @NonNull
    private String[] buildSoulProfileLabels() {
        String[] labels = new String[BUILT_IN_SOUL_PROFILES.length];
        String demoLanguage = AppPrefs.getDemoLanguage(this);
        for (int index = 0; index < BUILT_IN_SOUL_PROFILES.length; index++) {
            labels[index] = getLocalizedText(BUILT_IN_SOUL_PROFILES[index].labelResId, BUILT_IN_SOUL_PROFILES[index].labelResId, demoLanguage);
        }
        return labels;
    }

    /**
     * 弹出主页面语言切换对话框；当前只支持中文和日文。
     */
    private void showLanguageDialog() {
        String currentLanguage = AppPrefs.getDemoLanguage(this);
        int checkedIndex = AppPrefs.DEMO_LANGUAGE_JA.equals(currentLanguage) ? 1 : 0;
        String[] labels = new String[]{getString(R.string.language_option_zh), getString(R.string.language_option_ja)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_dialog_title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    applyDemoLanguage(DEMO_LANGUAGES[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 应用主页面展示语言并刷新控件文案。
     */
    private void applyDemoLanguage(@NonNull String language) {
        String normalized = AppPrefs.DEMO_LANGUAGE_JA.equals(language) ? AppPrefs.DEMO_LANGUAGE_JA : AppPrefs.DEMO_LANGUAGE_ZH;
        if (!TextUtils.equals(AppPrefs.getDemoLanguage(this), normalized)) {
            AppPrefs.setDemoLanguage(this, normalized);
        }
        applyDemoLanguageTexts();
    }

    /**
     * 刷新主页面所有跟语言切换有关的文案，不改动日志正文和设置页。
     */
    private void applyDemoLanguageTexts() {
        String demoLanguage = AppPrefs.getDemoLanguage(this);
        String languageLabel = getLocalizedText(R.string.language_value_zh, R.string.language_value_ja, demoLanguage);
        languageButtonContainer.setContentDescription(getString(R.string.language_switcher) + "：" + languageLabel);
        languageButton.setContentDescription(getString(R.string.language_switcher));
        languageValueText.setText(languageLabel);
        openSettingsButton.setContentDescription(getString(R.string.settings));
        sdkInfoLabelText.setText(getLocalizedText(R.string.sdk_info_label, R.string.sdk_info_label_ja, demoLanguage));
        soulSelectorLabelText.setText(getLocalizedText(R.string.soul_selector_label, R.string.soul_selector_label_ja, demoLanguage));
        logsPanelTitleText.setText(getLocalizedText(R.string.logs_panel, R.string.logs_panel_ja, demoLanguage));
        clearLogsButton.setContentDescription(getLocalizedText(R.string.clear_logs, R.string.clear_logs_ja, demoLanguage));
        if (TextUtils.equals(logsText.getText(), getString(R.string.logs_empty))
                || TextUtils.equals(logsText.getText(), getString(R.string.logs_empty_ja))) {
            logsText.setText(getLocalizedText(R.string.logs_empty, R.string.logs_empty_ja, demoLanguage));
        }
        refreshSoulSelectorLabels();
        renderSdkInfo();
        updateActionButtons(currentSessionState);
    }

    /**
     * 刷新角色下拉项文案，同时保持当前选中项不变。
     */
    private void refreshSoulSelectorLabels() {
        int selectedIndex = soulSelectorSpinner.getSelectedItemPosition();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                buildSoulProfileLabels()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        soulSelectorSpinner.setAdapter(adapter);
        suppressSoulSelectorCallback = true;
        soulSelectorSpinner.setSelection(Math.max(selectedIndex, 0), false);
        suppressSoulSelectorCallback = false;
    }

    /**
     * 让主页面下拉框和当前持久化的 soul_id 保持一致；遇到非内置值时回退到默认中文女生。
     */
    private void syncSoulSelectorSelection() {
        int selectedIndex = findSoulProfileIndex(AppPrefs.getSoulId(this));
        boolean shouldPersistSelection = selectedIndex < 0;
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        suppressSoulSelectorCallback = true;
        soulSelectorSpinner.setSelection(selectedIndex, false);
        suppressSoulSelectorCallback = false;
        applySoulSelection(selectedIndex, shouldPersistSelection);
    }

    /**
     * 应用当前选中的内置元神；需要持久化时同步写回 AppPrefs，保证后续 Connect 使用同一角色。
     */
    private void applySoulSelection(int index, boolean persistSelection) {
        if (index < 0 || index >= BUILT_IN_SOUL_PROFILES.length) {
            return;
        }
        SoulProfile soulProfile = BUILT_IN_SOUL_PROFILES[index];
        if (!persistSelection) {
            return;
        }
        if (TextUtils.equals(AppPrefs.getSoulId(this), soulProfile.soulId)) {
            return;
        }
        AppPrefs.setSoulId(this, soulProfile.soulId);
        appendLog("[UI] 当前角色=" + getString(soulProfile.labelResId) + " soulId=" + soulProfile.soulId);
    }

    /**
     * 当前角色是否为日文角色；主页面示例文本和提示文案会跟随语言切换。
     */
    private boolean isCurrentSoulJapanese() {
        int selectedIndex = soulSelectorSpinner.getSelectedItemPosition();
        if (selectedIndex < 0 || selectedIndex >= BUILT_IN_SOUL_PROFILES.length) {
            selectedIndex = findSoulProfileIndex(AppPrefs.getSoulId(this));
        }
        return selectedIndex == 2 || selectedIndex == 3;
    }

    /**
     * 在四个内置元神中查找当前 soul_id 对应的下标。
     */
    private int findSoulProfileIndex(@NonNull String soulId) {
        for (int index = 0; index < BUILT_IN_SOUL_PROFILES.length; index++) {
            if (TextUtils.equals(BUILT_IN_SOUL_PROFILES[index].soulId, soulId)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 注册 Demo 当前可供服务端调用的最小 MCP 工具集合。
     */
    private void registerMcpTools() {
        sessionClient.registerTool(new SessionTool() {
            @NonNull
            @Override
            public String getName() {
                return "show_expression";
            }

            @NonNull
            @Override
            public String getDescription() {
                return "显示普通表情动画，支持 happy/cry/cold 三种参数。";
            }

            @NonNull
            @Override
            public String getInputSchemaJson() {
                return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"enum\":[\"happy\",\"cry\",\"cold\"]}},\"required\":[\"name\"],\"additionalProperties\":false}";
            }

            @Override
            public String invoke(@NonNull String argumentsJson) {
                String expressionName = parseBasicExpressionName(argumentsJson);
                renderExpression(expressionName);
                return "emoji displayed: " + expressionName;
            }
        });
        registerFixedExpressionTool("show_dance", "显示跳舞动画，适用于活跃气氛、庆祝、表演等场景。", "dance");
        registerFixedExpressionTool("show_monkey", "显示猴子搞怪动画，适用于调皮、卖萌、整活等场景。", "monkey");
        registerFixedExpressionTool("go_idle", "让角色回到默认待机状态，不再展示任何表情或动作动画。适用于结束表情、恢复正常、回到默认状态等场景。", null);
        sessionClient.registerTool(new SessionTool() {
            @NonNull
            @Override
            public String getName() {
                return "return_to_idle";
            }

            @NonNull
            @Override
            public String getDescription() {
                return "让角色回到默认待机状态，不再展示任何表情或动作动画。适用于结束表情、恢复正常、回到默认状态等场景。";
            }

            @NonNull
            @Override
            public String getInputSchemaJson() {
                return EMPTY_TOOL_INPUT_SCHEMA_JSON;
            }

            @Override
            public String invoke(@NonNull String argumentsJson) {
                clearExpression();
                return "returned to idle";
            }
        });
    }

    /**
     * 解析基础表情工具参数，只接受 happy/cry/cold。
     */
    @NonNull
    private String parseBasicExpressionName(@NonNull String argumentsJson) {
        try {
            JsonObject root = JsonParser.parseString(argumentsJson).getAsJsonObject();
            String name = safe(root.has("name") && !root.get("name").isJsonNull() ? root.get("name").getAsString() : "").trim().toLowerCase(Locale.ROOT);
            if ("happy".equals(name) || "cry".equals(name) || "cold".equals(name)) {
                return name;
            }
            throw new IllegalArgumentException("unsupported expression: " + name);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid arguments: " + e.getMessage(), e);
        }
    }

    /**
     * 注册一个无参的固定动画工具。
     */
    private void registerFixedExpressionTool(@NonNull String toolName, @NonNull String description, @NonNull String expressionName) {
        sessionClient.registerTool(new SessionTool() {
            @NonNull
            @Override
            public String getName() {
                return toolName;
            }

            @NonNull
            @Override
            public String getDescription() {
                return description;
            }

            @NonNull
            @Override
            public String getInputSchemaJson() {
                return EMPTY_TOOL_INPUT_SCHEMA_JSON;
            }

            @Override
            public String invoke(@NonNull String argumentsJson) {
                if (expressionName == null) {
                    clearExpression();
                    return "returned to idle";
                }
                renderExpression(expressionName);
                return "emoji displayed: " + expressionName;
            }
        });
    }

    /**
     * 在主页面播放指定表情动画；直接复用 mcpEmoji 下的 Lottie JSON 资源。
     */
    private void renderExpression(@NonNull String expressionName) {
        runOnUiThread(() -> {
            expressionAnimationView.cancelAnimation();
            expressionAnimationView.setVisibility(android.view.View.VISIBLE);
            expressionAnimationView.setScaleX(resolveExpressionScale(expressionName));
            expressionAnimationView.setScaleY(resolveExpressionScale(expressionName));
            expressionAnimationView.setAnimation(resolveExpressionAssetFile(expressionName));
            expressionAnimationView.setRepeatCount(LottieDrawable.INFINITE);
            expressionAnimationView.playAnimation();
        });
    }

    /**
     * 清空当前动画展示区域。
     */
    private void clearExpression() {
        runOnUiThread(() -> {
            expressionAnimationView.cancelAnimation();
            expressionAnimationView.setScaleX(1f);
            expressionAnimationView.setScaleY(1f);
            expressionAnimationView.setVisibility(android.view.View.GONE);
        });
    }

    private float resolveExpressionScale(@NonNull String expressionName) {
        switch (expressionName) {
            case "monkey":
            case "snail":
                return 1.18f;
            default:
                return 1f;
        }
    }

    @NonNull
    private String resolveExpressionAssetFile(@NonNull String expressionName) {
        switch (expressionName) {
            case "happy":
                return "mcpEmoji/gao_xing.json";
            case "cry":
                return "mcpEmoji/ku_nao.json";
            case "cold":
                return "mcpEmoji/han_leng.json";
            case "dance":
                return "mcpEmoji/dance.json";
            case "monkey":
                return "mcpEmoji/monkey.json";
            default:
                throw new IllegalArgumentException("unsupported expression: " + expressionName);
        }
    }

    /**
     * 渲染当前 Demo 消费的 SDK 名称与版本，方便确认当前产物来源。
     */
    private void renderSdkInfo() {
        sdkInfoValueText.setText(getString(
                R.string.sdk_info_value_format,
                displayValue(BuildConfig.SDK_ARTIFACT_NAME),
                displayValue(BuildConfig.SDK_VERSION_NAME)
        ));
    }

    /**
     * 在后台线程中读取 AppPrefs，组装 provider/config，并发起 connect()。
     */
    private void runConnect() {
        try {
            AppPrefs.ConnectionSettings settings = AppPrefs.loadConnectionSettings(this);
            DebugOpenApiSessionTokenProvider provider = new DebugOpenApiSessionTokenProvider(
                    settings.openApiBaseUrl,
                    settings.accessKeyId,
                    settings.accessKeySecret,
                    requireNonBlank(settings.integrationAppId, "integration_app_id"),
                    requireNonBlank(settings.soulId, "soul_id"),
                    this::appendLog
            );

            SessionConfig config = new SessionConfig.Builder()
                    .setWsUrl(requireNonBlank(settings.wsUrl, "wsUrl"))
                    .setProtocolVersion(parseRequiredInt(settings.protocolVersion, "protocolVersion"))
                    .setLogicalDeviceId(requireNonBlank(settings.logicalDeviceId, "logicalDeviceId"))
                    .setLogicalClientId(requireNonBlank(settings.logicalClientId, "logicalClientId"))
                    .setSessionTokenProvider(provider)
                    .build();

            appendLog("[Connect] 使用配置 openApiBaseUrl=" + settings.openApiBaseUrl
                    + " wsUrl=" + settings.wsUrl
                    + " protocolVersion=" + settings.protocolVersion
                    + " soulId=" + displayValue(settings.soulId));
            sessionClient.connect(config);
            appendLog("[Connect] connect() 成功！");
        } catch (Exception e) {
            appendLog("[Connect] 失败: " + e.getMessage());
        }
    }

    /**
     * 处理 START LISTEN / STOP LISTEN 点击；录音权限已在页面启动时预先申请。
     */
    private void onListenButtonClicked() {
        if (currentSessionState != SessionState.CONNECTED) {
            appendLog("[Listen] 操作被忽略: 当前未连接");
            return;
        }
        if (listenActionRunning) {
            return;
        }
        if (listening) {
            appendLog("[UI] 点击 STOP LISTEN");
            stopListenNow();
            return;
        }
        if (hasRecordAudioPermission()) {
            appendLog("[UI] 点击 START LISTEN");
            startListenNow();
            return;
        }
        appendLog("[Permission] RECORD_AUDIO 未授权，正在重新请求权限");
        requestRecordAudioPermissionIfNeeded();
    }

    /**
     * 页面启动时预先申请录音权限，避免首次使用时出现二次点击启动收音。
     */
    private void requestRecordAudioPermissionIfNeeded() {
        if (hasRecordAudioPermission()) {
            return;
        }
        appendLog("[Permission] 首次进入页面，预先请求 RECORD_AUDIO 权限");
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
    }

    /**
     * 检查当前是否已经持有 RECORD_AUDIO 权限。
     */
    private boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 在 UI 层进入 start listen 提交流程，并串行切到后台线程执行。
     */
    private void startListenNow() {
        if (currentSessionState != SessionState.CONNECTED || listening || listenActionRunning) {
            return;
        }
        listenActionRunning = true;
        updateActionButtons(currentSessionState);
        sessionExecutor.execute(this::runStartListen);
    }

    /**
     * 在 UI 层进入 stop listen 提交流程，并串行切到后台线程执行。
     */
    private void stopListenNow() {
        if (currentSessionState != SessionState.CONNECTED || !listening || listenActionRunning) {
            return;
        }
        listenActionRunning = true;
        updateActionButtons(currentSessionState);
        sessionExecutor.execute(this::runStopListen);
    }

    /**
     * 在后台线程里启动 realtime listening。
     */
    private void runStartListen() {
        try {
            sessionClient.startRealtimeListen();
            appendLog("[Listen] startRealtimeListen() 成功！");
            finishListenAction(true);
        } catch (Exception e) {
            appendLog("[Listen] 开始失败: " + e.getMessage());
            finishListenAction(false);
        }
    }

    /**
     * 弹出发送文本对话框，在弹框里收集文本和 interrupt 开关。
     */
    private void showSendTextDialog() {
        if (currentSessionState != SessionState.CONNECTED) {
            appendLog("[UI] SEND TEXT 被忽略: 当前未连接");
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(16), padding, 0);

        EditText inputEdit = new EditText(this);
        String demoLanguage = AppPrefs.getDemoLanguage(this);
        inputEdit.setHint(getLocalizedText(R.string.text_input_hint, R.string.text_input_hint_ja, demoLanguage));
        inputEdit.setMinLines(4);
        inputEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        boolean japanese = isCurrentSoulJapanese();
        inputEdit.setText(AppPrefs.getLastSendText(this, japanese));
        container.addView(inputEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        CheckBox interruptCheckBox = new CheckBox(this);
        interruptCheckBox.setChecked(true);
        interruptCheckBox.setText(getLocalizedText(R.string.interrupt_true, R.string.interrupt_true_ja, demoLanguage));
        LinearLayout.LayoutParams checkBoxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        checkBoxParams.topMargin = dp(12);
        container.addView(interruptCheckBox, checkBoxParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getLocalizedText(R.string.text_input, R.string.text_input_ja, demoLanguage))
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getLocalizedText(R.string.send_text, R.string.send_text_ja, demoLanguage), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = inputEdit.getText().toString();
            boolean interrupt = interruptCheckBox.isChecked();
            if (text.trim().isEmpty()) {
                inputEdit.setError(getLocalizedText(R.string.text_input_empty_error, R.string.text_input_empty_error_ja, demoLanguage));
                return;
            }
            AppPrefs.setLastSendText(this, japanese, text);
            String clientInputId = nextClientInputId();
            appendLog("[用户输入] " + text.trim() + " interrupt=" + interrupt + " clientInputId=" + clientInputId);
            sessionExecutor.execute(() -> runSendText(text, interrupt, clientInputId));
            dialog.dismiss();
        }));
        dialog.show();
        inputEdit.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    /**
     * 在后台线程中发送一条文本输入。
     */
    private void runSendText(@NonNull String text, boolean interrupt, @NonNull String clientInputId) {
        AssistantPcmPlayer player = assistantPcmPlayer;
        if (interrupt && player != null) {
            player.interruptAndSuppressCurrentResponse();
        }
        try {
            sessionClient.sendText(text, interrupt, clientInputId);
            appendLog("[文本已发送] " + text.trim() + " clientInputId=" + clientInputId);
        } catch (Exception e) {
            if (interrupt && player != null) {
                player.flushStopAndResetResponseState();
            }
            appendLog("[SendText] 失败: " + e.getMessage());
        }
    }

    /**
     * 在后台线程里结束 realtime listening。
     */
    private void runStopListen() {
        try {
            sessionClient.stopListen();
            appendLog("[Listen] stopListen() 返回成功");
            finishListenAction(false);
        } catch (Exception e) {
            appendLog("[Listen] 停止失败: " + e.getMessage());
            finishListenAction(true);
        }
    }

    /**
     * 收口一次 listening 动作的 UI 状态；如果会话已经不在 CONNECTED，则强制回到非 listening。
     */
    private void finishListenAction(boolean shouldBeListening) {
        runOnUiThread(() -> {
            listening = currentSessionState == SessionState.CONNECTED && shouldBeListening;
            listenActionRunning = false;
            updateActionButtons(currentSessionState);
        });
    }

    /**
     * 重置当前连接内的 client_input_id 序号。
     */
    private void resetClientInputSequence() {
        nextClientInputSequence = 1;
    }

    /**
     * 生成当前连接内下一条文本输入的自增 client_input_id。
     */
    @NonNull
    private String nextClientInputId() {
        String clientInputId = String.valueOf(nextClientInputSequence);
        nextClientInputSequence += 1;
        return clientInputId;
    }

    /**
     * 把主页面渲染成初始空闲状态。
     */
    private void renderIdleState() {
        resetClientInputSequence();
        resetListenState();
        updateActionButtons(SessionState.DISCONNECTED);
    }

    /**
     * 应用启动后先打印一条平台效果器预检结果，便于在未开麦前确认平台声明能力。
     */
    private void renderStartupAudioPreprocessPreview() {
        AudioPreprocessStatus status = sessionClient.getAudioPreprocessPreviewStatus();
        appendLog(formatAudioPreprocessPreviewLog(status));
    }

    @NonNull
    private String formatAudioPreprocessLog(@NonNull AudioPreprocessStatus status) {
        return "[平台效果器] [录音实检] "
                + formatEffectStatus(status.getAcousticEchoCanceler())
                + " | "
                + formatEffectStatus(status.getNoiseSuppressor())
                + " | "
                + formatEffectStatus(status.getAutomaticGainControl());
    }

    @NonNull
    private String formatAudioPreprocessPreviewLog(@NonNull AudioPreprocessStatus status) {
        return "[平台效果器] [启动预检] "
                + formatEffectStatus(status.getAcousticEchoCanceler())
                + " | "
                + formatEffectStatus(status.getNoiseSuppressor())
                + " | "
                + formatEffectStatus(status.getAutomaticGainControl());
    }

    @NonNull
    private String formatEffectStatus(@NonNull AudioPreprocessStatus.EffectStatus effectStatus) {
        return effectStatus.getName() + "：" + effectStatus.getDetail();
    }

    /**
     * 根据当前连接态切换主按钮文案与可用性。
     */
    private void updateActionButtons(@NonNull SessionState state) {
        currentSessionState = state;
        runOnUiThread(() -> {
            String demoLanguage = AppPrefs.getDemoLanguage(this);
            if (state == SessionState.CONNECTED) {
                connectButton.setText(getLocalizedText(R.string.disconnect, R.string.disconnect_ja, demoLanguage));
                connectButton.setEnabled(true);
            } else {
                connectButton.setText(getLocalizedText(R.string.connect, R.string.connect_ja, demoLanguage));
                connectButton.setEnabled(state == SessionState.DISCONNECTED);
            }
            listenButton.setText(listening
                    ? getLocalizedText(R.string.stop_listen, R.string.stop_listen_ja, demoLanguage)
                    : getLocalizedText(R.string.start_listen, R.string.start_listen_ja, demoLanguage));
            listenButton.setEnabled(state == SessionState.CONNECTED && !listenActionRunning);
            sendTextButton.setEnabled(state == SessionState.CONNECTED);
            sendTextButton.setText(getLocalizedText(R.string.send_text, R.string.send_text_ja, demoLanguage));
            soulSelectorSpinner.setEnabled(state == SessionState.DISCONNECTED);
            listenButton.setBackgroundTintList(ContextCompat.getColorStateList(this,
                    listenButton.isEnabled() ? R.color.demo_primary_dark : R.color.demo_button_disabled));
            sendTextButton.setBackgroundTintList(ContextCompat.getColorStateList(this,
                    sendTextButton.isEnabled() ? R.color.demo_primary_dark : R.color.demo_button_disabled));
        });
    }

    /**
     * 重置 listening 相关的本地 UI 状态。
     */
    private void resetListenState() {
        listening = false;
        listenActionRunning = false;
    }

    /**
     * 在会话断开或页面销毁时停止当前 TTS 播放，避免旧会话语音残留。
     */
    private void stopAssistantPlayback() {
        AssistantPcmPlayer player = assistantPcmPlayer;
        if (player != null) {
            player.flushStopAndResetResponseState();
        }
    }

    /**
     * 根据设置页当前配置创建或重建 TTS 播放器，确保切换播放策略后无需重启 Demo。
     */
    private void ensureAssistantPcmPlayerConfigured(boolean allowRecreate) {
        if (!ENABLE_ASSISTANT_PCM_PLAYBACK) {
            return;
        }
        String strategyPreference = AppPrefs.getTtsPlaybackStrategyPreference(this);
        if (assistantPcmPlayer != null && TextUtils.equals(assistantPlaybackStrategyPreference, strategyPreference)) {
            return;
        }
        if (assistantPcmPlayer != null && !allowRecreate) {
            return;
        }
        if (assistantPcmPlayer != null) {
            assistantPcmPlayer.release();
        }
        assistantPcmPlayer = new AssistantPcmPlayer(
                this,
                line -> appendLog(line, true),
                strategyPreference,
                AppPrefs.describeTtsPlaybackStrategy(strategyPreference)
        );
        assistantPlaybackStrategyPreference = strategyPreference;
    }

    /**
     * 追加一条日志到页面日志面板，并自动滚动到最底部。
     */
    private void appendLog(@NonNull String line) {
        appendLog(line, false);
    }

    /**
     * 追加一条日志到页面日志面板；如果该日志此前已写入 logcat，则避免重复打印。
     */
    private void appendLog(@NonNull String line, boolean alreadyLoggedToLogcat) {
        String panelLine = formatLogLine(line, true);
        if (!alreadyLoggedToLogcat) {
            Log.i(LOGCAT_TAG, line);
        }
        runOnUiThread(() -> {
            String current = logsText.getText().toString();
            String emptyValue = getString(R.string.logs_empty);
            if (TextUtils.equals(current, emptyValue)) {
                logsText.setText(panelLine);
            } else {
                logsText.append("\n\n" + panelLine);
            }
            logsScrollView.post(() -> logsScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    /**
     * 清空日志面板内容，并恢复为空态提示。
     */
    private void clearLogs() {
        logsText.setText(getString(R.string.logs_empty));
    }

    /**
     * 给日志面板统一补上时间前缀，方便排查接入问题。
     */
    @NonNull
    private String formatLogLine(@NonNull String line, boolean multiline) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        return multiline ? "[" + timestamp + "]\n" + line : line;
    }

    /**
     * 把 dp 换算成 px，供动态创建对话框控件使用。
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * 校验字符串参数非空。
     */
    @NonNull
    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    /**
     * 把字符串解析成 int，并在错误时抛出明确提示。
     */
    private static int parseRequiredInt(String value, String fieldName) {
        try {
            return Integer.parseInt(requireNonBlank(value, fieldName));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是数字");
        }
    }

    /**
     * 把 null 安全转换成空串，便于日志拼接。
     */
    @NonNull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 把空字符串转换成短横线，便于页面展示摘要。
     */
    @NonNull
    private static String displayValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim();
    }

    /**
     * 把可选数字安全转换成短横线占位，便于日志展示。
     */
    @NonNull
    private static String displayValue(Number value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * 根据当前 Demo 语言返回对应文案。
     */
    @NonNull
    private String getLocalizedText(int zhResId, int jaResId, @NonNull String language) {
        return AppPrefs.DEMO_LANGUAGE_JA.equals(language) ? getString(jaResId) : getString(zhResId);
    }

    /**
     * 主页面内置角色项，只收口展示名和 soul_id 映射。
     */
    private static final class SoulProfile {
        final int labelResId;
        final String soulId;

        SoulProfile(int labelResId, @NonNull String soulId) {
            this.labelResId = labelResId;
            this.soulId = soulId;
        }
    }
}
