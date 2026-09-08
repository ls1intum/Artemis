package de.tum.cit.aet.artemis.hyperion.protocol;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bounded cumulative activity; contains no prompts, source content or provider credentials. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationActivity(@Nullable String step, int attempt, int turn, boolean waitingOnModel, int modelCalls, int toolCalls, int filesWritten) {
}
