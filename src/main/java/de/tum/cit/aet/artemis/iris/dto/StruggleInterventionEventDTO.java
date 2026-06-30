package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-user struggle event pushed to {@code /user/topic/iris/struggle-intervention} (spec §5.5). {@code kind} is the
 * event discriminator ({@code "decide"} | {@code "confirm_close"} | {@code "stale_check"}); A11 adds the latter two.
 * {@code action} is {@code "ambient"} (lamp, {@code message} holds the hint text) or {@code "active"} (chat bubble) or
 * {@code "silent"} (noop completion frame). After the pull-model change (spec §5, A9) ambient is event-only: it no
 * longer persists a proactive message; the client holds the text frozen and reveals it on click (A10/C2). Active still
 * persists and pushes a chat-ws bubble. Both carry {@code sessionId} so the client knows which session to target.
 * Active carries {@code messageId} when persist succeeded (null on permanent failure, client renders a runtime-only
 * fallback bubble). Silent carries neither. {@code confidence} is the server-computed Pyris confidence, forwarded for
 * the client eval log (spec §12). {@code anchorFile}/{@code anchorLine}/{@code inlineHint} are set only when the gate
 * localized the nudge to a single line (spec §4/§8). {@code episodeId} is the client-allocated UUID that correlates
 * this event back to the outstanding slot request. Every payload field beyond {@code exerciseId}/{@code kind}/
 * {@code action} is {@code @Nullable} so a partial push still serializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionEventDTO(long exerciseId, String kind, String action, @Nullable String message, @Nullable Long sessionId, @Nullable Long messageId,
        @Nullable String anchorFile, @Nullable Integer anchorLine, @Nullable String inlineHint, @Nullable Double confidence, @Nullable String episodeId) {
}
