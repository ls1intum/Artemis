package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Immutable input to a single worker execution. The seed includes the selected toolchain's canonical harness files. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationAssignment(ExecutionIdentity identity, ExerciseBrief brief, GenerationParameters parameters, WorkspaceSnapshot seed, Instant authoringDeadline,
        String imageDigest, GradingContext gradingContext) {

    /** Existing coordinator window for finishing verification after authoring has stopped. */
    public static final Duration AUTHORING_DRAIN_WINDOW = Duration.ofMinutes(5);

    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");

    public GenerationAssignment {
        if (identity == null || brief == null || parameters == null || seed == null || authoringDeadline == null || gradingContext == null) {
            throw new IllegalArgumentException("Assignment fields must be present");
        }
        if (imageDigest == null || !IMAGE_DIGEST.matcher(imageDigest).matches()) {
            throw new IllegalArgumentException("Assignment must match an immutable worker image");
        }
    }
}
