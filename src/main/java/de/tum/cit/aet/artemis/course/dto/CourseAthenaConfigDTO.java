package de.tum.cit.aet.artemis.course.dto;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;

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
// Deliberately not @JsonInclude(NON_EMPTY): that drops false booleans from the payload, leaving the client unable to
// tell a disabled feature from an absent field.
public record CourseAthenaConfigDTO(boolean gradingFeedbackEnabled, boolean formativeFeedbackEnabled) {

    /**
     * Reads the configuration off a course, treating a course without a config row as fully disabled.
     *
     * @param course the course to read the Athena configuration from
     * @return the course's Athena configuration
     */
    public static CourseAthenaConfigDTO from(Course course) {
        CourseAthenaConfig config = course.getAthenaConfig();
        if (config == null) {
            return new CourseAthenaConfigDTO(false, false);
        }
        return new CourseAthenaConfigDTO(config.isGradingFeedbackEnabled(), config.isFormativeFeedbackEnabled());
    }
}
