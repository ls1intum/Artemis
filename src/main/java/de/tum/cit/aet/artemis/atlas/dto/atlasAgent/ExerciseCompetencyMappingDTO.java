package de.tum.cit.aet.artemis.atlas.dto.atlasAgent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for previewing exercise-to-competency mappings before saving.
 * Used by the Exercise Mapper Agent to show which competencies will be linked to an exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCompetencyMappingDTO(Long exerciseId, String exerciseTitle, List<CompetencyMappingOptionDTO> competencies, Boolean viewOnly) {

    /**
     * Represents a single competency that can be mapped to an exercise.
     *
     * @param competencyId    The ID of the competency
     * @param competencyTitle The title of the competency
     * @param weight          The mapping weight (0.25=LOW, 0.5=MEDIUM, 1.0=HIGH)
     * @param alreadyMapped   True if this competency is already mapped to the exercise (different styling)
     * @param suggested       True if this competency was suggested by the AI (green checkbox)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyMappingOptionDTO(Long competencyId, String competencyTitle, Double weight, Boolean alreadyMapped, Boolean suggested) {
    }
}
