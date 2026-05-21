package vip.xiaoweisoul.sdk.demo;

import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import vip.xiaoweisoul.sdk.sessioncore.AudioPreprocessStatus;
import vip.xiaoweisoul.sdk.sessioncore.AssistantSentenceEvent;
import vip.xiaoweisoul.sdk.sessioncore.ListeningMode;
import vip.xiaoweisoul.sdk.sessioncore.PcmFrame;
import vip.xiaoweisoul.sdk.sessioncore.SessionConfig;
import vip.xiaoweisoul.sdk.sessioncore.SessionEventListener;
import vip.xiaoweisoul.sdk.sessioncore.SessionState;
import vip.xiaoweisoul.sdk.sessioncore.SessionStateEvent;
import vip.xiaoweisoul.sdk.sessioncore.SessionTool;
import vip.xiaoweisoul.sdk.sessioncore.ToolInvocationEvent;
import vip.xiaoweisoul.sdk.sessioncore.UserInputCommittedEvent;
import vip.xiaoweisoul.sdk.sessioncore.XiaoweiSessionClient;
import vip.xiaoweisoul.sdk.sessioncore.XiaoweiSessionClients;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final String[] DEMO_LANGUAGES = new String[]{AppPrefs.DEMO_LANGUAGE_ZH, AppPrefs.DEMO_LANGUAGE_JA};
    // Demo 直接在代码中演示 hello.session_config.idle_timeout_ms；设为 null 表示本次握手不上报该字段，如果您要设置的话建议最低不小于3分钟（180000）
    private static final Integer DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS = 60 * 1000;
    private static final long MANUAL_PANEL_EXPAND_DURATION_MS = 220L;
    private static final long MANUAL_PANEL_COLLAPSE_DURATION_MS = 150L;
    private static final float MANUAL_PANEL_OVERLAY_ALPHA = 0.72f;
    private static final int MANUAL_PANEL_IDLE_HEIGHT_DP = 56;
    private static final int MANUAL_PANEL_EXPANDED_HEIGHT_DP = 120;
    private static final int MANUAL_PANEL_IDLE_SIDE_MARGIN_DP = 16;
    private static final int MANUAL_PANEL_IDLE_BOTTOM_MARGIN_DP = 16;
    private static final int MANUAL_PANEL_IDLE_CORNER_DP = 4;
    private static final int MANUAL_PANEL_EXPANDED_TOP_CORNER_DP = 80;
    private static final int MANUAL_PANEL_CONTENT_BOTTOM_PADDING_DP = 72;
    private static final int MANUAL_PANEL_ACTIVE_TEXT_OFFSET_DP = 12;

    // 所有会话动作都串行提交，避免多按钮并发触发状态竞争。
    private final ExecutorService sessionExecutor = Executors.newSingleThreadExecutor();
    // 角色列表初始化走独立线程，避免 OpenAPI 慢请求阻塞 connect/disconnect。
    private final ExecutorService soulProfileExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger soulProfileLoadGeneration = new AtomicInteger();
    // Demo 通过 SDK 对外工厂获取客户端，避免依赖具体实现类。
    private final XiaoweiSessionClient sessionClient = XiaoweiSessionClients.create();
    // Demo 额外聚合多句 AI 文本，便于公开示例里展示一轮 response 的完整收口。
    private final AssistantResponseTracker assistantResponseTracker = new AssistantResponseTracker();

    // Demo 宿主负责正式的下行 PCM 播放链路，SDK 只负责回调统一 PcmFrame。
    private AssistantPcmPlayer assistantPcmPlayer;
    private String assistantPlaybackStrategyPreference;

    // 按文档建议，Demo 在单次连接内用简单自增数字生成 client_input_id。
    private int nextClientInputSequence = 1;
    private final FastOutSlowInInterpolator manualPanelInterpolator = new FastOutSlowInInterpolator();

    private LinearLayout languageButtonContainer;
    private ImageButton languageButton;
    private ImageButton openSettingsButton;
    private LinearLayout mainContentLayout;
    private Button connectButton;
    private Button listenButton;
    private Button sendTextButton;
    private View manualOverlayView;
    private FrameLayout manualListenPanel;
    private LinearLayout manualListenIdleContent;
    private LinearLayout manualListenActiveContent;
    private ImageView manualListenIdleIcon;
    private ImageView manualListenPressedIcon;
    private TextView manualListenIdleText;
    private TextView manualListenReleaseText;
    private LinearLayout voiceModeSectionLayout;
    private TextView voiceModeLabelText;
    private RadioGroup voiceModeGroup;
    private RadioButton voiceModeManualRadio;
    private RadioButton voiceModeRealtimeRadio;
    private TextView voiceModeSummaryText;
    private LinearLayout soulSelectorSectionLayout;
    private Spinner soulSelectorSpinner;
    private ImageButton clearLogsButton;
    private LottieAnimationView expressionAnimationView;
    private TextView sdkInfoLabelText;
    private TextView sdkInfoValueText;
    private TextView languageValueText;
    private LinearLayout sessionPromptSectionLayout;
    private TextView soulSelectorLabelText;
    private TextView sessionPromptLabelText;
    private CheckBox sessionPromptEnabledCheckBox;
    private Button editSessionPromptButton;
    private TextView sessionPromptSummaryText;
    private TextView logsPanelTitleText;
    private TextView logsText;
    private ScrollView logsScrollView;
    private MaterialShapeDrawable manualListenPanelBackground;
    private ValueAnimator manualListenPanelAnimator;
    private float manualListenPanelProgress;
    private int mainContentBaseBottomPadding;
    private boolean listening;
    private boolean listenActionRunning;
    private boolean suppressSoulSelectorCallback;
    private boolean suppressVoiceModeCallback;
    private boolean soulProfilesLoading;
    private boolean manualPressing;
    private boolean manualStopPendingAfterStart;
    private boolean manualAwaitingCommit;
    private boolean assistantSpeaking;
    private final List<SoulProfileOption> soulProfiles = new ArrayList<>();
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
        setupManualListenPanel();
        bindSoulSelector();
        bindSessionListener();
        registerMcpTools();
        bindActions();
        renderIdleState();
        applyDemoLanguageTexts();
        renderSdkInfo();
        renderStartupAudioPreprocessPreview();
        renderOutputLifecycleGuidance();
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
        if (currentSessionState == SessionState.DISCONNECTED) {
            refreshSoulProfilesFromOpenApi();
        } else {
            syncSoulSelectorSelection();
        }
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
        if (manualListenPanelAnimator != null) {
            manualListenPanelAnimator.cancel();
            manualListenPanelAnimator = null;
        }
        sessionExecutor.shutdownNow();
        soulProfileExecutor.shutdownNow();
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
        mainContentLayout = findViewById(R.id.layout_main_content);
        connectButton = findViewById(R.id.button_connect);
        listenButton = findViewById(R.id.button_listen);
        sendTextButton = findViewById(R.id.button_send_text);
        manualOverlayView = findViewById(R.id.view_manual_overlay);
        manualListenPanel = findViewById(R.id.layout_manual_listen_panel);
        manualListenIdleContent = findViewById(R.id.layout_manual_listen_idle_content);
        manualListenActiveContent = findViewById(R.id.layout_manual_listen_active_content);
        manualListenIdleIcon = findViewById(R.id.image_manual_listen_icon);
        manualListenPressedIcon = findViewById(R.id.image_manual_listen_pressed_icon);
        manualListenIdleText = findViewById(R.id.text_manual_listen_idle);
        manualListenReleaseText = findViewById(R.id.text_manual_listen_release);
        voiceModeSectionLayout = findViewById(R.id.layout_voice_mode_section);
        voiceModeLabelText = findViewById(R.id.text_voice_mode_label);
        voiceModeGroup = findViewById(R.id.group_voice_mode);
        voiceModeManualRadio = findViewById(R.id.radio_voice_mode_manual);
        voiceModeRealtimeRadio = findViewById(R.id.radio_voice_mode_realtime);
        voiceModeSummaryText = findViewById(R.id.text_voice_mode_summary);
        soulSelectorSectionLayout = findViewById(R.id.layout_soul_selector_section);
        soulSelectorSpinner = findViewById(R.id.spinner_soul_selector);
        clearLogsButton = findViewById(R.id.button_clear_logs);
        expressionAnimationView = findViewById(R.id.view_expression_animation);
        sdkInfoLabelText = findViewById(R.id.text_sdk_label);
        sdkInfoValueText = findViewById(R.id.text_sdk_value);
        languageValueText = findViewById(R.id.text_language_value);
        sessionPromptSectionLayout = findViewById(R.id.layout_session_prompt_section);
        soulSelectorLabelText = findViewById(R.id.text_soul_selector_label);
        sessionPromptLabelText = findViewById(R.id.text_session_prompt_label);
        sessionPromptEnabledCheckBox = findViewById(R.id.checkbox_session_prompt_enabled);
        editSessionPromptButton = findViewById(R.id.button_edit_session_prompt);
        sessionPromptSummaryText = findViewById(R.id.text_session_prompt_summary);
        logsPanelTitleText = findViewById(R.id.text_logs_panel_title);
        logsText = findViewById(R.id.text_logs);
        logsScrollView = findViewById(R.id.scroll_logs);
        mainContentBaseBottomPadding = mainContentLayout.getPaddingBottom();
    }

    /**
     * 初始化 Manual 按住说话面板的背景和默认视觉状态。
     */
    private void setupManualListenPanel() {
        manualListenPanelBackground = new MaterialShapeDrawable();
        manualListenPanelBackground.setShadowCompatibilityMode(MaterialShapeDrawable.SHADOW_COMPAT_MODE_NEVER);
        manualListenPanel.setBackground(manualListenPanelBackground);
        manualOverlayView.setOnClickListener(v -> {
            // 蒙版只负责阻断底层交互，不额外触发任何动作。
        });
        syncManualListenPressedIconSize();
        applyManualListenPanelProgress(0f, false);
    }

    /**
     * 绑定主页面角色下拉框；角色项由 OpenAPI 按当前 access_key 动态初始化。
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
                    assistantSpeaking = false;
                    stopAssistantPlayback();
                }
                updateActionButtons(state);
                appendLog("[Session] " + event);
            }

            @Override
            public void onUserInputCommitted(UserInputCommittedEvent event) {
                String source = safe(event.getSource());
                String text = safe(event.getText());
                if (manualAwaitingCommit && "asr".equals(source)) {
                    manualAwaitingCommit = false;
                    updateActionButtons(currentSessionState);
                }
                appendLog("[用户输入已确认]"
                        + " source=" + displayValue(source)
                        + " turnId=" + displayValue(event.getTurnId())
                        + " inputId=" + displayValue(event.getInputId())
                        + " clientInputId=" + displayValue(event.getClientInputId())
                        + " text=" + text);
            }

            @Override
            public void onAudioPreprocessStatusChanged(AudioPreprocessStatus status) {
                appendLog(formatAudioPreprocessLog(status));
            }

            @Override
            public void onAssistantSentence(AssistantSentenceEvent event) {
                String state = safe(event.getState());
                if (AssistantSentenceEvent.STATE_START.equals(state)) {
                    assistantSpeaking = true;
                    AssistantResponseTracker.SentenceSnapshot snapshot = assistantResponseTracker.recordSentenceStart(event);
                    appendLog("[AI文本句子]"
                            + " index=" + displayValue(event.getIndex())
                            + " sentenceCount=" + snapshot.getSentenceCount()
                            + " turnId=" + displayValue(snapshot.getTurnId())
                            + " responseId=" + displayValue(snapshot.getResponseId())
                            + " text=" + snapshot.getLatestText());
                    return;
                }
                AssistantResponseTracker.ResponseSummary summary = assistantResponseTracker.recordSentenceStop(event);
                if (event.isInterruptiveStop()) {
                    AssistantPcmPlayer player = assistantPcmPlayer;
                    if (player != null) {
                        player.interruptAndSuppressResponseFromServer(event.getResponseId(), event.getStopReason());
                    }
                }
                assistantSpeaking = false;
                appendLog("[AI回复结束]"
                        + " turnId=" + displayValue(event.getTurnId())
                        + " responseId=" + displayValue(event.getResponseId())
                        + " reason=" + displayValue(event.getStopReason())
                        + " interruptive=" + event.isInterruptiveStop()
                        + " stage=服务端收口（不代表本地已播完）");
                if (summary.hasText()) {
                    appendLog("[AI回复汇总]"
                            + " turnId=" + displayValue(summary.getTurnId())
                            + " responseId=" + displayValue(summary.getResponseId())
                            + " sentenceCount=" + summary.getSentenceCount()
                            + " text=" + summary.getTextPreview());
                }
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
                AssistantResponseTracker.PcmObservation observation = assistantResponseTracker.observePcm(frame);
                if (observation.isFirstFrame()) {
                    appendLog("[PCM下发]"
                            + " firstFrame=true"
                            + " turnId=" + displayValue(observation.getTurnId())
                            + " responseId=" + displayValue(observation.getResponseId())
                            + " seq=" + observation.getSeq()
                            + " samplesPerChannel=" + observation.getSamplesPerChannel());
                }
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

        listenButton.setOnClickListener(v -> {
            if (isManualVoiceModeSelected()) {
                return;
            }
            onListenButtonClicked();
        });
        manualListenPanel.setOnTouchListener((v, event) -> handleListenButtonTouch(event));
        sendTextButton.setOnClickListener(v -> showSendTextDialog());
        voiceModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (suppressVoiceModeCallback) {
                return;
            }
            if (checkedId == R.id.radio_voice_mode_manual) {
                AppPrefs.setVoiceInputMode(this, AppPrefs.VOICE_INPUT_MODE_MANUAL);
                appendLog("[UI] 当前语音模式=manual");
            } else {
                AppPrefs.setVoiceInputMode(this, AppPrefs.VOICE_INPUT_MODE_REALTIME);
                appendLog("[UI] 当前语音模式=realtime");
            }
            updateActionButtons(currentSessionState);
        });
        sessionPromptEnabledCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setSessionPromptEnabled(this, isChecked);
            renderSessionPromptControls();
        });
        editSessionPromptButton.setOnClickListener(v -> showSessionPromptDialog());
        clearLogsButton.setOnClickListener(v -> clearLogs());
    }

    /**
     * 返回当前角色下拉项的展示名称。
     */
    @NonNull
    private String[] buildSoulProfileLabels() {
        if (soulProfilesLoading) {
            return new String[]{localizedSoulSelectorPlaceholder("角色加载中...", "キャラクターを読み込み中...")};
        }
        if (soulProfiles.isEmpty()) {
            return new String[]{localizedSoulSelectorPlaceholder("暂无可用角色", "利用可能なキャラクターがありません")};
        }
        String[] labels = new String[soulProfiles.size()];
        for (int index = 0; index < soulProfiles.size(); index++) {
            labels[index] = soulProfiles.get(index).displayName();
        }
        return labels;
    }

    /**
     * 根据当前设置中的 access_key 从 OpenAPI 刷新角色列表。
     */
    private void refreshSoulProfilesFromOpenApi() {
        int generation = soulProfileLoadGeneration.incrementAndGet();
        soulProfilesLoading = true;
        soulProfiles.clear();
        refreshSoulSelectorLabels();
        updateActionButtons(currentSessionState);

        soulProfileExecutor.execute(() -> {
            try {
                AppPrefs.ConnectionSettings settings = AppPrefs.loadConnectionSettings(this);
                DebugOpenApiSoulProfileClient client = new DebugOpenApiSoulProfileClient(
                        settings.openApiBaseUrl,
                        settings.accessKeyId,
                        settings.accessKeySecret,
                        this::appendLog
                );
                List<SoulProfileOption> items = client.listSoulProfiles();
                runOnUiThread(() -> applyLoadedSoulProfiles(generation, items, null));
            } catch (Exception e) {
                runOnUiThread(() -> applyLoadedSoulProfiles(generation, new ArrayList<>(), e));
            }
        });
    }

    /**
     * 应用后台加载到的角色列表；过期请求直接丢弃。
     */
    private void applyLoadedSoulProfiles(int generation, @NonNull List<SoulProfileOption> items, Exception error) {
        if (generation != soulProfileLoadGeneration.get()) {
            return;
        }
        soulProfilesLoading = false;
        soulProfiles.clear();
        soulProfiles.addAll(items);
        refreshSoulSelectorLabels();
        if (error != null) {
            appendLog("[SoulProfile] 角色列表加载失败: " + error.getMessage());
        } else {
            appendLog("[SoulProfile] 已加载角色数量=" + soulProfiles.size());
        }
        syncSoulSelectorSelection();
        updateActionButtons(currentSessionState);
    }

    /**
     * 返回角色下拉框加载/空态占位文案。
     */
    @NonNull
    private String localizedSoulSelectorPlaceholder(@NonNull String zhText, @NonNull String jaText) {
        return AppPrefs.DEMO_LANGUAGE_JA.equals(AppPrefs.getDemoLanguage(this)) ? jaText : zhText;
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
        voiceModeLabelText.setText(getLocalizedText(R.string.voice_mode_label, R.string.voice_mode_label_ja, demoLanguage));
        soulSelectorLabelText.setText(getLocalizedText(R.string.soul_selector_label, R.string.soul_selector_label_ja, demoLanguage));
        sessionPromptLabelText.setText(getLocalizedText(R.string.session_prompt_label, R.string.session_prompt_label_ja, demoLanguage));
        logsPanelTitleText.setText(getLocalizedText(R.string.logs_panel, R.string.logs_panel_ja, demoLanguage));
        clearLogsButton.setContentDescription(getLocalizedText(R.string.clear_logs, R.string.clear_logs_ja, demoLanguage));
        if (TextUtils.equals(logsText.getText(), getString(R.string.logs_empty))
                || TextUtils.equals(logsText.getText(), getString(R.string.logs_empty_ja))) {
            logsText.setText(getLocalizedText(R.string.logs_empty, R.string.logs_empty_ja, demoLanguage));
        }
        refreshSoulSelectorLabels();
        renderSdkInfo();
        renderVoiceModeControls();
        renderSessionPromptControls();
        updateActionButtons(currentSessionState);
    }

    /**
     * 刷新语音模式区块，保证主页面展示与当前 SDK 能力、用户选择保持一致。
     */
    private void renderVoiceModeControls() {
        String demoLanguage = AppPrefs.getDemoLanguage(this);
        String voiceMode = getEffectiveVoiceInputMode();
        suppressVoiceModeCallback = true;
        voiceModeRealtimeRadio.setText(getLocalizedText(R.string.voice_mode_realtime, R.string.voice_mode_realtime_ja, demoLanguage));
        voiceModeManualRadio.setText(getLocalizedText(R.string.voice_mode_manual, R.string.voice_mode_manual_ja, demoLanguage));
        voiceModeGroup.check(AppPrefs.VOICE_INPUT_MODE_MANUAL.equals(voiceMode)
                ? R.id.radio_voice_mode_manual
                : R.id.radio_voice_mode_realtime);
        suppressVoiceModeCallback = false;
        voiceModeSummaryText.setText(buildVoiceModeSummary(demoLanguage, voiceMode));
        manualListenIdleText.setText(resolveManualListenButtonText(demoLanguage));
        manualListenReleaseText.setText(getLocalizedText(
                R.string.voice_action_manual_pressing,
                R.string.voice_action_manual_pressing_ja,
                demoLanguage
        ));
    }

    /**
     * 刷新 Session Prompt 相关控件的文案与摘要，让“是否携带”和“当前内容”可见。
     */
    private void renderSessionPromptControls() {
        String demoLanguage = AppPrefs.getDemoLanguage(this);
        boolean enabled = AppPrefs.isSessionPromptEnabled(this);
        String prompt = AppPrefs.getSessionPrompt(this);
        sessionPromptEnabledCheckBox.setText(getLocalizedText(
                R.string.session_prompt_enabled_checkbox,
                R.string.session_prompt_enabled_checkbox_ja,
                demoLanguage
        ));
        sessionPromptEnabledCheckBox.setChecked(enabled);
        editSessionPromptButton.setText(getLocalizedText(
                R.string.session_prompt_edit_button,
                R.string.session_prompt_edit_button_ja,
                demoLanguage
        ));
        sessionPromptSummaryText.setText(buildSessionPromptSummary(demoLanguage, enabled, prompt));
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
        suppressSoulSelectorCallback = true;
        soulSelectorSpinner.setAdapter(adapter);
        soulSelectorSpinner.setSelection(Math.max(selectedIndex, 0), false);
        suppressSoulSelectorCallback = false;
    }

    /**
     * 让主页面下拉框和当前持久化的 soul_id 保持一致；遇到不存在的值时回退到第一个可用角色。
     */
    private void syncSoulSelectorSelection() {
        if (soulProfiles.isEmpty()) {
            suppressSoulSelectorCallback = true;
            soulSelectorSpinner.setSelection(0, false);
            suppressSoulSelectorCallback = false;
            return;
        }
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
     * 应用当前选中的元神；需要持久化时同步写回 AppPrefs，保证后续 Connect 使用同一角色。
     */
    private void applySoulSelection(int index, boolean persistSelection) {
        if (index < 0 || index >= soulProfiles.size()) {
            return;
        }
        SoulProfileOption soulProfile = soulProfiles.get(index);
        if (!persistSelection) {
            return;
        }
        if (TextUtils.equals(AppPrefs.getSoulId(this), soulProfile.soulId)) {
            return;
        }
        AppPrefs.setSoulId(this, soulProfile.soulId);
        appendLog("[UI] 当前角色=" + soulProfile.displayName() + " soulId=" + soulProfile.soulId);
    }

    /**
     * 当前角色是否为日文角色；主页面示例文本和提示文案会跟随语言切换。
     */
    private boolean isCurrentSoulJapanese() {
        return AppPrefs.DEMO_LANGUAGE_JA.equals(AppPrefs.getDemoLanguage(this));
    }

    /**
     * 在当前角色列表中查找当前 soul_id 对应的下标。
     */
    private int findSoulProfileIndex(@NonNull String soulId) {
        for (int index = 0; index < soulProfiles.size(); index++) {
            if (TextUtils.equals(soulProfiles.get(index).soulId, soulId)) {
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

            public String getWaitingMessage() {
                return null;
            }

            @Override
            public String invoke(@NonNull String argumentsJson) {
                String expressionName = parseBasicExpressionName(argumentsJson);
                renderExpression(expressionName);
                return "emoji displayed: " + expressionName;
            }
        });
        registerFixedExpressionTool("show_dance", "显示跳舞动画，适用于活跃气氛、庆祝、表演等场景。", "dance", null);
        registerFixedExpressionTool("show_monkey", "显示猴子搞怪动画，适用于调皮、卖萌、整活等场景。", "monkey", "哈哈，请稍后");
        registerFixedExpressionTool("return_to_idle", "让角色回到默认待机状态，不再展示任何表情或动作动画。适用于结束表情、恢复正常、回到默认状态等场景。", null,null);
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
    private void registerFixedExpressionTool(
            @NonNull String toolName,
            @NonNull String description,
            @Nullable String expressionName,
            @Nullable String waitingMessage
    ) {
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

            public String getWaitingMessage() {
                return waitingMessage;
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
            String sessionPrompt = AppPrefs.getSessionPrompt(this);
            boolean sessionPromptEnabled = AppPrefs.isSessionPromptEnabled(this);
            boolean sessionPromptPresent = sessionPromptEnabled && !sessionPrompt.trim().isEmpty();
            DebugOpenApiSessionTokenProvider provider = new DebugOpenApiSessionTokenProvider(
                    settings.openApiBaseUrl,
                    settings.accessKeyId,
                    settings.accessKeySecret,
                    requireNonBlank(settings.integrationAppId, "integration_app_id"),
                    requireNonBlank(settings.endUserId, "end_user_id"),
                    requireNonBlank(settings.soulId, "soul_id"),
                    this::appendLog
            );

            SessionConfig.Builder configBuilder = new SessionConfig.Builder()
                    .setWsUrl(requireNonBlank(settings.wsUrl, "wsUrl"))
                    .setProtocolVersion(parseRequiredInt(settings.protocolVersion, "protocolVersion"))
                    .setLogicalDeviceId(requireNonBlank(settings.logicalDeviceId, "logicalDeviceId"))
                    .setLogicalClientId(requireNonBlank(settings.logicalClientId, "logicalClientId"))
                    .setSessionTokenProvider(provider);
            if (sessionPromptPresent) {
                configBuilder.setSessionPrompt(sessionPrompt);
            }
            if (DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS != null) {
                configBuilder.setSessionIdleTimeoutMs(DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS);
            }
            SessionConfig config = configBuilder.build();

            appendLog("[Connect] 使用配置 openApiBaseUrl=" + settings.openApiBaseUrl
                    + " wsUrl=" + settings.wsUrl
                    + " protocolVersion=" + settings.protocolVersion
                    + " endUserId=" + displayValue(settings.endUserId)
                    + " soulId=" + displayValue(settings.soulId)
                    + " voiceMode=" + getEffectiveVoiceInputMode()
                    + " helloSessionPromptEnabled=" + sessionPromptEnabled
                    + " helloSessionPromptPresent=" + sessionPromptPresent
                    + " helloSessionPromptLength=" + sessionPrompt.length()
                    + " helloSessionIdleTimeoutMs=" + displayValue(DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS));
            sessionClient.connect(config);
            appendLog("[Connect] connect() 成功！");
        } catch (Exception e) {
            String message = e.getMessage();
            if (TextUtils.isEmpty(message)) {
                message = e.getClass().getSimpleName();
            }
            Log.e(LOGCAT_TAG, "[Connect] 失败", e);
            appendLog("[Connect] 失败: " + message, true);
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
     * 处理语音按钮的手势输入；只有 Manual 模式才接管 touch 事件。
     */
    private boolean handleListenButtonTouch(@NonNull MotionEvent event) {
        if (!isManualVoiceModeSelected()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onManualListenPressed();
                return true;
            case MotionEvent.ACTION_UP:
                manualListenPanel.performClick();
                onManualListenReleased();
                return true;
            case MotionEvent.ACTION_CANCEL:
                onManualListenReleased();
                return true;
            default:
                return true;
        }
    }

    /**
     * Manual 模式下按下说话按钮时，启动当前一轮 listening。
     */
    private void onManualListenPressed() {
        if (currentSessionState != SessionState.CONNECTED) {
            appendLog("[Manual] 操作被忽略: 当前未连接");
            return;
        }
        if (!hasRecordAudioPermission()) {
            appendLog("[Permission] RECORD_AUDIO 未授权，正在重新请求权限");
            requestRecordAudioPermissionIfNeeded();
            return;
        }
        if (listening || listenActionRunning) {
            return;
        }
        AssistantPcmPlayer player = assistantPcmPlayer;
        if (player != null) {
            player.interruptAndSuppressCurrentResponse();
        }
        manualPressing = true;
        manualStopPendingAfterStart = false;
        manualAwaitingCommit = false;
        appendLog("[UI] Manual 按下，打断当前播报并开始本轮语音输入");
        startListenNow();
    }

    /**
     * Manual 模式下松开说话按钮时，结束当前一轮 listening 并等待服务端提交最终文本。
     */
    private void onManualListenReleased() {
        if (!manualPressing && !listenActionRunning && !listening) {
            return;
        }
        manualPressing = false;
        if (currentSessionState != SessionState.CONNECTED) {
            updateActionButtons(currentSessionState);
            return;
        }
        if (listening) {
            manualAwaitingCommit = true;
            appendLog("[UI] Manual 松开，准备发送本轮语音");
            stopListenNow();
            return;
        }
        if (listenActionRunning) {
            manualAwaitingCommit = true;
            manualStopPendingAfterStart = true;
            appendLog("[UI] Manual 松开，等待收音启动后立即发送");
            updateActionButtons(currentSessionState);
        }
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
     * 在后台线程里启动当前选中模式的 listening。
     */
    private void runStartListen() {
        try {
            String voiceMode = getEffectiveVoiceInputMode();
            if (AppPrefs.VOICE_INPUT_MODE_MANUAL.equals(voiceMode)) {
                abortSpeakingIfNeeded();
                sessionClient.startListen(ListeningMode.MANUAL);
            } else {
                sessionClient.startRealtimeListen();
            }
            appendLog("[Listen] startListen() 成功 mode=" + voiceMode);
            finishListenAction(true);
        } catch (Exception e) {
            manualStopPendingAfterStart = false;
            manualAwaitingCommit = false;
            appendLog("[Listen] 开始失败 mode=" + getEffectiveVoiceInputMode() + " error=" + e.getMessage());
            finishListenAction(false);
        }
    }

    /**
     * Manual 再次按下时，若当前仍有 assistant 回复进行中，则先向服务端发送 abort。
     */
    private void abortSpeakingIfNeeded() {
        if (!assistantSpeaking) {
            return;
        }
        try {
            sessionClient.abortSpeaking();
            appendLog("[Listen] abortSpeaking() 返回成功");
        } catch (Exception e) {
            appendLog("[Listen] abortSpeaking() 失败 error=" + e.getMessage());
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
     * 弹出 Session Prompt 查看/编辑对话框；保存后写回 AppPrefs，供下一次 connect() 使用。
     */
    private void showSessionPromptDialog() {
        String demoLanguage = AppPrefs.getDemoLanguage(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        container.setPadding(padding, dp(16), padding, 0);

        TextView hintText = new TextView(this);
        hintText.setText(getLocalizedText(
                R.string.session_prompt_dialog_hint,
                R.string.session_prompt_dialog_hint_ja,
                demoLanguage
        ));
        hintText.setTextColor(ContextCompat.getColor(this, R.color.demo_text_secondary));
        container.addView(hintText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText promptEdit = new EditText(this);
        promptEdit.setMinLines(10);
        promptEdit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        promptEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        promptEdit.setHint(getLocalizedText(
                R.string.session_prompt_dialog_input_hint,
                R.string.session_prompt_dialog_input_hint_ja,
                demoLanguage
        ));
        promptEdit.setText(AppPrefs.getSessionPrompt(this));
        promptEdit.setSelection(promptEdit.getText().length());
        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        promptParams.topMargin = dp(12);
        container.addView(promptEdit, promptParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getLocalizedText(
                        R.string.session_prompt_dialog_title,
                        R.string.session_prompt_dialog_title_ja,
                        demoLanguage
                ))
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getLocalizedText(R.string.save, R.string.save_ja, demoLanguage), null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String prompt = promptEdit.getText().toString();
            AppPrefs.setSessionPrompt(this, prompt);
            renderSessionPromptControls();
            appendLog("[UI] 已保存 Session Prompt length=" + prompt.length());
            dialog.dismiss();
        }));
        dialog.show();
        promptEdit.requestFocus();
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
     * 在后台线程里结束当前一轮 listening。
     */
    private void runStopListen() {
        try {
            sessionClient.stopListen();
            appendLog("[Listen] stopListen() 返回成功 mode=" + getEffectiveVoiceInputMode());
            finishListenAction(false);
        } catch (Exception e) {
            manualAwaitingCommit = false;
            manualStopPendingAfterStart = false;
            appendLog("[Listen] 停止失败 mode=" + getEffectiveVoiceInputMode() + " error=" + e.getMessage());
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
            boolean shouldStopImmediatelyAfterStart = isManualVoiceModeSelected() && manualStopPendingAfterStart && listening;
            manualStopPendingAfterStart = false;
            updateActionButtons(currentSessionState);
            if (shouldStopImmediatelyAfterStart) {
                stopListenNow();
            }
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

    /**
     * 首次打开页面时先补一条接入提示，帮助公开 Demo 使用者建立正确的输出生命周期心智模型。
     */
    private void renderOutputLifecycleGuidance() {
        appendLog("[接入提示] [AI回复结束] 只表示服务端 stop；真正更接近“本地已播完”的时机，请继续观察 [TtsPlayer] [本地播放收口]。");
        appendLog("[接入提示] Demo 会按 responseId 聚合多句 AI 文本，并额外打印 [AI回复汇总]，便于理解一轮长回复。");
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
            String voiceMode = getEffectiveVoiceInputMode();
            boolean manualMode = AppPrefs.VOICE_INPUT_MODE_MANUAL.equals(voiceMode);
            boolean showSetupSections = state == SessionState.DISCONNECTED;
            boolean canSwitchVoiceMode = state == SessionState.DISCONNECTED
                    || (state == SessionState.CONNECTED && !listening && !listenActionRunning && !manualAwaitingCommit);
            if (state == SessionState.CONNECTED) {
                connectButton.setText(getLocalizedText(R.string.disconnect, R.string.disconnect_ja, demoLanguage));
                connectButton.setEnabled(true);
            } else {
                connectButton.setText(getLocalizedText(R.string.connect, R.string.connect_ja, demoLanguage));
                connectButton.setEnabled(state == SessionState.DISCONNECTED);
            }
            listenButton.setVisibility(manualMode ? View.GONE : View.VISIBLE);
            listenButton.setText(resolveVoiceActionButtonText(demoLanguage, manualMode));
            listenButton.setEnabled(state == SessionState.CONNECTED && !listenActionRunning);
            sendTextButton.setEnabled(state == SessionState.CONNECTED);
            sendTextButton.setText(getLocalizedText(R.string.send_text, R.string.send_text_ja, demoLanguage));
            voiceModeSectionLayout.setVisibility(showSetupSections ? View.VISIBLE : View.GONE);
            voiceModeGroup.setEnabled(canSwitchVoiceMode);
            voiceModeManualRadio.setEnabled(canSwitchVoiceMode);
            voiceModeRealtimeRadio.setEnabled(canSwitchVoiceMode);
            voiceModeSummaryText.setText(buildVoiceModeSummary(demoLanguage, voiceMode));
            soulSelectorSectionLayout.setVisibility(showSetupSections ? View.VISIBLE : View.GONE);
            sessionPromptSectionLayout.setVisibility(showSetupSections ? View.VISIBLE : View.GONE);
            soulSelectorSpinner.setEnabled(state == SessionState.DISCONNECTED && !soulProfilesLoading && !soulProfiles.isEmpty());
            sessionPromptEnabledCheckBox.setEnabled(state == SessionState.DISCONNECTED);
            editSessionPromptButton.setEnabled(state == SessionState.DISCONNECTED);
            updateListenButtonIcon(manualMode);
            listenButton.setBackgroundTintList(ContextCompat.getColorStateList(this,
                    listenButton.isEnabled() ? R.color.demo_primary_dark : R.color.demo_button_disabled));
            sendTextButton.setBackgroundTintList(ContextCompat.getColorStateList(this,
                    sendTextButton.isEnabled() ? R.color.demo_primary_dark : R.color.demo_button_disabled));
            syncManualListenPanel(demoLanguage, manualMode, state);
        });
    }

    /**
     * 根据连接态和按压态刷新 Manual 底部面板；视觉动画和交互启用在这里统一收口。
     */
    private void syncManualListenPanel(@NonNull String language, boolean manualMode, @NonNull SessionState state) {
        boolean showPanel = manualMode && state != SessionState.DISCONNECTED;
        boolean expanded = showPanel && manualPressing;
        boolean enabled = showPanel && state == SessionState.CONNECTED && (!listenActionRunning || manualPressing);
        manualListenIdleText.setText(resolveManualListenButtonText(language));
        manualListenReleaseText.setText(getLocalizedText(
                R.string.voice_action_manual_pressing,
                R.string.voice_action_manual_pressing_ja,
                language
        ));
        manualListenPanel.setContentDescription(expanded
                ? manualListenReleaseText.getText()
                : manualListenIdleText.getText());
        updateMainContentBottomPadding(showPanel);
        if (!showPanel) {
            if (manualListenPanelAnimator != null) {
                manualListenPanelAnimator.cancel();
                manualListenPanelAnimator = null;
            }
            manualListenPanel.setVisibility(View.GONE);
            applyManualListenPanelProgress(0f, false);
            return;
        }
        manualListenPanel.setVisibility(View.VISIBLE);
        manualListenPanel.setEnabled(enabled);
        manualListenPanel.setClickable(true);
        if (Math.abs(manualListenPanelProgress - (expanded ? 1f : 0f)) < 0.001f) {
            applyManualListenPanelProgress(manualListenPanelProgress, enabled);
            return;
        }
        animateManualListenPanel(expanded, enabled);
    }

    /**
     * 让默认态按钮和吸底半弧面板之间以同一个容器连续过渡，避免生硬切换。
     */
    private void animateManualListenPanel(boolean expand, boolean enabled) {
        if (manualListenPanelAnimator != null) {
            manualListenPanelAnimator.cancel();
        }
        float targetProgress = expand ? 1f : 0f;
        manualListenPanelAnimator = ValueAnimator.ofFloat(manualListenPanelProgress, targetProgress);
        manualListenPanelAnimator.setDuration(expand ? MANUAL_PANEL_EXPAND_DURATION_MS : MANUAL_PANEL_COLLAPSE_DURATION_MS);
        manualListenPanelAnimator.setInterpolator(manualPanelInterpolator);
        manualListenPanelAnimator.addUpdateListener(animation ->
                applyManualListenPanelProgress((float) animation.getAnimatedValue(), enabled));
        manualListenPanelAnimator.start();
    }

    /**
     * 按当前进度应用蒙版透明度、面板尺寸、圆角和文案切换。
     */
    private void applyManualListenPanelProgress(float progress, boolean enabled) {
        manualListenPanelProgress = progress;
        float clampedProgress = Math.max(0f, Math.min(1f, progress));
        int idleHeight = dp(MANUAL_PANEL_IDLE_HEIGHT_DP);
        int expandedHeight = dp(MANUAL_PANEL_EXPANDED_HEIGHT_DP);
        int idleSideMargin = dp(MANUAL_PANEL_IDLE_SIDE_MARGIN_DP);
        int idleBottomMargin = dp(MANUAL_PANEL_IDLE_BOTTOM_MARGIN_DP);
        int idleCorner = dp(MANUAL_PANEL_IDLE_CORNER_DP);
        int expandedTopCorner = dp(MANUAL_PANEL_EXPANDED_TOP_CORNER_DP);
        int activeTextOffset = dp(MANUAL_PANEL_ACTIVE_TEXT_OFFSET_DP);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) manualListenPanel.getLayoutParams();
        layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        layoutParams.height = lerpInt(idleHeight, expandedHeight, clampedProgress);
        layoutParams.leftMargin = lerpInt(idleSideMargin, 0, clampedProgress);
        layoutParams.rightMargin = lerpInt(idleSideMargin, 0, clampedProgress);
        layoutParams.bottomMargin = lerpInt(idleBottomMargin, 0, clampedProgress);
        manualListenPanel.setLayoutParams(layoutParams);

        int disabledColor = ContextCompat.getColor(this, R.color.demo_button_disabled);
        int idleColor = ContextCompat.getColor(this, R.color.demo_primary_dark);
        int pressedColor = ContextCompat.getColor(this, R.color.demo_primary);
        int panelColor = enabled
                ? ColorUtils.blendARGB(idleColor, pressedColor, clampedProgress)
                : disabledColor;
        float topCornerRadius = lerpInt(idleCorner, expandedTopCorner, clampedProgress);
        float bottomCornerRadius = lerpInt(idleCorner, 0, clampedProgress);
        ShapeAppearanceModel shapeAppearanceModel = new ShapeAppearanceModel.Builder()
                .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                .build();
        manualListenPanelBackground.setShapeAppearanceModel(shapeAppearanceModel);
        manualListenPanelBackground.setFillColor(ColorStateList.valueOf(panelColor));

        float contentAlpha = 1f - clampedProgress;
        manualListenIdleContent.setAlpha(contentAlpha);
        manualListenIdleContent.setTranslationY(clampedProgress * dp(8));
        manualListenActiveContent.setAlpha(clampedProgress * (enabled ? 1f : 0.82f));
        manualListenActiveContent.setTranslationY((1f - clampedProgress) * activeTextOffset);
        int foregroundColor = ContextCompat.getColor(this, R.color.demo_text_inverse);
        manualListenIdleIcon.setColorFilter(foregroundColor);
        manualListenPressedIcon.setColorFilter(foregroundColor);
        manualListenIdleIcon.setAlpha(enabled ? 1f : 0.78f);
        manualListenIdleText.setAlpha(enabled ? 1f : 0.82f);

        float overlayAlpha = MANUAL_PANEL_OVERLAY_ALPHA * clampedProgress;
        manualOverlayView.setAlpha(overlayAlpha);
        boolean showOverlay = overlayAlpha > 0.01f;
        manualOverlayView.setVisibility(showOverlay ? View.VISIBLE : View.GONE);
        manualOverlayView.setClickable(showOverlay);
    }

    /**
     * 给主内容区补足底部留白，避免默认态底部按钮压住日志面板和其他控件。
     */
    private void updateMainContentBottomPadding(boolean showManualPanel) {
        int bottomPadding = mainContentBaseBottomPadding + (showManualPanel ? dp(MANUAL_PANEL_CONTENT_BOTTOM_PADDING_DP) : 0);
        if (mainContentLayout.getPaddingBottom() == bottomPadding) {
            return;
        }
        mainContentLayout.setPadding(
                mainContentLayout.getPaddingLeft(),
                mainContentLayout.getPaddingTop(),
                mainContentLayout.getPaddingRight(),
                bottomPadding
        );
    }

    /**
     * 重置 listening 相关的本地 UI 状态。
     */
    private void resetListenState() {
        listening = false;
        listenActionRunning = false;
        manualPressing = false;
        manualStopPendingAfterStart = false;
        manualAwaitingCommit = false;
    }

    /**
     * 在会话断开或页面销毁时停止当前 TTS 播放，避免旧会话语音残留。
     */
    private void stopAssistantPlayback() {
        assistantResponseTracker.reset();
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
     * 在线性动画里把两个整数区间按进度插值，统一给尺寸和圆角使用。
     */
    private int lerpInt(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    /**
     * 让按住态麦克风图标跟随“松开发送”字号，避免图标与文字比例失衡。
     */
    private void syncManualListenPressedIconSize() {
        int iconSize = Math.round(manualListenReleaseText.getTextSize() * 4f);
        int iconTopMargin = Math.max(dp(4), Math.round(manualListenReleaseText.getTextSize() * 0.28f));
        LinearLayout.LayoutParams iconParams = (LinearLayout.LayoutParams) manualListenPressedIcon.getLayoutParams();
        iconParams.width = iconSize;
        iconParams.height = iconSize;
        iconParams.topMargin = iconTopMargin;
        manualListenPressedIcon.setLayoutParams(iconParams);
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
     * 返回当前 Demo 应生效的语音输入模式。
     */
    @NonNull
    private String getEffectiveVoiceInputMode() {
        return AppPrefs.getVoiceInputMode(this);
    }

    /**
     * 当前是否处于 Manual 语音模式。
     */
    private boolean isManualVoiceModeSelected() {
        return AppPrefs.VOICE_INPUT_MODE_MANUAL.equals(getEffectiveVoiceInputMode());
    }

    /**
     * 根据当前模式和状态返回语音操作按钮文案，避免 Manual / Realtime 语义混淆。
     */
    @NonNull
    private String resolveVoiceActionButtonText(@NonNull String language, boolean manualMode) {
        if (manualMode) {
            if (manualPressing) {
                return getLocalizedText(R.string.voice_action_manual_pressing, R.string.voice_action_manual_pressing_ja, language);
            }
            if (manualAwaitingCommit) {
                return getLocalizedText(R.string.voice_action_manual_waiting, R.string.voice_action_manual_waiting_ja, language);
            }
            return getLocalizedText(R.string.voice_action_manual_idle, R.string.voice_action_manual_idle_ja, language);
        }
        return listening
                ? getLocalizedText(R.string.voice_action_realtime_listening, R.string.voice_action_realtime_listening_ja, language)
                : getLocalizedText(R.string.voice_action_realtime_idle, R.string.voice_action_realtime_idle_ja, language);
    }

    /**
     * 返回 Manual 大按钮文案；等待识别期间仍允许再次按住，因此空闲态继续展示“按住说话”。
     */
    @NonNull
    private String resolveManualListenButtonText(@NonNull String language) {
        if (manualPressing) {
            return getLocalizedText(R.string.voice_action_manual_pressing, R.string.voice_action_manual_pressing_ja, language);
        }
        return getLocalizedText(R.string.voice_action_manual_idle, R.string.voice_action_manual_idle_ja, language);
    }

    /**
     * 生成语音模式摘要，帮助用户理解当前模式和即时状态。
     */
    @NonNull
    private String buildVoiceModeSummary(@NonNull String language, @NonNull String voiceMode) {
        if (AppPrefs.VOICE_INPUT_MODE_MANUAL.equals(voiceMode)) {
            if (manualPressing) {
                return getLocalizedText(
                        R.string.voice_mode_summary_manual_pressing,
                        R.string.voice_mode_summary_manual_pressing_ja,
                        language
                );
            }
            if (manualAwaitingCommit) {
                return getLocalizedText(
                        R.string.voice_mode_summary_manual_waiting,
                        R.string.voice_mode_summary_manual_waiting_ja,
                        language
                );
            }
            return getLocalizedText(
                    R.string.voice_mode_summary_manual_idle,
                    R.string.voice_mode_summary_manual_idle_ja,
                    language
            );
        }
        return listening
                ? getLocalizedText(
                        R.string.voice_mode_summary_realtime_listening,
                        R.string.voice_mode_summary_realtime_listening_ja,
                        language
                )
                : getLocalizedText(
                        R.string.voice_mode_summary_realtime_idle,
                        R.string.voice_mode_summary_realtime_idle_ja,
                        language
                );
    }

    /**
     * Manual 模式给按钮加上麦克风图标，Realtime 模式则保持纯文本按钮。
     */
    private void updateListenButtonIcon(boolean manualMode) {
        int startDrawable = manualMode ? android.R.drawable.ic_btn_speak_now : 0;
        listenButton.setCompoundDrawablesWithIntrinsicBounds(startDrawable, 0, 0, 0);
        listenButton.setCompoundDrawablePadding(manualMode ? dp(6) : 0);
    }

    /**
     * 生成主页面 Session Prompt 摘要，避免长 prompt 完整铺开占满首页。
     */
    @NonNull
    private String buildSessionPromptSummary(@NonNull String language, boolean enabled, @NonNull String prompt) {
        String normalized = prompt.trim();
        if (normalized.isEmpty()) {
            return getLocalizedText(
                    enabled ? R.string.session_prompt_summary_enabled_empty : R.string.session_prompt_summary_disabled_empty,
                    enabled ? R.string.session_prompt_summary_enabled_empty_ja : R.string.session_prompt_summary_disabled_empty_ja,
                    language
            );
        }
        String preview = abbreviatePromptPreview(normalized);
        int length = prompt.length();
        int resId = AppPrefs.DEMO_LANGUAGE_JA.equals(language)
                ? (enabled ? R.string.session_prompt_summary_enabled_ja : R.string.session_prompt_summary_disabled_ja)
                : (enabled ? R.string.session_prompt_summary_enabled : R.string.session_prompt_summary_disabled);
        return getString(resId, length, preview);
    }

    /**
     * 把长 prompt 收敛成单行预览，便于首页快速判断当前配置内容。
     */
    @NonNull
    private String abbreviatePromptPreview(@NonNull String prompt) {
        String preview = prompt.replace('\r', ' ').replace('\n', ' ').trim();
        if (preview.length() <= 48) {
            return preview;
        }
        return preview.substring(0, 48) + "...";
    }

    /**
     * 根据当前 Demo 语言返回对应文案。
     */
    @NonNull
    private String getLocalizedText(int zhResId, int jaResId, @NonNull String language) {
        return AppPrefs.DEMO_LANGUAGE_JA.equals(language) ? getString(jaResId) : getString(zhResId);
    }

}
