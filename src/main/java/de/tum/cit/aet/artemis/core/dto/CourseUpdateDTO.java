package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.Language;
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
 * @see de.tum.cit.aet.artemis.core.web.course.CourseUpdateResource#updateCourse
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseUpdateDTO(
        // ID is required for update
        @NotNull Long id,

        // Basic info
        @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Size(max = 2000) String description, String semester,

        // Group names
        String studentGroupName, String teachingAssistantGroupName, String editorGroupName, String instructorGroupName,

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
        boolean faqEnabled, boolean learningPathsEnabled, boolean studentCourseAnalyticsDashboardEnabled, Integer presentationScore, Integer maxPoints,
        @Min(0) @Max(5) Integer accuracyOfScores, boolean restrictedAthenaModulesAccess, String timeZone,
        CourseInformationSharingConfiguration courseInformationSharingConfiguration) {

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
        course.setCourseIcon(courseIcon);
        course.setEnrollmentEnabled(enrollmentEnabled);
        course.setEnrollmentConfirmationMessage(enrollmentConfirmationMessage);
        course.setUnenrollmentEnabled(unenrollmentEnabled);
        course.setCourseInformationSharingMessagingCodeOfConduct(courseInformationSharingMessagingCodeOfConduct);

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

    /**
     * Creates a CourseUpdateDTO from an existing Course entity.
     *
     * @param course the course entity to convert
     * @return a new CourseUpdateDTO with values from the course
     */
    public static CourseUpdateDTO of(Course course) {
        return new CourseUpdateDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getSemester(), course.getStudentGroupName(),
                course.getTeachingAssistantGroupName(), course.getEditorGroupName(), course.getInstructorGroupName(), course.getStartDate(), course.getEndDate(),
                course.getEnrollmentStartDate(), course.getEnrollmentEndDate(), course.getUnenrollmentEndDate(), course.isTestCourse(), course.isOnlineCourse(),
                course.getLanguage(), course.getDefaultProgrammingLanguage(), course.getMaxComplaints(), course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(),
                course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(), course.getColor(), course.getCourseIcon(),
                course.isEnrollmentEnabled(), course.getEnrollmentConfirmationMessage(), course.isUnenrollmentEnabled(), course.getCourseInformationSharingMessagingCodeOfConduct(),
                course.isFaqEnabled(), course.getLearningPathsEnabled(), course.getStudentCourseAnalyticsDashboardEnabled(), course.getPresentationScore(), course.getMaxPoints(),
                course.getAccuracyOfScores(), course.getRestrictedAthenaModulesAccess(), course.getTimeZone(), course.getCourseInformationSharingConfiguration());
    }
}
