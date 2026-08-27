package de.tum.cit.aet.artemis.quiz.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkFromEditorDTO(@NotNull Long competencyId, @NotNull Double weight, @JsonProperty(access = JsonProperty.Access.READ_ONLY) boolean generatedByAi) {

    public static CompetencyExerciseLinkFromEditorDTO of(CompetencyExerciseLink link) {
        return new CompetencyExerciseLinkFromEditorDTO(link.getCompetency().getId(), link.getWeight(), link.isGeneratedByAi());
    }
}
