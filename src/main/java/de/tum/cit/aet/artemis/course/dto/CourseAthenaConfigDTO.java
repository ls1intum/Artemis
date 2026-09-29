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
 * @param defaultFeedbackDetail    the course default for how detailed Athena feedback reads, on the same 1-3 scale as
 *                                     {@code LearnerProfile.feedbackDetail}; 0 means no course default is set. Applies
 *                                     only to a student who has not set their own feedback preference.
 * @param defaultFeedbackFormality the course default for how formal Athena feedback reads, same scale and 0 meaning
 *                                     as {@code defaultFeedbackDetail}.
 */
// @JsonInclude (ALWAYS) rather than the usual NON_EMPTY: NON_EMPTY drops a false primitive from the payload, which
// would leave the client unable to tell a disabled feature from a field the server did not send.
@JsonInclude
public record CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled, int defaultFeedbackDetail, int defaultFeedbackFormality) {
}
