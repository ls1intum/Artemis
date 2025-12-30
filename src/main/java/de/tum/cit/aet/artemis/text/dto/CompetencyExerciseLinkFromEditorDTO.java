package de.tum.cit.aet.artemis.text.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;

/**
 * DTO for competency exercise links in the editor context.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkFromEditorDTO(@NotNull Long competencyId, @NotNull Double weight) {

    /**
     * Creates a CompetencyExerciseLinkFromEditorDTO from the given CompetencyExerciseLink domain object.
     *
     * @param link the competency exercise link to convert
     * @return the corresponding DTO
     */
    public static CompetencyExerciseLinkFromEditorDTO of(CompetencyExerciseLink link) {
        return new CompetencyExerciseLinkFromEditorDTO(link.getCompetency().getId(), link.getWeight());
    }
}
