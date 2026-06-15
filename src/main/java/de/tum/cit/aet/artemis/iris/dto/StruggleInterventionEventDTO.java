package de.tum.cit.aet.artemis.iris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Session-less per-user struggle event pushed to {@code /user/topic/iris/struggle-intervention} (spec §5.5).
 * {@code action} is {@code "ambient"} (lamp, {@code message} set, no session) or {@code "active"}
 * ({@code sessionId} set so the extension opens/fetches the proactive bubble). {@code "silent"} is never sent.
 * {@code confidence} is the server-computed Pyris confidence, forwarded for the client eval log (spec §12) on
 * BOTH ambient and active — it is the only client-side observation point for ambient confidence (ambient is
 * never persisted as an {@code LLM} message). {@code @Nullable} so a future confidence-less push still serializes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StruggleInterventionEventDTO(long exerciseId, String action, @Nullable String message, @Nullable Long sessionId, @Nullable Double confidence) {
}
