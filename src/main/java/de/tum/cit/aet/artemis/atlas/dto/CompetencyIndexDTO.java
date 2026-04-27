package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;

/**
 * Compact competency entry for the orchestrator's {@code listCompetencyIndex} tool.
 * Exposes identifiers plus linked exercises (with type and current link weight) and lecture
 * units (with type) so the LLM can reason about coverage and evidence strength without issuing
 * additional tool calls for every competency.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyIndexDTO(Long id, String title, CompetencyTaxonomy taxonomy, String type, List<ExerciseLinkRef> exercises, List<LectureUnitRef> lectureUnits) {

    public record ExerciseLinkRef(String title, String type, Double weight) {
    }

    public record LectureUnitRef(String name, String type) {
    }
}
