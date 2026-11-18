package de.tum.cit.aet.artemis.course.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A change to the course-level Athena configuration, sent by
 * {@link de.tum.cit.aet.artemis.course.web.CourseAthenaConfigResource}.
 * <p>
 * Each field is optional and {@code null} means "leave this feature as it is". The two features are switched
 * independently and save on click, so a request names only the feature that was actually switched. Sending the whole
 * configuration instead would make every switch rewrite the other feature from the value the client last saw, and a
 * second instructor switching the other feature at the same time would have their change silently undone.
 *
 * @param gradingFeedbackEnabled   whether Athena suggests feedback to tutors while they assess, or null to leave it
 * @param formativeFeedbackEnabled whether students may request preliminary Athena feedback, or null to leave it
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseAthenaConfigUpdateDTO(@Nullable Boolean gradingFeedbackEnabled, @Nullable Boolean formativeFeedbackEnabled) {
}
