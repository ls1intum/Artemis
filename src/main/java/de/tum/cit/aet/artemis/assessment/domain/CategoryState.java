package de.tum.cit.aet.artemis.assessment.domain;

/**
 * Enumeration for category states used by StaticCodeAnalysisCategory.
 * <ul>
 * <li>{@code INACTIVE}: issues in this category are discarded entirely.</li>
 * <li>{@code FEEDBACK}: issues are shown to the student but cost no points.</li>
 * <li>{@code GRADED}: issues are shown and deduct the category's penalty per issue.</li>
 * <li>{@code BLOCKING}: issues are shown and a single one of them zeroes the score of the exercise whose configuration
 * declared the category. For a {@code MilestoneExercise} that is the whole group's aggregated score, so one blocking
 * violation anywhere in the shared codebase costs every user story of the group.</li>
 * </ul>
 */
public enum CategoryState {
    INACTIVE, FEEDBACK, GRADED, BLOCKING
}
