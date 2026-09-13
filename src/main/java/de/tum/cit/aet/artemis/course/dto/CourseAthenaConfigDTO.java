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
 * @param allowedFeedbackRequests  the instance-wide cap on successful automatic Athena feedback requests per
 *                                     participation ({@code artemis.athena.allowed-feedback-requests}); not editable
 *                                     through this DTO, shown read-only on the settings page's admin tab
 */
// @JsonInclude (ALWAYS) rather than the usual NON_EMPTY: NON_EMPTY drops a false primitive from the payload, which
// would leave the client unable to tell a disabled feature from a field the server did not send.
@JsonInclude
public record CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled, int allowedFeedbackRequests) {

    /**
     * Secondary constructor for {@link de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository#findConfigByCourseId},
     * whose JPQL projection reads only the two persisted switches: {@code allowedFeedbackRequests} is instance-wide
     * Spring configuration, not something a query against this entity can supply. Callers that need it read the
     * two-field result this constructor produces and build the full record themselves; see
     * {@code CourseAthenaConfigService#getConfig}.
     *
     * @param gradingFeedbackEnabled   whether Athena suggests feedback to tutors while they assess
     * @param formativeFeedbackEnabled whether students may request preliminary Athena feedback before the due date
     */
    public CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled) {
        this(gradingFeedbackEnabled, formativeFeedbackEnabled, 0);
    }
}
