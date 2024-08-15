package de.tum.in.www1.artemis.web.rest.dto.competency;

import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, Double value, CompetencyNodeValueType valueType) {

    public enum CompetencyNodeValueType {
        MASTERY_PROGRESS
    }

    public static CompetencyGraphNodeDTO of(@NonNull CourseCompetency competency, Double value, CompetencyNodeValueType valueType) {
        return new CompetencyGraphNodeDTO(competency.getId().toString(), competency.getTitle(), competency.getSoftDueDate(), value, valueType);
    }
}
