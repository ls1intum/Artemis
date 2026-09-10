package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-user struggle event pushed to {@code /user/topic/iris/struggle-intervention}. {@code kind} discriminates
 * {@code "decide"} from {@code "confirm_close"}. On a decide, {@code action} is {@code "ambient"} (event-only, the
 * client holds {@code message} frozen and reveals it on click), {@code "active"} (a persisted, pushed bubble) or
 * {@code "silent"} (a noop completion frame); it stays null for confirm_close. Active carries {@code messageId} when
 * the persist succeeded and null on permanent failure, where the client renders a runtime-only fallback bubble.
 *
 * <p>
 * {@code confidence} and {@code rationale} are forwarded for the client's eval log and never shown to the student,
 * which matters most for a {@code silent} run, where the detector fired and the gate still surfaced nothing.
 * {@code anchorFile}, {@code anchorLine} and {@code inlineHint} are set only when the gate localized the nudge to a
 * single line. {@code episodeId} correlates the event back to the outstanding slot request. A confirm_close carries
 * {@code resolved} and, when it is true, {@code closingSentence} and {@code episodeLabel}.
 *
 * <p>
 * Every payload field beyond {@code exerciseId} and {@code kind} is nullable, so a partial push still serializes.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionEventDTO(long exerciseId, String kind, @Nullable String action, @Nullable String message, @Nullable Long sessionId, @Nullable Long messageId,
        @Nullable String anchorFile, @Nullable Integer anchorLine, @Nullable String inlineHint, @Nullable Double confidence, @Nullable String episodeId, @Nullable Boolean resolved,
        @Nullable String closingSentence, @Nullable String episodeLabel, @Nullable String rationale) {

    /**
     * The noop completion frame for a {@code decide} run that surfaces nothing. A named factory rather than the
     * positional constructor, because fifteen adjacent nullable fields are easy to shift by one without the compiler
     * noticing, which is how the empty-result frame once lost the confidence the client logs for the eval.
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
     * The bare completion frame for a {@code confirm_close} run that resolved nothing. {@code resolved=false} rather
     * than null, because a run that ended without resolving must not read as a resolved episode. Every path that
     * commits neither a closing row nor a {@code RECOVERED} outcome goes through here, including the ones Pyris
     * answered {@code resolved=true} for: the gate's verdict is not the same fact as a committed close.
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
