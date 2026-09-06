package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.tum.cit.aet.artemis.core.config.StrictIntegerDeserializer;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Data Transfer Object for creating a new course via the admin API.
 * <p>
 * This DTO is used instead of directly deserializing to a Course entity to prevent
 * potential security issues from client-controlled entity state. By using a DTO:
 * <ul>
 * <li>The server controls which fields can be set during creation</li>
 * <li>Entity IDs cannot be spoofed by the client</li>
 * <li>Internal entity state (e.g., relationships, computed fields) is not exposed</li>
 * <li>Validation annotations are clearly defined for the API contract</li>
 * </ul>
 * <p>
 * The {@link #toCourse()} method creates a clean, server-controlled entity instance
 * with all fields properly initialized from the DTO values.
 *
 * @see de.tum.cit.aet.artemis.admin.web.AdminCourseResource#createCourse
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCreateDTO(
        // Basic info
        @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Size(max = 2000) String description, @NotBlank @Size(max = 25) String semester,

        // Dates
        @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate, ZonedDateTime unenrollmentEndDate,

        // Configuration flags
        boolean testCourse, Boolean onlineCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage,

        // Complaint settings
        Integer maxComplaints, Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit,
        int maxComplaintResponseTextLimit,

        // UI settings
        String color, Boolean enrollmentEnabled, @Size(max = 2000) String enrollmentConfirmationMessage, boolean unenrollmentEnabled,

        // Course features
        boolean learningPathsEnabled, @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer presentationScore,
        @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxPoints, @Min(0) @Max(5) Integer accuracyOfScores, boolean athenaGradingFeedbackEnabled,
        boolean athenaFormativeFeedbackEnabled, String timeZone, CourseInformationSharingConfiguration courseInformationSharingConfiguration,

        // Data-privacy / retention: whether the course is grade-relevant (drives how long student data is retained).
        // Boxed so an omitted value fails safe to grade-relevant (the longer retention), not to earlier deletion.
        Boolean gradeRelevant,

        // Atlas auto-orchestration configuration (per-course): kill switch plus nullable overrides. Creating a course is
        // admin-only, so the same admin-gated settings the update form exposes are accepted here; without them, enabling
        // the pipeline on the create form would be silently dropped and only take effect after a second (edit) save.
        // The strict deserializer matches CourseUpdateDTO: @Min(1) alone would not reject a fractional value, because the
        // default Integer deserializer truncates it (10.5 -> 10) before bean validation runs.
        boolean autoOrchestratorEnabled, @Min(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer debounceWindowSecondsOverride,
        @Min(1) @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer maxDailyOrchestrationOverride) {

    /**
     * Creates a new Course entity from this DTO.
     * <p>
     * The entity is created server-side to ensure a clean, controlled state.
     * No entity ID is set, allowing the persistence layer to generate it.
     * All fields from the DTO are mapped to the corresponding entity fields.
     * <p>
     * Note: Some fields like {@code onlineCourse} have special handling for null values
     * to ensure proper defaults are applied.
     *
     * @return a new Course entity with all fields set from this DTO, ready for persistence
     */
    public Course toCourse() {
        Course course = new Course();

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
        course.setEnrollmentEnabled(enrollmentEnabled);
        course.setEnrollmentConfirmationMessage(enrollmentConfirmationMessage);
        course.setUnenrollmentEnabled(unenrollmentEnabled);

        // Course features
        course.setLearningPathsEnabled(learningPathsEnabled);
        course.setPresentationScore(presentationScore);
        course.setMaxPoints(maxPoints);
        course.setAccuracyOfScores(accuracyOfScores);
        var athenaConfig = new CourseAthenaConfig();
        athenaConfig.setGradingFeedbackEnabled(athenaGradingFeedbackEnabled);
        athenaConfig.setFormativeFeedbackEnabled(athenaFormativeFeedbackEnabled);
        course.setAthenaConfig(athenaConfig);
        course.setTimeZone(timeZone);
        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        // Attach the course configuration holding the grade-relevance flag (drives the student-data retention period)
        // and the Atlas auto-orchestration settings.
        // Fail safe to grade-relevant (longer retention) when the client omits the flag.
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setGradeRelevant(gradeRelevant == null || gradeRelevant);
        configuration.setAutoOrchestratorEnabled(autoOrchestratorEnabled);
        configuration.setDebounceWindowSecondsOverride(debounceWindowSecondsOverride);
        configuration.setMaxDailyOrchestrationOverride(maxDailyOrchestrationOverride);
        configuration.setCourse(course);
        course.setCourseConfiguration(configuration);

        return course;
    }
}
