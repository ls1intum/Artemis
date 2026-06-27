package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-user struggle event pushed to {@code /user/topic/iris/struggle-intervention} (spec §5.5). {@code action} is
 * {@code "ambient"} (lamp, {@code message} holds the lamp text) or {@code "active"} (chat bubble). {@code "silent"}
 * is never sent. After unify-persistence (spec §7) both {@code ambient} and {@code active} persist a proactive
 * message into the shared exercise-chat session, so both carry {@code sessionId} + {@code messageId} pointing at
 * that saved message (lets a later slice open/reveal/dismiss it). {@code confidence} is the server-computed Pyris
 * confidence, forwarded for the client eval log (spec §12) on both actions. Every payload field beyond
 * {@code exerciseId}/{@code action} is {@code @Nullable} so a partial push still serializes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StruggleInterventionEventDTO(long exerciseId, String action, @Nullable String message, @Nullable Long sessionId, @Nullable Long messageId,
        @Nullable Double confidence) {
}
