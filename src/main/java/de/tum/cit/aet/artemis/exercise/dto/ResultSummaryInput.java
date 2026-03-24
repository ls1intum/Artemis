package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

public record ResultSummaryInput(@Nullable Double score, @Nullable Double maxPoints, @Nullable Double bonusPoints, @Nullable @Size(max = 100) String commitHash,
        @Nullable @Size(max = 100) String submissionDate, @Nullable @Size(max = 50) String assessmentType) {
}
