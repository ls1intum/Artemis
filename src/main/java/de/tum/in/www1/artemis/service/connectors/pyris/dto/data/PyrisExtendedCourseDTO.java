package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.util.List;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * An extended course DTO for Pyris so it can better answer
 * questions regarding the course organization and content.
 */
public record PyrisExtendedCourseDTO(long id, String name, String description, Instant startTime, Instant endTime, ProgrammingLanguage defaultProgrammingLanguage,
        int maxComplaints, int maxTeamComplaints, int maxComplaintTimeDays, int maxRequestMoreFeedbackTimeDays, Integer maxPoints, Integer presentationScore,

        List<PyrisExerciseWithStudentSubmissionsDTO> exercises, List<PyrisExamDTO> exams, List<PyrisCompetencyDTO> competencies) {
}
