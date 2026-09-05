package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-user struggle event pushed to {@code /user/topic/iris/struggle-intervention}. {@code kind} is the
 * event discriminator ({@code "decide"} | {@code "confirm_close"}); A11 added the latter.
 * {@code action} is {@code "ambient"} (lamp, {@code message} holds the hint text) or {@code "active"} (chat bubble) or
 * {@code "silent"} (noop completion frame); null for confirm_close events. After the pull-model change
 * Ambient is event-only: no proactive message is persisted. The client holds the text frozen and
 * reveals it on click (A10/C2). Active still persists and pushes a chat-ws bubble. Both carry {@code sessionId} so the
 * client knows which session to target. Active carries {@code messageId} when persist succeeded (null on permanent
 * failure, client renders a runtime-only fallback bubble). Silent carries neither. {@code confidence} is the
 * server-computed Pyris confidence, forwarded for the client eval log. {@code anchorFile}/
 * {@code anchorLine}/{@code inlineHint} are set only when the gate localized the nudge to a single line.
 * {@code episodeId} is the client-allocated UUID that correlates this event back to the outstanding slot request.
 * {@code rationale} is the gate's own one-sentence reason for the decision. It is never shown to the student; it rides
 * alongside {@code confidence} so the client's eval log records WHY a run decided as it did, which matters most for a
 * {@code silent} run, where the detector fired and the gate still surfaced nothing.
 *
 * <p>
 * A11 confirm_close payload fields:
 * <ul>
 * <li>{@code resolved}: boolean result for confirm_close events.</li>
 * <li>{@code closingSentence}: closing praise for a {@code resolved=true} confirm_close (progress).</li>
 * <li>{@code episodeLabel}: episode label for a {@code resolved=true} confirm_close (progress).</li>
 * </ul>
 *
 * Every payload field beyond {@code exerciseId}/{@code kind} is {@code @Nullable} so a partial push still serializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionEventDTO(long exerciseId, String kind, @Nullable String action, @Nullable String message, @Nullable Long sessionId, @Nullable Long messageId,
        @Nullable String anchorFile, @Nullable Integer anchorLine, @Nullable String inlineHint, @Nullable Double confidence, @Nullable String episodeId, @Nullable Boolean resolved,
        @Nullable String closingSentence, @Nullable String episodeLabel, @Nullable String rationale) {

    /**
     * The noop completion frame for a {@code decide} run that surfaces nothing, so the client's in-flight decide
     * clears. Fifteen positional fields, most of them nullable and adjacent, are easy to shift by one without the
     * compiler noticing - which is exactly what happened to the empty-result frame, where a {@code null} sat in the
     * {@code confidence} slot and the client silently lost the value it logs for the eval.
     *
     * @param exerciseId the exercise the run belongs to
     * @param confidence the gate confidence, forwarded for the client eval log; null when no decision produced one
     * @param episodeId  the client-allocated episode id, or null when the run carried none
     * @param rationale  the gate's reason for staying silent, for the eval log; null when the run produced none
     * @return the silent completion event
     */
    public static StruggleInterventionEventDTO silentDecide(long exerciseId, @Nullable Double confidence, @Nullable String episodeId, @Nullable String rationale) {
        return new StruggleInterventionEventDTO(exerciseId, "decide", "silent", null, null, null, null, null, null, confidence, episodeId, null, null, null, rationale);
    }

    /**
     * The bare completion frame for a {@code confirm_close} run that resolved nothing, the close-mode counterpart to
     * {@link #silentDecide}. {@code resolved=false} rather than null: a run that ended without resolving must not
     * read as a resolved episode. Every confirm_close path that commits neither a closing row nor a {@code RECOVERED}
     * outcome goes through here, including the ones where Pyris itself answered {@code resolved=true}: the gate's
     * verdict is not the same fact as a committed close, and forwarding it told the client an episode had recovered
     * that carried no closing row and no outcome.
     *
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, or null when the run carried none
     * @param rationale  the gate's reason, forwarded for the client eval log; null when the run produced none
     * @return the unresolved completion event
     */
    public static StruggleInterventionEventDTO unresolvedClose(long exerciseId, @Nullable String episodeId, @Nullable String rationale) {
        return new StruggleInterventionEventDTO(exerciseId, "confirm_close", null, null, null, null, null, null, null, null, episodeId, false, null, null, rationale);
    }

    /**
     * The completion frame for a run that ended without producing a decision at all, shaped by its intent: a
     * {@code confirm_close} completes as {@link #unresolvedClose}, everything else (including the legacy null intent)
     * as {@link #silentDecide}. Both the callback handler and the dispatch-failure path emit this, from two services
     * that cannot depend on each other; deciding the shape here is what keeps them from drifting apart. The episode
     * id is normalised, because an id that cannot serve as an identity must not reach the client as one.
     *
     * @param intent     the job's intent, possibly null
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the episode id stamped on the job, possibly null or unusable
     * @return the terminal completion event for that intent
     */
    public static StruggleInterventionEventDTO terminalCompletion(@Nullable String intent, long exerciseId, @Nullable String episodeId) {
        var usable = StruggleEpisodeDTO.usableEpisodeId(episodeId);
        return "confirm_close".equals(intent) ? unresolvedClose(exerciseId, usable, null) : silentDecide(exerciseId, null, usable, null);
    }
}
