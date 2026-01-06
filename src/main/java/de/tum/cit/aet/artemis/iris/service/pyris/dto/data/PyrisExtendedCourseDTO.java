package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

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
        List<PyrisExerciseWithStudentSubmissionsDTO> exercises, List<PyrisExamDTO> exams, List<PyrisCompetencyDTO> competencies, boolean studentAnalyticsDashboardEnabled) {

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

        var extendedSettings = course.getExtendedSettings();
        String description = extendedSettings != null ? extendedSettings.getDescription() : null;

        var complaintConfig = course.getComplaintConfiguration();
        int maxComplaints = complaintConfig != null ? complaintConfig.getMaxComplaints() : 3;
        int maxTeamComplaints = complaintConfig != null ? complaintConfig.getMaxTeamComplaints() : 3;
        int maxComplaintTimeDays = complaintConfig != null ? complaintConfig.getMaxComplaintTimeDays() : 7;
        int maxRequestMoreFeedbackTimeDays = complaintConfig != null ? complaintConfig.getMaxRequestMoreFeedbackTimeDays() : 7;

        return new PyrisExtendedCourseDTO(course.getId(), course.getTitle(), description, toInstant(course.getStartDate()), toInstant(course.getEndDate()),
                course.getDefaultProgrammingLanguage(), maxComplaints, maxTeamComplaints, maxComplaintTimeDays, maxRequestMoreFeedbackTimeDays, course.getMaxPoints(),
                course.getPresentationScore(), exercises, exams, competencies, course.getStudentCourseAnalyticsDashboardEnabled());
    }
}
