package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Full competency index payload exposed to the Atlas orchestrator. Wraps the per-competency entries
 * with the list of course exercises that are currently not linked to any competency, so the LLM
 * can reason about global coverage gaps without issuing extra tool calls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompetencyIndexResponseDTO(List<CompetencyIndexDTO> competencies, List<UnassignedExerciseRef> unassignedExercises) {

    public record UnassignedExerciseRef(Long id, String title, String type) {
    }
}
