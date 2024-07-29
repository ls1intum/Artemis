package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, Double value,
                                     CompetencyNodeValueType valueType) {

    public enum CompetencyNodeValueType {
        MASTERY_PROGRESS
    }

    public static CompetencyGraphNodeDTO of(@NotNull CourseCompetency competency, Double value, CompetencyNodeValueType valueType) {
        return new CompetencyGraphNodeDTO(competency.getId().toString(), competency.getTitle(), competency.getSoftDueDate(),
            value, valueType);
    }
}
