package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCourseDTO(long id, String name, String description, @Nullable Instant startTime, @Nullable Instant endTime,
        @Nullable ProgrammingLanguage defaultProgrammingLanguage, @Nullable Integer maxComplaints, @Nullable Integer maxTeamComplaints, @Nullable Integer maxComplaintTimeDays,
        @Nullable Integer maxRequestMoreFeedbackTimeDays, @Nullable Integer maxPoints, @Nullable Integer presentationScore,
        @Nullable List<PyrisExerciseWithStudentSubmissionsDTO> exercises, @Nullable List<PyrisExamDTO> exams, @Nullable List<PyrisCompetencyDTO> competencies,
        @Nullable Boolean studentAnalyticsDashboardEnabled) {

    /**
     * Create a basic PyrisCourseDTO with only id, name, and description.
     *
     * @param course The course
     */
    // TODO: REFACTORING ASLAN: SOLLEN TUTOR SUGGESTION & AUTONOMOUS TUTOR NUR BASIS VAIRANTE ODER EXTENDED VARIANTE BEKOMMEN?
    public PyrisCourseDTO(Course course) {
        this(course.getId(), course.getTitle(), course.getDescription(), null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Create a fully populated PyrisCourseDTO with all extended fields.
     * Requires the course to have exercises, exams, and competencies loaded.
     *
     * @param course The course with all associations loaded
     * @return The fully populated DTO
     */
    public static PyrisCourseDTO of(Course course) {
        List<PyrisExerciseWithStudentSubmissionsDTO> exercises = course.getExercises().stream().map(PyrisExerciseWithStudentSubmissionsDTO::of).toList();
        List<PyrisExamDTO> exams = course.getExams().stream().map(PyrisExamDTO::of).toList();
        List<PyrisCompetencyDTO> competencies = course.getCompetencies().stream().map(PyrisCompetencyDTO::of).toList();

        return new PyrisCourseDTO(course.getId(), course.getTitle(), course.getDescription(), toInstant(course.getStartDate()), toInstant(course.getEndDate()),
                course.getDefaultProgrammingLanguage(), course.getMaxComplaints(), course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(),
                course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxPoints(), course.getPresentationScore(), exercises, exams, competencies,
                course.getStudentCourseAnalyticsDashboardEnabled());
    }
}
