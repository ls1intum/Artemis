package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyInstructorGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, double percentOfMasteredStudents, double averageMasteryProgress,
        double meanMasteryProgress) {

    public static CompetencyInstructorGraphNodeDTO of(@NotNull CourseCompetency competency, double percentOfMasteredStudents, double averageMasteryProgress,
            double meanMasteryProgress) {
        return new CompetencyInstructorGraphNodeDTO(competency.getId().toString(), competency.getTitle(), competency.getSoftDueDate(), percentOfMasteredStudents,
                averageMasteryProgress, meanMasteryProgress);
    }
}
