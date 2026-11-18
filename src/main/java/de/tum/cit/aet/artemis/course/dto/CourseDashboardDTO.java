package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyResponseDTO;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exam.dto.ExamDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/** DTO-safe course graph returned by the single- and multi-course dashboard endpoints. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseDashboardDTO(long id, String title, String shortName, @Nullable String description, @Nullable String semester, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, boolean testCourse, @Nullable Language language, @Nullable ProgrammingLanguage defaultProgrammingLanguage, @Nullable String timeZone,
        @Nullable String color, @Nullable String courseIcon, @Nullable Boolean enrollmentEnabled, boolean unenrollmentEnabled, @Nullable ZonedDateTime enrollmentStartDate,
        @Nullable ZonedDateTime enrollmentEndDate, @Nullable ZonedDateTime unenrollmentEndDate, @Nullable String enrollmentConfirmationMessage, boolean onlineCourse,
        @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration, @Nullable String courseInformationSharingMessagingCodeOfConduct,
        @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, int maxComplaintTextLimit,
        int maxComplaintResponseTextLimit, @Nullable Integer presentationScore, @Nullable Integer maxPoints, @Nullable Integer accuracyOfScores, boolean complaintsEnabled,
        boolean requestMoreFeedbackEnabled, boolean athenaGradingFeedbackEnabled, boolean athenaFormativeFeedbackEnabled, boolean learningPathsEnabled, boolean trainingEnabled,
        Set<CourseDashboardExerciseDTO> exercises, Set<LectureForCourseManagementDTO> lectures, Set<ExamDTO> exams, Set<CourseCompetencyResponseDTO> competencies,
        Set<CoursePrerequisiteDTO> prerequisites) {

    /** Maps the course only after the resource has applied all student visibility filters. */
    public static CourseDashboardDTO of(Course course) {
        Set<CourseDashboardExerciseDTO> exercises = initialized(course.getExercises())
                ? course.getExercises().stream().map(CourseDashboardExerciseDTO::of).collect(Collectors.toSet())
                : Set.of();
        Set<LectureForCourseManagementDTO> lectures = initialized(course.getLectures())
                ? course.getLectures().stream().map(LectureForCourseManagementDTO::of).collect(Collectors.toSet())
                : Set.of();
        Set<ExamDTO> exams = initialized(course.getExams()) ? course.getExams().stream().map(ExamDTO::of).collect(Collectors.toSet()) : Set.of();
        Set<CourseCompetencyResponseDTO> competencies = initialized(course.getCompetencies())
                ? course.getCompetencies().stream().map(CourseCompetencyResponseDTO::of).collect(Collectors.toSet())
                : Set.of();
        Set<CoursePrerequisiteDTO> prerequisites = initialized(course.getPrerequisites())
                ? course.getPrerequisites().stream().map(CoursePrerequisiteDTO::of).collect(Collectors.toSet())
                : Set.of();

        return new CourseDashboardDTO(course.getId(), course.getTitle(), course.getShortName(), course.getDescription(), course.getSemester(), course.getStartDate(),
                course.getEndDate(), course.isTestCourse(), course.getLanguage(), course.getDefaultProgrammingLanguage(), course.getTimeZone(), course.getColor(),
                course.getCourseIcon(), course.isEnrollmentEnabled(), course.isUnenrollmentEnabled(), course.getEnrollmentStartDate(), course.getEnrollmentEndDate(),
                course.getUnenrollmentEndDate(), course.getEnrollmentConfirmationMessage(), course.isOnlineCourse(), course.getCourseInformationSharingConfiguration(),
                course.getCourseInformationSharingMessagingCodeOfConduct(), course.getMaxComplaints(), course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(),
                course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxComplaintTextLimit(), course.getMaxComplaintResponseTextLimit(), course.getPresentationScore(),
                course.getMaxPoints(), course.getAccuracyOfScores(), course.getComplaintsEnabled(), course.getRequestMoreFeedbackEnabled(), course.isAthenaGradingFeedbackEnabled(),
                course.isAthenaFormativeFeedbackEnabled(), course.getLearningPathsEnabled(), course.isTrainingEnabled(), exercises, lectures, exams, competencies, prerequisites);
    }

    private static boolean initialized(@Nullable Object association) {
        return association != null && Hibernate.isInitialized(association);
    }
}
