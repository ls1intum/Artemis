package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyGraphNodeDTO(String id, String label, ZonedDateTime softDueDate, Double value, CompetencyNodeValueType valueType) {

    public enum CompetencyNodeValueType {
        MASTERY_PROGRESS
    }

    public static CompetencyGraphNodeDTO of(@NotNull CourseCompetency competency, Double value, CompetencyNodeValueType valueType) {
        return new CompetencyGraphNodeDTO(competency.getId().toString(), competency.getTitle(), competency.getSoftDueDate(), value, valueType);
    }
}
