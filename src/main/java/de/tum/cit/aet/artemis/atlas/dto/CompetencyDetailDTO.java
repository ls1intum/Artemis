package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * Full competency view for the orchestrator's {@code getCompetencyDetails} tool. Lists all
 * linked exercises and lecture units by id/title/type so the LLM can decide whether to reuse,
 * edit, or complement the competency. Each exercise ref includes the current link weight so
 * the orchestrator can reason honestly about existing evidence strength before reweighting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyDetailDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy, String type, ZonedDateTime softDueDate, Integer masteryThreshold,
        Boolean optional, List<ExerciseRefDTO> exercises, List<LectureUnitRefDTO> lectureUnits) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExerciseRefDTO(Long id, String title, String type, Double weight) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LectureUnitRefDTO(Long id, String name, String type) {
    }
}
