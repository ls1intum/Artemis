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
 * @param allowedFeedbackRequests  the instance-wide cap on successful automatic Athena feedback requests per
 *                                     participation ({@code artemis.athena.allowed-feedback-requests}); not editable
 *                                     through this DTO, shown read-only on the settings page's admin tab
 */
// @JsonInclude (ALWAYS) rather than the usual NON_EMPTY: NON_EMPTY drops a false primitive from the payload, which
// would leave the client unable to tell a disabled feature from a field the server did not send.
@JsonInclude
public record CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled, int defaultFeedbackDetail, int defaultFeedbackFormality,
        int allowedFeedbackRequests) {

    /**
     * Secondary constructor for {@link de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository#findConfigByCourseId},
     * whose JPQL projection reads only the persisted columns: {@code allowedFeedbackRequests} is instance-wide Spring
     * configuration, not something a query against this entity can supply. Callers that need it read the result this
     * constructor produces and build the full record themselves; see {@code CourseAthenaConfigService#getConfig}.
     *
     * @param gradingFeedbackEnabled   whether Athena suggests feedback to tutors while they assess
     * @param formativeFeedbackEnabled whether students may request preliminary Athena feedback before the due date
     * @param defaultFeedbackDetail    the course default for feedback detail; 0 means no course default is set
     * @param defaultFeedbackFormality the course default for feedback formality; 0 means no course default is set
     */
    public CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled, int defaultFeedbackDetail, int defaultFeedbackFormality) {
        this(gradingFeedbackEnabled, formativeFeedbackEnabled, defaultFeedbackDetail, defaultFeedbackFormality, 0);
    }
}
