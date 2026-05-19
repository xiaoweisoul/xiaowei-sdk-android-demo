package vip.xiaoweisoul.sdk.demo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import vip.xiaoweisoul.sdk.sessioncore.AssistantSentenceEvent;
import vip.xiaoweisoul.sdk.sessioncore.PcmFrame;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo 侧的最小回复跟踪器。
 * 负责按 responseId 聚合多句文本，并识别一轮回复的首帧 PCM，便于把时序日志打清楚。
 */
final class AssistantResponseTracker {
    private static final int MAX_ACTIVE_RESPONSES = 8;
    private static final int MAX_TEXT_PREVIEW_CHARS = 160;

    private final LinkedHashMap<String, ResponseState> activeResponses = new LinkedHashMap<>();

    /**
     * 记录一条 AI 文本句子，并返回当前聚合后的快照。
     */
    @NonNull
    SentenceSnapshot recordSentenceStart(@NonNull AssistantSentenceEvent event) {
        ResponseState state = getOrCreateState(event.getTurnId(), event.getResponseId());
        state.turnId = event.getTurnId();
        state.responseId = normalizeResponseId(event.getResponseId());
        String text = safe(event.getText()).trim();
        if (!text.isEmpty()) {
            state.sentenceCount += 1;
            state.appendText(text);
        }
        return new SentenceSnapshot(
                state.turnId,
                state.responseId,
                state.sentenceCount,
                text,
                state.buildTextPreview()
        );
    }

    /**
     * 在一轮回复结束时返回当前聚合摘要，并把该回复从活跃集合中移除。
     */
    @NonNull
    ResponseSummary recordSentenceStop(@NonNull AssistantSentenceEvent event) {
        String key = buildResponseKey(event.getTurnId(), event.getResponseId());
        ResponseState state = activeResponses.remove(key);
        if (state == null) {
            return new ResponseSummary(
                    event.getTurnId(),
                    normalizeResponseId(event.getResponseId()),
                    0,
                    ""
            );
        }
        return new ResponseSummary(
                state.turnId,
                state.responseId,
                state.sentenceCount,
                state.buildTextPreview()
        );
    }

    /**
     * 标记一帧 PCM 是否是当前回复首次被观测到的首帧。
     */
    @NonNull
    PcmObservation observePcm(@NonNull PcmFrame frame) {
        ResponseState state = getOrCreateState(frame.getTurnId(), frame.getResponseId());
        state.turnId = frame.getTurnId();
        state.responseId = normalizeResponseId(frame.getResponseId());
        boolean firstFrame = !state.firstPcmObserved;
        state.firstPcmObserved = true;
        return new PcmObservation(
                state.turnId,
                state.responseId,
                frame.getSeq(),
                frame.getSamplesPerChannel(),
                firstFrame
        );
    }

    /**
     * 在断开会话等场景下清空当前聚合状态，避免旧 response 污染下一轮连接。
     */
    void reset() {
        activeResponses.clear();
    }

    @NonNull
    private ResponseState getOrCreateState(@Nullable Long turnId, @Nullable String responseId) {
        String key = buildResponseKey(turnId, responseId);
        ResponseState state = activeResponses.get(key);
        if (state != null) {
            return state;
        }
        trimActiveResponsesIfNeeded();
        ResponseState created = new ResponseState(turnId, normalizeResponseId(responseId));
        activeResponses.put(key, created);
        return created;
    }

    private void trimActiveResponsesIfNeeded() {
        if (activeResponses.size() < MAX_ACTIVE_RESPONSES) {
            return;
        }
        Iterator<Map.Entry<String, ResponseState>> iterator = activeResponses.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    @NonNull
    private static String buildResponseKey(@Nullable Long turnId, @Nullable String responseId) {
        String normalizedResponseId = normalizeResponseId(responseId);
        if (normalizedResponseId != null) {
            return "response:" + normalizedResponseId;
        }
        if (turnId != null) {
            return "turn:" + turnId;
        }
        return "unknown";
    }

    @Nullable
    private static String normalizeResponseId(@Nullable String responseId) {
        if (responseId == null) {
            return null;
        }
        String normalized = responseId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    static final class SentenceSnapshot {
        @Nullable
        private final Long turnId;
        @Nullable
        private final String responseId;
        private final int sentenceCount;
        @NonNull
        private final String latestText;
        @NonNull
        private final String textPreview;

        SentenceSnapshot(
                @Nullable Long turnId,
                @Nullable String responseId,
                int sentenceCount,
                @NonNull String latestText,
                @NonNull String textPreview
        ) {
            this.turnId = turnId;
            this.responseId = responseId;
            this.sentenceCount = sentenceCount;
            this.latestText = latestText;
            this.textPreview = textPreview;
        }

        @Nullable
        Long getTurnId() {
            return turnId;
        }

        @Nullable
        String getResponseId() {
            return responseId;
        }

        int getSentenceCount() {
            return sentenceCount;
        }

        @NonNull
        String getLatestText() {
            return latestText;
        }

        @NonNull
        String getTextPreview() {
            return textPreview;
        }
    }

    static final class ResponseSummary {
        @Nullable
        private final Long turnId;
        @Nullable
        private final String responseId;
        private final int sentenceCount;
        @NonNull
        private final String textPreview;

        ResponseSummary(@Nullable Long turnId, @Nullable String responseId, int sentenceCount, @NonNull String textPreview) {
            this.turnId = turnId;
            this.responseId = responseId;
            this.sentenceCount = sentenceCount;
            this.textPreview = textPreview;
        }

        @Nullable
        Long getTurnId() {
            return turnId;
        }

        @Nullable
        String getResponseId() {
            return responseId;
        }

        int getSentenceCount() {
            return sentenceCount;
        }

        @NonNull
        String getTextPreview() {
            return textPreview;
        }

        boolean hasText() {
            return !textPreview.isEmpty();
        }
    }

    static final class PcmObservation {
        @Nullable
        private final Long turnId;
        @Nullable
        private final String responseId;
        private final long seq;
        private final int samplesPerChannel;
        private final boolean firstFrame;

        PcmObservation(
                @Nullable Long turnId,
                @Nullable String responseId,
                long seq,
                int samplesPerChannel,
                boolean firstFrame
        ) {
            this.turnId = turnId;
            this.responseId = responseId;
            this.seq = seq;
            this.samplesPerChannel = samplesPerChannel;
            this.firstFrame = firstFrame;
        }

        @Nullable
        Long getTurnId() {
            return turnId;
        }

        @Nullable
        String getResponseId() {
            return responseId;
        }

        long getSeq() {
            return seq;
        }

        int getSamplesPerChannel() {
            return samplesPerChannel;
        }

        boolean isFirstFrame() {
            return firstFrame;
        }
    }

    /**
     * 单轮回复的最小聚合状态。
     */
    private static final class ResponseState {
        @Nullable
        private Long turnId;
        @Nullable
        private String responseId;
        private int sentenceCount;
        private boolean firstPcmObserved;
        @NonNull
        private final StringBuilder fullText = new StringBuilder();

        private ResponseState(@Nullable Long turnId, @Nullable String responseId) {
            this.turnId = turnId;
            this.responseId = responseId;
        }

        private void appendText(@NonNull String text) {
            if (fullText.length() > 0) {
                fullText.append('\n');
            }
            fullText.append(text);
        }

        @NonNull
        private String buildTextPreview() {
            if (fullText.length() <= MAX_TEXT_PREVIEW_CHARS) {
                return fullText.toString();
            }
            return fullText.substring(0, MAX_TEXT_PREVIEW_CHARS) + "…";
        }
    }
}
