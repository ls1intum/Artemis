package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictIntegerDeserializer;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Data Transfer Object for updating an existing course.
 * <p>
 * This DTO is used instead of directly deserializing to a Course entity to prevent
 * potential issues with entity state management, particularly:
 * <ul>
 * <li>Avoiding unintended orphan removal of child entities (e.g., tutorial groups)</li>
 * <li>The server controls which fields can be updated</li>
 * <li>Internal entity state (e.g., relationships, computed fields) is not exposed</li>
 * </ul>
 * <p>
 * The {@link #applyTo(Course)} method applies the DTO values to an existing Course entity.
 *
 * @see de.tum.cit.aet.artemis.course.web.CourseUpdateResource#updateCourse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseUpdateDTO(
        // ID is required for update
        @NotNull Long id,

        // Basic info
        @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Size(max = 2000) String description, String semester,

        // Dates
        ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate, ZonedDateTime unenrollmentEndDate,

        // Configuration flags
        boolean testCourse, Boolean onlineCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage,

        // Complaint settings
        Integer maxComplaints, Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit,
        int maxComplaintResponseTextLimit,

        // UI settings
        String color, String courseIcon, Boolean enrollmentEnabled, @Size(max = 2000) String enrollmentConfirmationMessage, boolean unenrollmentEnabled,
        String courseInformationSharingMessagingCodeOfConduct,

        // Course features
        boolean learningPathsEnabled, @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer presentationScore,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxPoints, @Min(0) @Max(5) Integer accuracyOfScores, boolean athenaGradingFeedbackEnabled,
        boolean athenaFormativeFeedbackEnabled, String timeZone, CourseInformationSharingConfiguration courseInformationSharingConfiguration, boolean onboardingDone,

        // Data-privacy / retention: whether the course is grade-relevant (drives how long student data is retained).
        // Boxed so an omitted value fails safe to grade-relevant (the longer retention), not to earlier deletion.
        Boolean gradeRelevant,

        // Data-privacy / retention: whether a pending objection or legal proceeding suspends the cleanup for this course.
        // Boxed so an omitted value fails safe to keeping an existing hold rather than silently lifting it.
        Boolean dataRetentionHold,

        // Atlas auto-orchestration configuration (per-course): kill switch plus nullable overrides.
        boolean autoOrchestratorEnabled, @Min(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer debounceWindowSecondsOverride,
        @Min(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxDailyOrchestrationOverride) {

    /**
     * Applies the DTO values to an existing Course entity.
     * <p>
     * This method updates only the fields that are part of the DTO, leaving
     * other entity state (like tutorial groups, exercises, etc.) untouched.
     *
     * @param course the existing course entity to update
     * @return the updated course entity
     */
    public Course applyTo(Course course) {
        // Basic info
        course.setTitle(title);
        course.setShortName(shortName);
        course.setDescription(description);
        course.setSemester(semester);

        // Dates
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setEnrollmentStartDate(enrollmentStartDate);
        course.setEnrollmentEndDate(enrollmentEndDate);
        course.setUnenrollmentEndDate(unenrollmentEndDate);

        // Configuration flags
        course.setTestCourse(testCourse);
        course.setOnlineCourse(onlineCourse != null && onlineCourse);
        course.setLanguage(language);
        course.setDefaultProgrammingLanguage(defaultProgrammingLanguage);

        // Complaint settings
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setMaxRequestMoreFeedbackTimeDays(maxRequestMoreFeedbackTimeDays);
        course.setMaxComplaintTextLimit(maxComplaintTextLimit);
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit);

        // UI settings
        course.setColor(color);
        course.setCourseIcon(courseIcon);
        course.setEnrollmentEnabled(enrollmentEnabled);
        course.setEnrollmentConfirmationMessage(enrollmentConfirmationMessage);
        course.setUnenrollmentEnabled(unenrollmentEnabled);
        course.setCourseInformationSharingMessagingCodeOfConduct(courseInformationSharingMessagingCodeOfConduct);

        // Course features
        course.setLearningPathsEnabled(learningPathsEnabled);
        course.setPresentationScore(presentationScore);
        course.setMaxPoints(maxPoints);
        course.setAccuracyOfScores(accuracyOfScores);
        if (course.getAthenaConfig() == null) {
            course.setAthenaConfig(new CourseAthenaConfig());
        }
        course.getAthenaConfig().setGradingFeedbackEnabled(athenaGradingFeedbackEnabled);
        course.getAthenaConfig().setFormativeFeedbackEnabled(athenaFormativeFeedbackEnabled);
        course.setTimeZone(timeZone);
        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        // Enforce the auto-orchestration override bounds server-side: the @Min(1) bean-validation annotations are not
        // active here (the multipart update endpoint does not run @Valid), so a crafted request could otherwise persist
        // zero/negative overrides that the scheduler would treat as invalid configuration.
        if ((debounceWindowSecondsOverride != null && debounceWindowSecondsOverride < 1) || (maxDailyOrchestrationOverride != null && maxDailyOrchestrationOverride < 1)) {
            throw new BadRequestAlertException("Auto-orchestration overrides must be positive", Course.ENTITY_NAME, "invalidAutoOrchestrationOverride", true);
        }

        // Only allow transitioning from false to true (one-way)
        if (onboardingDone) {
            course.setOnboardingDone(true);
        }

        // Update the course's configuration (data-retention flags plus the Atlas auto-orchestration settings), creating
        // it if absent. The course must be loaded with its (lazy) configuration for this to update in place instead of
        // creating a duplicate.
        CourseConfiguration configuration = course.getCourseConfiguration();
        if (configuration == null) {
            configuration = new CourseConfiguration();
            configuration.setCourse(course);
            course.setCourseConfiguration(configuration);
        }
        // Fail safe to grade-relevant (longer retention) when the client omits the flag.
        configuration.setGradeRelevant(gradeRelevant == null || gradeRelevant);
        // Fail safe to keeping an existing hold: an omitted flag must never lift a legal hold and expose the course to
        // the cleanup again.
        configuration.setDataRetentionHold(dataRetentionHold == null ? configuration.isDataRetentionHold() : dataRetentionHold);
        configuration.setAutoOrchestratorEnabled(autoOrchestratorEnabled);
        configuration.setDebounceWindowSecondsOverride(debounceWindowSecondsOverride);
        configuration.setMaxDailyOrchestrationOverride(maxDailyOrchestrationOverride);

        return course;
    }

    /**
     * Creates a CourseUpdateDTO from an existing Course entity.
     *
     * @param course the course entity to convert
     * @return a new CourseUpdateDTO with values from the course
     */
    public static CourseUpdateDTO of(Course course) {
        return new CourseUpdateDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getSemester(), course.getStartDate(),
                course.getEndDate(), course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.isTestCourse(),
                course.isOnlineCourse(), course.getLanguage(), course.getDefaultProgrammingLanguage(), course.getMaxComplaints(), course.getMaxTeamComplaints(),
                course.getMaxComplaintTimeDays(), course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(),
                course.getColor(), course.getCourseIcon(), course.isEnrollmentEnabled(), course.getEnrollmentConfirmationMessage(), course.isUnenrollmentEnabled(),
                course.getCourseInformationSharingMessagingCodeOfConduct(), course.getLearningPathsEnabled(), course.getPresentationScore(), course.getMaxPoints(),
                course.getAccuracyOfScores(), course.getAthenaConfig() != null && course.getAthenaConfig().isGradingFeedbackEnabled(),
                course.getAthenaConfig() != null && course.getAthenaConfig().isFormativeFeedbackEnabled(), course.getTimeZone(), course.getCourseInformationSharingConfiguration(),
                course.isOnboardingDone(), course.isGradeRelevant(), course.isDataRetentionHold(), course.getAutoOrchestratorEnabled(), course.getDebounceWindowSecondsOverride(),
                course.getMaxDailyOrchestrationOverride());
    }
}
