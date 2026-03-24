package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

public record TestFeedbackInput(long testId, @Size(max = 500) String testName, boolean passed, @Nullable @Size(max = 5000) String message, @Nullable Double credits) {
}
