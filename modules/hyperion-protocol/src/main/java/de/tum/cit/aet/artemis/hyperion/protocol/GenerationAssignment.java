package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable input to a single worker execution. The seed includes canonical Gradle harness files. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationAssignment(ExecutionIdentity identity, ExerciseBrief brief, GenerationParameters parameters, WorkspaceSnapshot seed, Instant authoringDeadline,
        String imageDigest) {

    public GenerationAssignment {
        if (identity == null || brief == null || parameters == null || seed == null || authoringDeadline == null) {
            throw new IllegalArgumentException("Assignment fields must be present");
        }
        if (imageDigest == null || !imageDigest.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Assignment must match an immutable worker image");
        }
    }
}
