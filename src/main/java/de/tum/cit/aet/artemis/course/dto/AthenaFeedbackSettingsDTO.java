package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The two Athena switches of a course, read on their own rather than through the lazy configuration on the course.
 *
 * @param gradingFeedbackEnabled   whether tutors are offered Athena feedback suggestions while assessing
 * @param formativeFeedbackEnabled whether students may request AI feedback
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AthenaFeedbackSettingsDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled) {

    /** Both switched off, for a course that has no Athena configuration row. */
    public static final AthenaFeedbackSettingsDTO DISABLED = new AthenaFeedbackSettingsDTO(false, false);
}
