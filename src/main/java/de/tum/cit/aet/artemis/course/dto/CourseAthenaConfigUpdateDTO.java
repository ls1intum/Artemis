package de.tum.cit.aet.artemis.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;

/**
 * A change to the course-level Athena configuration, sent by
 * {@link de.tum.cit.aet.artemis.course.web.CourseAthenaConfigResource}.
 * <p>
 * Each field is optional and {@code null} means "leave this feature as it is". Every field is switched independently
 * and saves on click, so a request names only the field that was actually changed. Sending the whole configuration
 * instead would make every change rewrite the other fields from the value the client last saw, and a second
 * instructor changing another field at the same time would have their change silently undone.
 *
 * @param gradingFeedbackEnabled   whether Athena suggests feedback to tutors while they assess, or null to leave it
 * @param formativeFeedbackEnabled whether students may request preliminary Athena feedback, or null to leave it
 * @param defaultFeedbackDetail    the course default for feedback detail (1-3), or 0 to clear it back to "no course
 *                                     default", or null to leave it as it is
 * @param defaultFeedbackFormality the course default for feedback formality, same scale and null/0 meaning as
 *                                     {@code defaultFeedbackDetail}
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAthenaConfigUpdateDTO(@Nullable Boolean gradingFeedbackEnabled, @Nullable Boolean formativeFeedbackEnabled,
        @Nullable @Min(CourseAthenaConfig.FEEDBACK_STYLE_NOT_SET) @Max(CourseAthenaConfig.MAX_FEEDBACK_STYLE_VALUE) Integer defaultFeedbackDetail,
        @Nullable @Min(CourseAthenaConfig.FEEDBACK_STYLE_NOT_SET) @Max(CourseAthenaConfig.MAX_FEEDBACK_STYLE_VALUE) Integer defaultFeedbackFormality) {
}
