package de.tum.cit.aet.artemis.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The course-level Athena configuration an instructor can edit, exchanged by
 * {@link de.tum.cit.aet.artemis.course.web.CourseAthenaConfigResource}.
 * <p>
 * Deliberately separate from {@link CourseUpdateDTO}: the toggles live on the course overview and in the onboarding
 * wizard and write immediately, so routing them through the whole-course update would let a stale settings form
 * overwrite what was just toggled.
 *
 * @param gradingFeedbackEnabled   whether Athena suggests feedback to tutors while they assess
 * @param formativeFeedbackEnabled whether students may request preliminary Athena feedback before the due date
 */
// @JsonInclude (ALWAYS) rather than the usual NON_EMPTY: NON_EMPTY drops a false primitive from the payload, which
// would leave the client unable to tell a disabled feature from a field the server did not send.
@JsonInclude
public record CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled) {
}
