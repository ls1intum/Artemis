package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Administrator-only live generation summary; no prompts, repository contents or credentials. {@code mode}, {@code exerciseTitle} and {@code courseId} tell an administrator
 * what kind of run it is and where it belongs without a second lookup; they are {@code null} for slots recorded before this context was tracked.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveGenerationDTO(String jobId, long exerciseId, String userLogin, Instant startedAt, boolean cancellable, boolean cancellationRequested,
        @Nullable GenerationMode mode, @Nullable String exerciseTitle, @Nullable Long courseId) {
}
