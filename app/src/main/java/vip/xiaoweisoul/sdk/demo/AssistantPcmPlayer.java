package vip.xiaoweisoul.sdk.demo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import vip.xiaoweisoul.sdk.sessioncore.PcmFrame;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo 侧正式的 assistant PCM 播放器。
 * 负责音频焦点、AudioTrack、后台写入线程以及空闲自动收口。
 */
final class AssistantPcmPlayer {
    private static final String TAG = "AssistantPcmPlayer";

    /**
     * 下行 PCM 播放队列容量上限；够大可覆盖短时突发下发，又避免无限积压占用内存。
     */
    private static final int QUEUE_CAPACITY = 256;

    /**
     * 播放队列满载时，入队线程每次等待 AudioTrack 消费腾挪空间的最长时长。
     */
    private static final long QUEUE_BACKPRESSURE_WAIT_MS = 200L;

    /**
     * 播放线程空闲超时；超过该时长仍无新 PCM，则把当前链路收口到静音空闲态。
     * Demo 用这个空闲收口点示意“更接近本地已播完”，方便公开文档解释广告插播时机。
     */
    private static final long IDLE_STOP_TIMEOUT_MS = 5000L;

    /**
     * 销毁播放器时等待后台线程退出的最长时间，避免主线程无限阻塞。
     */
    private static final long RELEASE_JOIN_TIMEOUT_MS = 1500L;

    /**
     * 正常播放时的固定输出音量；静音态会在此基础上额外压到 0。
     */
    private static final float PLAYBACK_VOLUME = 1.0f;

    /**
     * 新建 AudioTrack 后预热写入的静音时长，用于尽量提前触发底层起播链路，降低首声爆破音概率。
     */
    private static final int PREWARM_SILENCE_MS = 12;

    /**
     * 每轮回复首帧 PCM 的样本级淡入时长，避免首样本从非零点硬起播产生 click/pop。
     */
    private static final int START_FADE_IN_MS = 8;

    /**
     * 首播先攒 3 帧再发车，先用最小逻辑提高起播稳定性。
     */
    private static final int START_BUFFER_FRAMES = 3;

    /**
     * 单帧理论时长 60ms 左右；逐条抖动日志提高到接近 3 帧断供时再打印，避免把可接受的小抖动也刷出来。
     */
    private static final long LOCAL_PLAYBACK_GAP_WARN_MS = 180L;

    /**
     * AudioTrack.write 是阻塞调用；这里和上面的 180ms 口径对齐，只有明显超过 3 帧量级才逐条告警。
     */
    private static final long LOCAL_WRITE_BLOCK_EXTRA_WARN_MS = 120L;

    interface Logger {
        /**
         * 输出一条播放器调试日志。
         */
        void log(@NonNull String line);
    }

    private final Object audioLock = new Object();
    private final Logger logger;
    @Nullable
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private final AudioAttributes audioAttributes;
    private final LinkedBlockingDeque<PlaybackItem> queue = new LinkedBlockingDeque<>(QUEUE_CAPACITY);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final AtomicBoolean queueBackpressureActive = new AtomicBoolean(false);
    private final Thread workerThread;
    private final String playbackStrategy;
    private final String playbackStrategyLabel;
    private final int focusGain;
    private final boolean audioFocusEnabled;
    @Nullable
    private String latestAcceptedResponseId;
    @Nullable
    private String suppressedResponseId;
    @Nullable
    private String lastFadeInResponseId;
    private boolean suppressUnknownResponseUntilNextKnown;

    @Nullable
    private AudioTrack audioTrack;
    @Nullable
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusGranted;
    private boolean idleStateEntered;
    private boolean forceMute;
    private int currentSampleRateHz = -1;
    private int currentChannels = -1;
    @Nullable
    private Long currentPlaybackTurnId;
    @Nullable
    private String currentPlaybackResponseId;
    private boolean localPlaybackActive;
    @Nullable
    private Long lastWriteEndAtMs;
    private long playbackFrameCount;
    private int playbackGapWarnCount;
    private long playbackMaxGapMs;
    private int queueWaitWarnCount;
    private long queueWaitMaxMs;
    private int writeBlockWarnCount;
    private long writeBlockMaxMs;

    /**
     * 创建一个独立播放器线程，专门消费 SDK 回调的 assistant PCM。
     */
    AssistantPcmPlayer(@NonNull Context context, @NonNull Logger logger, @NonNull String playbackStrategy, @NonNull String playbackStrategyLabel) {
        this.logger = logger;
        this.playbackStrategy = normalizePlaybackStrategy(playbackStrategy);
        this.playbackStrategyLabel = playbackStrategyLabel;
        this.focusGain = resolveFocusGain(this.playbackStrategy);
        this.audioFocusEnabled = focusGain != Integer.MIN_VALUE;
        Context appContext = context.getApplicationContext();
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        this.audioFocusChangeListener = this::onAudioFocusChange;
        this.audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        this.workerThread = new Thread(this::runLoop, "XW-AssistantPcmPlayer");
        this.workerThread.start();
    }

    /**
     * 提交一帧 PCM 给后台播放线程；当播放消费跟不上时，对上游施加背压，避免继续丢帧。
     */
    void play(@NonNull PcmFrame frame) {
        if (released.get()) {
            return;
        }
        if (!PcmFrame.FORMAT_PCM_S16LE.equals(frame.getFormat())) {
            logLine("[TtsPlayer] 忽略不支持的 PCM 格式: " + frame.getFormat());
            return;
        }
        if (!shouldAcceptFrame(frame.getResponseId())) {
            return;
        }
        PlaybackItem item = PlaybackItem.play(
                frame.getSampleRateHz(),
                frame.getChannels(),
                frame.getSeq(),
                frame.getTurnId(),
                frame.getResponseId(),
                copyFrameData(frame)
        );
        try {
            while (!released.get()) {
                if (queue.offerLast(item, QUEUE_BACKPRESSURE_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    if (queueBackpressureActive.compareAndSet(true, false)) {
                        logLine("[TtsPlayer] 播放队列已恢复");
                    }
                    return;
                }
                if (queueBackpressureActive.compareAndSet(false, true)) {
                    logLine("[TtsPlayer] 播放队列积压，暂停继续接收 PCM，等待 AudioTrack 消费");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logLine("[TtsPlayer] PCM 入队等待被中断");
        }
    }

    /**
     * 立即清空尚未播放的数据，并让当前播放链路进入静默态。
     */
    void flushAndStop() {
        if (released.get()) {
            return;
        }
        queue.clear();
        queueBackpressureActive.set(false);
        queue.offerLast(PlaybackItem.flushStop());
    }

    /**
     * 告诉播放器：这一轮回复已经服务端收口。
     * 这样短句即使不足 3 帧，也不会一直等首播门槛。
     */
    void markResponseStop(@Nullable Long turnId, @Nullable String responseId, @Nullable String reason) {
        if (released.get()) {
            return;
        }
        queue.offerLast(PlaybackItem.responseStop(turnId, responseId));
    }

    /**
     * 在本地发送 interrupt=true 前，先停掉当前播放，并持续屏蔽旧 response 的尾包。
     */
    void interruptAndSuppressCurrentResponse() {
        suppressCurrentResponseAndStop("[TtsPlayer] 本地 interrupt=true");
    }

    /**
     * 服务端下发强打断型 tts.stop 时，立即停掉当前播放并屏蔽旧 response 尾包。
     */
    void interruptAndSuppressResponseFromServer(@Nullable String responseId, @Nullable String reason) {
        String normalizedReason = reason == null || reason.trim().isEmpty() ? "unknown" : reason.trim();
        String normalizedResponseId = normalizeResponseId(responseId);
        suppressResponseAndStop("[TtsPlayer] 服务端强打断 stop reason=" + normalizedReason, normalizedResponseId);
    }

    /**
     * 统一执行“屏蔽旧 response 尾包 + 本地软停止”。
     */
    private void suppressCurrentResponseAndStop(@NonNull String actionLabel) {
        suppressResponseAndStop(actionLabel, null);
    }

    /**
     * 统一执行“屏蔽指定 response 尾包 + 本地软停止”；若未指定，则回退到当前最新 response。
     */
    private void suppressResponseAndStop(@NonNull String actionLabel, @Nullable String responseIdHint) {
        if (released.get()) {
            return;
        }
        String responseId;
        synchronized (audioLock) {
            responseId = responseIdHint != null ? responseIdHint : latestAcceptedResponseId;
            suppressedResponseId = responseId;
            suppressUnknownResponseUntilNextKnown = true;
            muteAudioTrackLocked(null);
        }
        if (responseId == null) {
            logLine(actionLabel + "：已停止播放，并在新 response 到来前丢弃旧尾包 PCM");
        } else {
            logLine(actionLabel + "：已停止播放，并屏蔽旧 responseId=" + responseId + " 的尾包");
        }
        flushAndStop();
    }

    /**
     * 在断开会话等场景下清空播放器状态，避免上一轮 response 影响后续连接。
     */
    void flushStopAndResetResponseState() {
        synchronized (audioLock) {
            latestAcceptedResponseId = null;
            lastFadeInResponseId = null;
            clearSuppressionLocked();
        }
        flushAndStop();
    }

    /**
     * 释放播放器线程、AudioTrack 与音频焦点。
     */
    void release() {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        queue.clear();
        queueBackpressureActive.set(false);
        queue.offerLast(PlaybackItem.release());
        if (Thread.currentThread() != workerThread) {
            try {
                workerThread.join(RELEASE_JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 后台播放线程主循环：消费 PCM、处理停止命令，并在空闲时把播放链路收口干净。
     */
    private void runLoop() {
        ArrayDeque<PlaybackItem> startBuffer = new ArrayDeque<>();
        PlaybackGateState gateState = new PlaybackGateState();
        try {
            while (true) {
                PlaybackWaitState waitState = snapshotPlaybackWaitState();
                long pollStartAtMs = SystemClock.elapsedRealtime();
                PlaybackItem item = queue.pollFirst(IDLE_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                long pollWaitMs = SystemClock.elapsedRealtime() - pollStartAtMs;
                if (item == null) {
                    if (hasTrack()) {
                        enterIdleState("等待新 PCM 超时 " + IDLE_STOP_TIMEOUT_MS + "ms");
                    }
                    continue;
                }
                if (item.type == PlaybackItem.TYPE_RELEASE) {
                    break;
                }
                if (item.type == PlaybackItem.TYPE_FLUSH_STOP) {
                    startBuffer.clear();
                    gateState.reset();
                    enterIdleState("收到停止命令");
                    continue;
                }
                if (item.type == PlaybackItem.TYPE_RESPONSE_STOP) {
                    gateState.onResponseStop(item.turnId, item.responseId);
                    if (!gateState.started && gateState.canStart(startBuffer.size())) {
                        while (!startBuffer.isEmpty()) {
                            playFrame(startBuffer.removeFirst());
                        }
                        gateState.started = true;
                    }
                    continue;
                }
                if (!gateState.matches(item.turnId, item.responseId)) {
                    startBuffer.clear();
                    gateState.reset();
                    gateState.bind(item.turnId, item.responseId);
                }
                if (!gateState.started) {
                    startBuffer.addLast(item);
                    if (gateState.canStart(startBuffer.size())) {
                        while (!startBuffer.isEmpty()) {
                            playFrame(startBuffer.removeFirst());
                        }
                        gateState.started = true;
                    }
                    continue;
                }
                String queueWaitLog = recordQueueWait(item, waitState, pollWaitMs);
                if (queueWaitLog != null) {
                    logLine(queueWaitLog);
                }
                playFrame(item);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdownPlayback("播放器线程退出");
        }
    }

    /**
     * 播放单帧 PCM；先确保焦点和播放链路都已就绪，再解除静音并写入真实数据。
     * 这样可以避免在尚未拿到焦点时就恢复音量，减少起播瞬态噪声。
     */
    private void playFrame(@NonNull PlaybackItem item) {
        try {
            synchronized (audioLock) {
                idleStateEntered = false;
            }
            if (!ensureAudioFocus()) {
                logLine("[TtsPlayer] 音频焦点申请失败，跳过 seq=" + item.seq);
                return;
            }
            AudioTrack track = ensureAudioTrack(item.sampleRateHz, item.channels);
            synchronized (audioLock) {
                // 焦点和 Track 都已准备完成后再解除静音，避免过早恢复音量放大起播瞬态噪声。
                clearForceMuteLocked();
            }
            ensureTrackPlaying(track);
            String playbackStartLog = announcePlaybackStartIfNeeded(item);
            if (playbackStartLog != null) {
                logLine(playbackStartLog);
            }
            byte[] data = item.data;
            if (data == null || data.length == 0) {
                return;
            }
            int pendingQueueDepth = queue.size();
            long frameDurationMs = estimateFrameDurationMs(data.length, item.sampleRateHz, item.channels);
            long writeStartAtMs = SystemClock.elapsedRealtime();
            String playbackGapLog = maybeBuildPlaybackGapLog(item, writeStartAtMs, frameDurationMs, pendingQueueDepth);
            if (playbackGapLog != null) {
                logLine(playbackGapLog);
            }
            int written = track.write(data, 0, data.length, AudioTrack.WRITE_BLOCKING);
            long writeCostMs = SystemClock.elapsedRealtime() - writeStartAtMs;
            if (written < 0) {
                throw new IllegalStateException("AudioTrack.write failed: " + written);
            }
            if (written != data.length) {
                logLine("[TtsPlayer] PCM 写入不完整，seq=" + item.seq + " bytes=" + written + "/" + data.length);
            }
            String writeBlockLog = recordWriteResult(item, writeStartAtMs + writeCostMs, frameDurationMs, pendingQueueDepth, writeCostMs);
            if (writeBlockLog != null) {
                logLine(writeBlockLog);
            }
        } catch (Exception e) {
            logLine("[TtsPlayer] 播放失败: " + e.getMessage());
            Log.w(TAG, "assistant pcm playback failed", e);
            synchronized (audioLock) {
                releaseAudioTrackLocked("播放异常重建");
                abandonAudioFocusLocked();
            }
        }
    }

    /**
     * 确保当前存在与 PCM 参数匹配的 AudioTrack；参数变化时自动重建。
     * 新建后会立即进入播放态并灌入一小段静音，尽量把底层起播边沿前置掉。
     */
    @NonNull
    private AudioTrack ensureAudioTrack(int sampleRateHz, int channels) {
        synchronized (audioLock) {
            if (audioTrack != null && sampleRateHz == currentSampleRateHz && channels == currentChannels) {
                return audioTrack;
            }
            releaseAudioTrackLocked(audioTrack == null ? null : "播放参数变化，重建 AudioTrack");

            int channelMask = resolveChannelMask(channels);
            int minBufferBytes = AudioTrack.getMinBufferSize(sampleRateHz, channelMask, AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferBytes <= 0) {
                throw new IllegalStateException("AudioTrack min buffer unavailable: " + minBufferBytes);
            }
            int bufferBytes = Math.max(minBufferBytes * 2, sampleRateHz * channels * 2 / 5);
            AudioTrack track = new AudioTrack(
                    audioAttributes,
                    new AudioFormat.Builder()
                            .setSampleRate(sampleRateHz)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(channelMask)
                            .build(),
                    bufferBytes,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                try {
                    track.release();
                } catch (Exception ignored) {
                }
                throw new IllegalStateException("AudioTrack init failed");
            }
            audioTrack = track;
            currentSampleRateHz = sampleRateHz;
            currentChannels = channels;
            applyTrackVolumeLocked();
            ensureTrackPlaying(track);
            primeTrackWithSilence(track, sampleRateHz, channels);
            logLine("[TtsPlayer] AudioTrack usage=USAGE_MEDIA strategy=" + playbackStrategyLabel + " rawStrategy=" + playbackStrategy);
            logLine("[TtsPlayer] 创建播放链路 sampleRate=" + sampleRateHz + " channels=" + channels + " bufferBytes=" + bufferBytes);
            return track;
        }
    }

    /**
     * 确保 Track 处于播放态，避免空闲后再次写入前仍停留在 pause 状态。
     */
    private void ensureTrackPlaying(@NonNull AudioTrack track) {
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        track.play();
    }

    /**
     * 按当前策略申请音频焦点；并行模式下不请求焦点。
     */
    private boolean ensureAudioFocus() {
        synchronized (audioLock) {
            if (!audioFocusEnabled || audioManager == null) {
                return true;
            }
            if (audioFocusGranted) {
                return true;
            }
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest request = getOrCreateAudioFocusRequestLocked();
                result = audioManager.requestAudioFocus(request);
            } else {
                result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, focusGain);
            }
            audioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (audioFocusGranted) {
                logLine("[TtsPlayer] 已获取音频焦点 strategy=" + playbackStrategyLabel);
            }
            return audioFocusGranted;
        }
    }

    @NonNull
    private AudioFocusRequest getOrCreateAudioFocusRequestLocked() {
        if (audioFocusRequest != null) {
            return audioFocusRequest;
        }
        audioFocusRequest = new AudioFocusRequest.Builder(focusGain)
                .setAcceptsDelayedFocusGain(false)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build();
        return audioFocusRequest;
    }

    /**
     * 处理系统音频焦点变化：在真正失焦时停止当前播放并进入静默态。
     */
    private void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            logLine("[TtsPlayer] 音频焦点恢复");
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            synchronized (audioLock) {
                audioFocusGranted = false;
            }
            logLine("[TtsPlayer] 音频焦点丢失，停止当前播放");
            flushAndStop();
        }
    }

    /**
     * 进入静默态：直接释放当前 AudioTrack，并归还音频焦点。
     * 这样做的原因是上一轮播放结束后，底层 Track 可能已经因为 underrun 进入 disabled 状态；
     * 如果继续复用，下一轮起播时就容易出现 restartIfDisabled，进而带来抖动或颤音。
     */
    private void enterIdleState(@NonNull String reason) {
        String idleLog = null;
        String playbackStatsLog = null;
        synchronized (audioLock) {
            boolean shouldLog = !idleStateEntered;
            if (shouldLog) {
                idleLog = buildPlaybackIdleLogLocked(reason);
                playbackStatsLog = buildPlaybackStatsLogLocked();
            }
            muteAudioTrackLocked(null);
            releaseAudioTrackLocked("进入静默态，丢弃可能已 underrun 的旧 AudioTrack");
            abandonAudioFocusLocked();
            idleStateEntered = true;
        }
        if (idleLog != null) {
            logLine(idleLog);
        }
        if (playbackStatsLog != null) {
            logLine(playbackStatsLog);
        }
    }

    /**
     * 彻底关闭播放链路，用于线程退出和播放器销毁。
     */
    private void shutdownPlayback(@NonNull String reason) {
        synchronized (audioLock) {
            releaseAudioTrackLocked(reason);
            abandonAudioFocusLocked();
        }
    }

    /**
     * 返回当前是否已经创建过 AudioTrack。
     */
    private boolean hasTrack() {
        synchronized (audioLock) {
            return audioTrack != null;
        }
    }

    /**
     * 过滤 stop 之后可能迟到的旧 response 音频帧，避免尾音串到下一轮回复。
     */
    private boolean shouldAcceptFrame(@Nullable String responseId) {
        synchronized (audioLock) {
            String normalizedResponseId = responseId == null ? null : responseId.trim();
            if (normalizedResponseId == null || normalizedResponseId.isEmpty()) {
                return !suppressUnknownResponseUntilNextKnown;
            }
            if (normalizedResponseId.equals(suppressedResponseId)) {
                return false;
            }
            latestAcceptedResponseId = normalizedResponseId;
            suppressedResponseId = null;
            suppressUnknownResponseUntilNextKnown = false;
            return true;
        }
    }

    private void clearSuppressionLocked() {
        suppressedResponseId = null;
        suppressUnknownResponseUntilNextKnown = false;
    }

    /**
     * 复制一帧 PCM；如果是新一轮回复的首帧，则对首段样本做极短淡入。
     */
    @NonNull
    private byte[] copyFrameData(@NonNull PcmFrame frame) {
        byte[] data = Arrays.copyOf(frame.getData(), frame.getData().length);
        if (shouldApplyLeadingFadeIn(frame)) {
            applyLeadingFadeIn(data, frame.getSampleRateHz(), frame.getChannels());
        }
        return data;
    }

    /**
     * 只对每轮回复的首帧补淡入，减少首样本非零起跳带来的爆破音。
     */
    private boolean shouldApplyLeadingFadeIn(@NonNull PcmFrame frame) {
        synchronized (audioLock) {
            String responseId = normalizeResponseId(frame.getResponseId());
            if (responseId != null) {
                if (responseId.equals(lastFadeInResponseId)) {
                    return false;
                }
                lastFadeInResponseId = responseId;
                return true;
            }
            return frame.getSeq() <= 0;
        }
    }

    /**
     * 直接修改 PCM 首段波形包络，避免首帧从非零点硬起播。
     */
    private void applyLeadingFadeIn(@NonNull byte[] data, int sampleRateHz, int channels) {
        if (channels <= 0 || data.length < channels * 2) {
            return;
        }
        int totalFrames = data.length / (channels * 2);
        if (totalFrames <= 0) {
            return;
        }
        int fadeFrames = Math.min(totalFrames, Math.max(1, (sampleRateHz * START_FADE_IN_MS) / 1000));
        for (int frameIndex = 0; frameIndex < fadeFrames; frameIndex += 1) {
            float gain = (float) (frameIndex + 1) / (float) fadeFrames;
            for (int channelIndex = 0; channelIndex < channels; channelIndex += 1) {
                int sampleIndex = ((frameIndex * channels) + channelIndex) * 2;
                short sample = (short) ((data[sampleIndex] & 0xff) | (data[sampleIndex + 1] << 8));
                int scaled = Math.round(sample * gain);
                data[sampleIndex] = (byte) (scaled & 0xff);
                data[sampleIndex + 1] = (byte) ((scaled >> 8) & 0xff);
            }
        }
    }

    /**
     * 新建 AudioTrack 后先灌入一小段静音，尽量把首轮真实出声前的硬件起播边沿前置掉。
     */
    private void primeTrackWithSilence(@NonNull AudioTrack track, int sampleRateHz, int channels) {
        int silenceFrames = Math.max(1, (sampleRateHz * PREWARM_SILENCE_MS) / 1000);
        byte[] silence = new byte[silenceFrames * channels * 2];
        try {
            int written = track.write(silence, 0, silence.length, AudioTrack.WRITE_BLOCKING);
            if (written > 0) {
                track.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "prime track with silence failed", e);
        }
    }

    /**
     * 让当前 AudioTrack 进入强制静音态，但不 pause/flush，避免打断时硬切波形。
     */
    private void muteAudioTrackLocked(@Nullable String reason) {
        if (audioTrack == null) {
            return;
        }
        forceMute = true;
        try {
            applyTrackVolumeLocked();
        } catch (Exception ignored) {
        }
        if (reason != null && !reason.isEmpty()) {
            logLine("[TtsPlayer] 停止播放: " + reason);
        }
    }

    /**
     * 当前一轮新的可播放 PCM 到来时，恢复 AudioTrack 正常音量。
     * 这里只在焦点和 Track 都准备完成后调用，避免过早解除静音放大起播瞬态。
     */
    private void clearForceMuteLocked() {
        if (!forceMute) {
            return;
        }
        forceMute = false;
        applyTrackVolumeLocked();
    }

    /**
     * 把逻辑音量同步到 AudioTrack；静音态下把实际音量压到 0。
     * 这是当前 Demo 用来替代 pause/flush 硬停播的最小静音门控手段。
     */
    private void applyTrackVolumeLocked() {
        AudioTrack track = audioTrack;
        if (track == null) {
            return;
        }
        track.setVolume(forceMute ? 0.0f : PLAYBACK_VOLUME);
    }

    /**
     * 彻底释放 AudioTrack，并重置当前播放参数。
     */
    private void releaseAudioTrackLocked(@Nullable String reason) {
        AudioTrack track = audioTrack;
        if (track == null) {
            currentSampleRateHz = -1;
            currentChannels = -1;
            clearCurrentPlaybackStateLocked();
            return;
        }
        audioTrack = null;
        currentSampleRateHz = -1;
        currentChannels = -1;
        idleStateEntered = false;
        forceMute = false;
        clearCurrentPlaybackStateLocked();
        try {
            track.pause();
        } catch (Exception ignored) {
        }
        try {
            track.flush();
        } catch (Exception ignored) {
        }
        try {
            track.release();
        } catch (Exception ignored) {
        }
        if (reason != null && !reason.isEmpty()) {
            logLine("[TtsPlayer] 释放播放链路: " + reason);
        }
    }

    /**
     * 放弃当前已申请的音频焦点。
     */
    private void abandonAudioFocusLocked() {
        if (!audioFocusGranted || audioManager == null) {
            audioFocusGranted = false;
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest request = getOrCreateAudioFocusRequestLocked();
                audioManager.abandonAudioFocusRequest(request);
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
        } catch (Exception e) {
            Log.w(TAG, "abandon audio focus failed", e);
        } finally {
            audioFocusGranted = false;
            logLine("[TtsPlayer] [释放音频焦点] 将申请到的音频焦点还给系统。");
        }
    }

    /**
     * 把声道数转换成 Android AudioTrack 所需的 channel mask。
     */
    private int resolveChannelMask(int channels) {
        if (channels == 1) {
            return AudioFormat.CHANNEL_OUT_MONO;
        }
        if (channels == 2) {
            return AudioFormat.CHANNEL_OUT_STEREO;
        }
        throw new IllegalArgumentException("unsupported pcm channels: " + channels);
    }

    @NonNull
    private static String normalizePlaybackStrategy(@Nullable String value) {
        if (AppPrefs.TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS.equals(value)) {
            return AppPrefs.TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS;
        }
        if (AppPrefs.TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS.equals(value)) {
            return AppPrefs.TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS;
        }
        return AppPrefs.TTS_PLAYBACK_STRATEGY_DUCK_OTHERS;
    }

    private static int resolveFocusGain(@NonNull String playbackStrategy) {
        if (AppPrefs.TTS_PLAYBACK_STRATEGY_PAUSE_OTHERS.equals(playbackStrategy)) {
            return AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
        }
        if (AppPrefs.TTS_PLAYBACK_STRATEGY_MIX_WITH_OTHERS.equals(playbackStrategy)) {
            return Integer.MIN_VALUE;
        }
        return AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
    }

    @Nullable
    private static String normalizeResponseId(@Nullable String responseId) {
        if (responseId == null) {
            return null;
        }
        String normalized = responseId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 同时输出到 Demo 日志面板和 Logcat，方便现场排障。
     */
    private void logLine(@NonNull String message) {
        Log.i(TAG, message);
        logger.log(message);
    }

    @Nullable
    private String announcePlaybackStartIfNeeded(@NonNull PlaybackItem item) {
        String previousStatsLog = null;
        synchronized (audioLock) {
            String normalizedResponseId = normalizeResponseId(item.responseId);
            if (localPlaybackActive
                    && Objects.equals(currentPlaybackResponseId, normalizedResponseId)
                    && Objects.equals(currentPlaybackTurnId, item.turnId)) {
                return null;
            }
            if (localPlaybackActive) {
                previousStatsLog = buildPlaybackStatsLogLocked();
                resetPlaybackMetricsLocked();
            }
            localPlaybackActive = true;
            currentPlaybackTurnId = item.turnId;
            currentPlaybackResponseId = normalizedResponseId;
            String startLog = "[TtsPlayer] [本地播放开始]"
                    + " turnId=" + displayValue(item.turnId)
                    + " responseId=" + displayValue(normalizedResponseId)
                    + " seq=" + item.seq;
            if (previousStatsLog != null) {
                logLine(previousStatsLog);
            }
            return startLog;
        }
    }

    @NonNull
    private String buildPlaybackIdleLogLocked(@NonNull String reason) {
        return "[TtsPlayer] [本地播放收口]"
                + " turnId=" + displayValue(currentPlaybackTurnId)
                + " responseId=" + displayValue(currentPlaybackResponseId)
                + " reason=" + reason
                + "；Demo 中这类本地收口信号比服务端 stop 更接近广告/背景音恢复时机。";
    }

    private void clearCurrentPlaybackStateLocked() {
        currentPlaybackTurnId = null;
        currentPlaybackResponseId = null;
        localPlaybackActive = false;
        resetPlaybackMetricsLocked();
    }

    private void resetPlaybackMetricsLocked() {
        lastWriteEndAtMs = null;
        playbackFrameCount = 0L;
        playbackGapWarnCount = 0;
        playbackMaxGapMs = 0L;
        queueWaitWarnCount = 0;
        queueWaitMaxMs = 0L;
        writeBlockWarnCount = 0;
        writeBlockMaxMs = 0L;
    }

    @Nullable
    private String maybeBuildPlaybackGapLog(@NonNull PlaybackItem item, long writeStartAtMs, long frameDurationMs, int pendingQueueDepth) {
        if (pendingQueueDepth <= 0 || lastWriteEndAtMs == null) {
            return null;
        }
        if (!Objects.equals(currentPlaybackTurnId, item.turnId)
                || !Objects.equals(currentPlaybackResponseId, normalizeResponseId(item.responseId))) {
            return null;
        }
        long gapMs = writeStartAtMs - lastWriteEndAtMs;
        if (gapMs <= LOCAL_PLAYBACK_GAP_WARN_MS) {
            return null;
        }
        playbackGapWarnCount += 1;
        playbackMaxGapMs = Math.max(playbackMaxGapMs, gapMs);
        return "[TtsPlayer] [本地播放抖动]"
                + " type=write_start_gap"
                + " turnId=" + displayValue(item.turnId)
                + " responseId=" + displayValue(normalizeResponseId(item.responseId))
                + " seq=" + item.seq
                + " gapMs=" + gapMs
                + " expectedFrameMs=" + frameDurationMs
                + " queueDepth=" + pendingQueueDepth;
    }

    @Nullable
    private String recordWriteResult(@NonNull PlaybackItem item, long writeEndAtMs, long frameDurationMs, int pendingQueueDepth, long writeCostMs) {
        playbackFrameCount += 1L;
        lastWriteEndAtMs = writeEndAtMs;
        if (pendingQueueDepth <= 0 || writeCostMs <= frameDurationMs + LOCAL_WRITE_BLOCK_EXTRA_WARN_MS) {
            return null;
        }
        writeBlockWarnCount += 1;
        writeBlockMaxMs = Math.max(writeBlockMaxMs, writeCostMs);
        return "[TtsPlayer] [本地播放抖动]"
                + " type=write_block"
                + " turnId=" + displayValue(item.turnId)
                + " responseId=" + displayValue(normalizeResponseId(item.responseId))
                + " seq=" + item.seq
                + " costMs=" + writeCostMs
                + " expectedFrameMs=" + frameDurationMs
                + " queueDepth=" + pendingQueueDepth;
    }

    @Nullable
    private String buildPlaybackStatsLogLocked() {
        if (!localPlaybackActive && playbackFrameCount <= 0L) {
            return null;
        }
        return "[TtsPlayer] [本地播放统计]"
                + " turnId=" + displayValue(currentPlaybackTurnId)
                + " responseId=" + displayValue(currentPlaybackResponseId)
                + " frames=" + playbackFrameCount
                + " gapWarnCount=" + playbackGapWarnCount
                + " maxGapMs=" + playbackMaxGapMs
                + " queueWaitWarnCount=" + queueWaitWarnCount
                + " maxQueueWaitMs=" + queueWaitMaxMs
                + " writeBlockWarnCount=" + writeBlockWarnCount
                + " maxWriteBlockMs=" + writeBlockMaxMs;
    }

    @Nullable
    private String recordQueueWait(@NonNull PlaybackItem item, @NonNull PlaybackWaitState waitState, long pollWaitMs) {
        if (!waitState.hasTrack || !waitState.playbackActive || pollWaitMs <= LOCAL_PLAYBACK_GAP_WARN_MS) {
            return null;
        }
        synchronized (audioLock) {
            queueWaitWarnCount += 1;
            queueWaitMaxMs = Math.max(queueWaitMaxMs, pollWaitMs);
        }
        return "[TtsPlayer] [本地播放抖动]"
                + " type=queue_wait"
                + " turnId=" + displayValue(waitState.turnId)
                + " responseId=" + displayValue(waitState.responseId)
                + " nextSeq=" + item.seq
                + " gapMs=" + pollWaitMs;
    }

    @NonNull
    private PlaybackWaitState snapshotPlaybackWaitState() {
        synchronized (audioLock) {
            return new PlaybackWaitState(audioTrack != null, localPlaybackActive, currentPlaybackTurnId, currentPlaybackResponseId);
        }
    }

    private static long estimateFrameDurationMs(int dataLength, int sampleRateHz, int channels) {
        if (sampleRateHz <= 0 || channels <= 0 || dataLength <= 0) {
            return 0L;
        }
        double samplesPerChannel = dataLength / (double) (channels * 2);
        return Math.max(1L, Math.round(samplesPerChannel * 1000.0d / sampleRateHz));
    }

    private static final class PlaybackWaitState {
        private final boolean hasTrack;
        private final boolean playbackActive;
        @Nullable
        private final Long turnId;
        @Nullable
        private final String responseId;

        private PlaybackWaitState(boolean hasTrack, boolean playbackActive, @Nullable Long turnId, @Nullable String responseId) {
            this.hasTrack = hasTrack;
            this.playbackActive = playbackActive;
            this.turnId = turnId;
            this.responseId = responseId;
        }
    }

    /**
     * 首播门槛状态只在播放线程内部使用，避免多线程同步复杂化。
     */
    private static final class PlaybackGateState {
        @Nullable
        private Long turnId;
        @Nullable
        private String responseId;
        private boolean started;
        private boolean responseStopped;

        private void bind(@Nullable Long turnId, @Nullable String responseId) {
            this.turnId = turnId;
            this.responseId = normalizeResponseId(responseId);
            this.started = false;
            this.responseStopped = false;
        }

        private boolean matches(@Nullable Long turnId, @Nullable String responseId) {
            return Objects.equals(this.turnId, turnId)
                    && Objects.equals(this.responseId, normalizeResponseId(responseId));
        }

        private void onResponseStop(@Nullable Long turnId, @Nullable String responseId) {
            if (this.turnId == null && this.responseId == null) {
                bind(turnId, responseId);
                responseStopped = true;
                return;
            }
            if (!matches(turnId, responseId)) {
                return;
            }
            responseStopped = true;
        }

        private boolean canStart(int bufferedFrames) {
            if (bufferedFrames <= 0) {
                return false;
            }
            return bufferedFrames >= START_BUFFER_FRAMES || responseStopped;
        }

        private void reset() {
            turnId = null;
            responseId = null;
            started = false;
            responseStopped = false;
        }
    }

    @NonNull
    private static String displayValue(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim();
    }

    @NonNull
    private static String displayValue(@Nullable Number value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * 播放线程内部统一命令对象。
     */
    private static final class PlaybackItem {
        private static final int TYPE_PLAY = 1;
        private static final int TYPE_FLUSH_STOP = 2;
        private static final int TYPE_RELEASE = 3;
        private static final int TYPE_RESPONSE_STOP = 4;

        private final int type;
        private final int sampleRateHz;
        private final int channels;
        private final long seq;
        @Nullable
        private final Long turnId;
        @Nullable
        private final String responseId;
        @Nullable
        private final byte[] data;

        private PlaybackItem(
                int type,
                int sampleRateHz,
                int channels,
                long seq,
                @Nullable Long turnId,
                @Nullable String responseId,
                @Nullable byte[] data
        ) {
            this.type = type;
            this.sampleRateHz = sampleRateHz;
            this.channels = channels;
            this.seq = seq;
            this.turnId = turnId;
            this.responseId = responseId;
            this.data = data;
        }

        @NonNull
        private static PlaybackItem play(
                int sampleRateHz,
                int channels,
                long seq,
                @Nullable Long turnId,
                @Nullable String responseId,
                @NonNull byte[] data
        ) {
            return new PlaybackItem(TYPE_PLAY, sampleRateHz, channels, seq, turnId, responseId, data);
        }

        @NonNull
        private static PlaybackItem flushStop() {
            return new PlaybackItem(TYPE_FLUSH_STOP, 0, 0, 0L, null, null, null);
        }

        @NonNull
        private static PlaybackItem release() {
            return new PlaybackItem(TYPE_RELEASE, 0, 0, 0L, null, null, null);
        }

        @NonNull
        private static PlaybackItem responseStop(@Nullable Long turnId, @Nullable String responseId) {
            return new PlaybackItem(TYPE_RESPONSE_STOP, 0, 0, 0L, turnId, responseId, null);
        }
    }
}
