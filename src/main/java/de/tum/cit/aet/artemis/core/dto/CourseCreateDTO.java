package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseComplaintConfiguration;
import de.tum.cit.aet.artemis.core.domain.CourseEnrollmentConfiguration;
import de.tum.cit.aet.artemis.core.domain.CourseExtendedSettings;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.Language;
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
 * @see de.tum.cit.aet.artemis.core.web.admin.AdminCourseResource#createCourse
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseCreateDTO(
        // Basic info
        @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, String semester,

        // Group names (optional - will use defaults if not set)
        String studentGroupName, String teachingAssistantGroupName, String editorGroupName, String instructorGroupName,

        // Dates
        ZonedDateTime startDate, ZonedDateTime endDate,

        // Configuration flags
        boolean testCourse, Boolean onlineCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage,

        // UI settings
        String color,

        // Course features
        boolean faqEnabled, boolean learningPathsEnabled, boolean studentCourseAnalyticsDashboardEnabled, Integer presentationScore, Integer maxPoints,
        @Min(0) @Max(5) Integer accuracyOfScores, boolean restrictedAthenaModulesAccess, String timeZone,
        CourseInformationSharingConfiguration courseInformationSharingConfiguration,

        // Nested settings
        CourseEnrollmentConfigurationDTO enrollmentConfiguration, CourseComplaintConfigurationDTO complaintConfiguration, CourseExtendedSettingsDTO extendedSettings) {

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
        course.setSemester(semester);

        // Group names
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setEditorGroupName(editorGroupName);
        course.setInstructorGroupName(instructorGroupName);

        // Dates
        course.setStartDate(startDate);
        course.setEndDate(endDate);

        // Configuration flags
        course.setTestCourse(testCourse);
        course.setOnlineCourse(onlineCourse != null && onlineCourse);
        course.setLanguage(language);
        course.setDefaultProgrammingLanguage(defaultProgrammingLanguage);

        // UI settings
        course.setColor(color);

        // Course features
        course.setFaqEnabled(faqEnabled);
        course.setLearningPathsEnabled(learningPathsEnabled);
        course.setStudentCourseAnalyticsDashboardEnabled(studentCourseAnalyticsDashboardEnabled);
        course.setPresentationScore(presentationScore);
        course.setMaxPoints(maxPoints);
        if (accuracyOfScores != null) {
            course.setAccuracyOfScores(accuracyOfScores);
        }
        course.setRestrictedAthenaModulesAccess(restrictedAthenaModulesAccess);
        course.setTimeZone(timeZone);
        if (courseInformationSharingConfiguration != null) {
            course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        }

        // Extended settings
        var extendedSettingsEntity = new CourseExtendedSettings();
        if (extendedSettings != null) {
            extendedSettingsEntity.setDescription(extendedSettings.description());
            extendedSettingsEntity.setMessagingCodeOfConduct(extendedSettings.messagingCodeOfConduct());
            extendedSettingsEntity.setCourseArchivePath(extendedSettings.courseArchivePath());
        }
        course.setExtendedSettings(extendedSettingsEntity);

        // Enrollment configuration
        var enrollmentConfigEntity = new CourseEnrollmentConfiguration();
        if (enrollmentConfiguration != null) {
            if (enrollmentConfiguration.enrollmentEnabled() != null) {
                enrollmentConfigEntity.setEnrollmentEnabled(enrollmentConfiguration.enrollmentEnabled());
            }
            enrollmentConfigEntity.setEnrollmentStartDate(enrollmentConfiguration.enrollmentStartDate());
            enrollmentConfigEntity.setEnrollmentEndDate(enrollmentConfiguration.enrollmentEndDate());
            enrollmentConfigEntity.setUnenrollmentEndDate(enrollmentConfiguration.unenrollmentEndDate());
            enrollmentConfigEntity.setEnrollmentConfirmationMessage(enrollmentConfiguration.enrollmentConfirmationMessage());
            if (enrollmentConfiguration.unenrollmentEnabled() != null) {
                enrollmentConfigEntity.setUnenrollmentEnabled(enrollmentConfiguration.unenrollmentEnabled());
            }
        }
        course.setEnrollmentConfiguration(enrollmentConfigEntity);

        // Complaint configuration
        var complaintConfigEntity = new CourseComplaintConfiguration();
        if (complaintConfiguration != null) {
            if (complaintConfiguration.maxComplaints() != null) {
                complaintConfigEntity.setMaxComplaints(complaintConfiguration.maxComplaints());
            }
            if (complaintConfiguration.maxTeamComplaints() != null) {
                complaintConfigEntity.setMaxTeamComplaints(complaintConfiguration.maxTeamComplaints());
            }
            if (complaintConfiguration.maxComplaintTimeDays() != null) {
                complaintConfigEntity.setMaxComplaintTimeDays(complaintConfiguration.maxComplaintTimeDays());
            }
            if (complaintConfiguration.maxRequestMoreFeedbackTimeDays() != null) {
                complaintConfigEntity.setMaxRequestMoreFeedbackTimeDays(complaintConfiguration.maxRequestMoreFeedbackTimeDays());
            }
            if (complaintConfiguration.maxComplaintTextLimit() != null) {
                complaintConfigEntity.setMaxComplaintTextLimit(complaintConfiguration.maxComplaintTextLimit());
            }
            if (complaintConfiguration.maxComplaintResponseTextLimit() != null) {
                complaintConfigEntity.setMaxComplaintResponseTextLimit(complaintConfiguration.maxComplaintResponseTextLimit());
            }
        }
        course.setComplaintConfiguration(complaintConfigEntity);

        return course;
    }

    public record CourseEnrollmentConfigurationDTO(Boolean enrollmentEnabled, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate,
            @Size(max = 2000) String enrollmentConfirmationMessage, Boolean unenrollmentEnabled, ZonedDateTime unenrollmentEndDate) {
    }

    public record CourseComplaintConfigurationDTO(Integer maxComplaints, Integer maxTeamComplaints, Integer maxComplaintTimeDays, Integer maxRequestMoreFeedbackTimeDays,
            Integer maxComplaintTextLimit, Integer maxComplaintResponseTextLimit) {
    }

    public record CourseExtendedSettingsDTO(@Size(max = 2000) String description, String messagingCodeOfConduct, String courseArchivePath) {
    }
}
