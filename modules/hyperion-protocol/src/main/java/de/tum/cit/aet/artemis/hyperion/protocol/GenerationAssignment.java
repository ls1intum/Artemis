package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable input to a single worker execution. The seed includes canonical Gradle harness files. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationAssignment(ExecutionIdentity identity, ExerciseBrief brief, GenerationParameters parameters, WorkspaceSnapshot seed, Instant authoringDeadline,
        String imageDigest, GradingContext gradingContext) {

    public GenerationAssignment {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(brief);
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(seed);
        Objects.requireNonNull(gradingContext);
        Objects.requireNonNull(authoringDeadline);
        if (imageDigest == null || !imageDigest.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Assignment must match an immutable worker image");
        }
    }
}
