package de.tum.cit.aet.artemis.core.service.connectors.pyris.dto.data;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * An extended course DTO for Pyris so it can better answer
 * questions regarding the course organization and content.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExtendedCourseDTO(long id, String name, String description, Instant startTime, Instant endTime, ProgrammingLanguage defaultProgrammingLanguage,
        int maxComplaints, int maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, Integer maxPoints, Integer presentationScore,
        List<PyrisExerciseWithStudentSubmissionsDTO> exercises, List<PyrisExamDTO> exams, List<PyrisCompetencyDTO> competencies) {

    /**
     * Convert a course to a PyrisExtendedCourseDTO.
     *
     * @param course The course to convert.
     * @return The converted course.
     */
    public static PyrisExtendedCourseDTO of(Course course) {
        List<PyrisExerciseWithStudentSubmissionsDTO> exercises = course.getExercises().stream().map(PyrisExerciseWithStudentSubmissionsDTO::of).toList();

        List<PyrisExamDTO> exams = course.getExams().stream().map(PyrisExamDTO::of).toList();
        List<PyrisCompetencyDTO> competencies = course.getCompetencies().stream().map(PyrisCompetencyDTO::of).toList();

        return new PyrisExtendedCourseDTO(course.getId(), course.getTitle(), course.getDescription(), toInstant(course.getStartDate()), toInstant(course.getEndDate()),
                course.getDefaultProgrammingLanguage(), course.getMaxComplaints(), course.getMaxTeamComplaints(), course.getMaxComplaintTimeDays(),
                course.getMaxRequestMoreFeedbackTimeDays(), course.getMaxPoints(), course.getPresentationScore(), exercises, exams, competencies);
    }
}
