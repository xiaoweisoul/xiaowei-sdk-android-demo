package com.xiaowei.sdk.demo;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xiaowei.sdk.sessioncore.PcmFrame;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo 侧正式的 assistant PCM 播放器。
 * 负责音频焦点、AudioTrack、后台写入线程以及空闲自动收口。
 */
final class AssistantPcmPlayer {
    private static final String TAG = "AssistantPcmPlayer";
    private static final int QUEUE_CAPACITY = 256;
    private static final long QUEUE_BACKPRESSURE_WAIT_MS = 200L;
    private static final long IDLE_STOP_TIMEOUT_MS = 5000L;
    private static final long RELEASE_JOIN_TIMEOUT_MS = 1500L;
    private static final float PLAYBACK_VOLUME = 1.0f;

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
    private boolean suppressUnknownResponseUntilNextKnown;

    @Nullable
    private AudioTrack audioTrack;
    @Nullable
    private AudioFocusRequest audioFocusRequest;
    private boolean audioFocusGranted;
    private boolean idleStateEntered;
    private int currentSampleRateHz = -1;
    private int currentChannels = -1;

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
                frame.getResponseId(),
                Arrays.copyOf(frame.getData(), frame.getData().length)
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
     * 在本地发送 interrupt=true 前，先停掉当前播放，并持续屏蔽旧 response 的尾包。
     */
    void interruptAndSuppressCurrentResponse() {
        if (released.get()) {
            return;
        }
        String responseId;
        synchronized (audioLock) {
            responseId = latestAcceptedResponseId;
            suppressedResponseId = responseId;
            suppressUnknownResponseUntilNextKnown = true;
        }
        if (responseId == null) {
            logLine("[TtsPlayer] 本地 interrupt=true：已停止播放，并在新 response 到来前丢弃旧尾包 PCM");
        } else {
            logLine("[TtsPlayer] 本地 interrupt=true：已停止播放，并屏蔽旧 responseId=" + responseId + " 的尾包");
        }
        flushAndStop();
    }

    /**
     * 在断开会话等场景下清空播放器状态，避免上一轮 response 影响后续连接。
     */
    void flushStopAndResetResponseState() {
        synchronized (audioLock) {
            latestAcceptedResponseId = null;
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
     * 后台播放线程主循环：消费 PCM、处理停止命令，并在空闲时释放音频焦点但保留 AudioTrack。
     */
    private void runLoop() {
        try {
            while (true) {
                PlaybackItem item = queue.pollFirst(IDLE_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (item == null) {
                    if (hasTrack()) {
                        enterIdleState("[AudioTrack空闲] 无声音播放，超出时限 " + IDLE_STOP_TIMEOUT_MS + "ms，释放音频焦点并保留 AudioTrack");
                    }
                    continue;
                }
                if (item.type == PlaybackItem.TYPE_RELEASE) {
                    break;
                }
                if (item.type == PlaybackItem.TYPE_FLUSH_STOP) {
                    enterIdleState("收到停止命令");
                    continue;
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
     * 播放单帧 PCM；必要时会自动申请音频焦点并复用 AudioTrack。
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
            ensureTrackPlaying(track);
            byte[] data = item.data;
            if (data == null || data.length == 0) {
                return;
            }
            int written = track.write(data, 0, data.length, AudioTrack.WRITE_BLOCKING);
            if (written < 0) {
                throw new IllegalStateException("AudioTrack.write failed: " + written);
            }
            if (written != data.length) {
                logLine("[TtsPlayer] PCM 写入不完整，seq=" + item.seq + " bytes=" + written + "/" + data.length);
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
            track.setVolume(PLAYBACK_VOLUME);
            audioTrack = track;
            currentSampleRateHz = sampleRateHz;
            currentChannels = channels;
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
            logLine("[TtsPlayer] 音频焦点丢失，停止当前播放");
            flushAndStop();
        }
    }

    /**
     * 进入静默态：保留 AudioTrack，释放音频焦点，减少设备功放和路由反复抖动。
     */
    private void enterIdleState(@NonNull String reason) {
        synchronized (audioLock) {
            boolean shouldLog = !idleStateEntered;
            idleStateEntered = true;
            pauseAndFlushAudioTrackLocked(shouldLog ? reason : null);
            abandonAudioFocusLocked();
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
     * 让当前 AudioTrack 停到静默态，但不销毁对象，避免频繁重建导致爆破音。
     */
    private void pauseAndFlushAudioTrackLocked(@Nullable String reason) {
        AudioTrack track = audioTrack;
        if (track == null) {
            return;
        }
        try {
            track.pause();
        } catch (Exception ignored) {
        }
        try {
            track.flush();
        } catch (Exception ignored) {
        }
        if (reason != null && !reason.isEmpty()) {
            logLine("[TtsPlayer] 停止播放: " + reason);
        }
    }

    /**
     * 彻底释放 AudioTrack，并重置当前播放参数。
     */
    private void releaseAudioTrackLocked(@Nullable String reason) {
        AudioTrack track = audioTrack;
        if (track == null) {
            currentSampleRateHz = -1;
            currentChannels = -1;
            return;
        }
        audioTrack = null;
        currentSampleRateHz = -1;
        currentChannels = -1;
        idleStateEntered = false;
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

    /**
     * 同时输出到 Demo 日志面板和 Logcat，方便现场排障。
     */
    private void logLine(@NonNull String message) {
        Log.i(TAG, message);
        logger.log(message);
    }

    /**
     * 播放线程内部统一命令对象。
     */
    private static final class PlaybackItem {
        private static final int TYPE_PLAY = 1;
        private static final int TYPE_FLUSH_STOP = 2;
        private static final int TYPE_RELEASE = 3;

        private final int type;
        private final int sampleRateHz;
        private final int channels;
        private final long seq;
        @Nullable
        private final String responseId;
        @Nullable
        private final byte[] data;

        private PlaybackItem(int type, int sampleRateHz, int channels, long seq, @Nullable String responseId, @Nullable byte[] data) {
            this.type = type;
            this.sampleRateHz = sampleRateHz;
            this.channels = channels;
            this.seq = seq;
            this.responseId = responseId;
            this.data = data;
        }

        @NonNull
        private static PlaybackItem play(int sampleRateHz, int channels, long seq, @Nullable String responseId, @NonNull byte[] data) {
            return new PlaybackItem(TYPE_PLAY, sampleRateHz, channels, seq, responseId, data);
        }

        @NonNull
        private static PlaybackItem flushStop() {
            return new PlaybackItem(TYPE_FLUSH_STOP, 0, 0, 0L, null, null);
        }

        @NonNull
        private static PlaybackItem release() {
            return new PlaybackItem(TYPE_RELEASE, 0, 0, 0L, null, null);
        }
    }
}
