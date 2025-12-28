package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
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
        @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Size(max = 2000) String description, String semester,

        // Group names (optional - will use defaults if not set)
        String studentGroupName, String teachingAssistantGroupName, String editorGroupName, String instructorGroupName,

        // Dates
        ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime enrollmentStartDate, ZonedDateTime enrollmentEndDate, ZonedDateTime unenrollmentEndDate,

        // Configuration flags
        boolean testCourse, Boolean onlineCourse, Language language, ProgrammingLanguage defaultProgrammingLanguage,

        // Complaint settings
        Integer maxComplaints, Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit,
        int maxComplaintResponseTextLimit,

        // UI settings
        String color, Boolean enrollmentEnabled, @Size(max = 2000) String enrollmentConfirmationMessage, boolean unenrollmentEnabled,

        // Course features
        boolean faqEnabled, boolean learningPathsEnabled, boolean studentCourseAnalyticsDashboardEnabled, Integer presentationScore, Integer maxPoints,
        @Min(0) @Max(5) Integer accuracyOfScores, boolean restrictedAthenaModulesAccess, String timeZone,
        CourseInformationSharingConfiguration courseInformationSharingConfiguration) {

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

        // Group names
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setEditorGroupName(editorGroupName);
        course.setInstructorGroupName(instructorGroupName);

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
        course.setFaqEnabled(faqEnabled);
        course.setLearningPathsEnabled(learningPathsEnabled);
        course.setStudentCourseAnalyticsDashboardEnabled(studentCourseAnalyticsDashboardEnabled);
        course.setPresentationScore(presentationScore);
        course.setMaxPoints(maxPoints);
        course.setAccuracyOfScores(accuracyOfScores);
        course.setRestrictedAthenaModulesAccess(restrictedAthenaModulesAccess);
        course.setTimeZone(timeZone);
        course.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        return course;
    }
}
