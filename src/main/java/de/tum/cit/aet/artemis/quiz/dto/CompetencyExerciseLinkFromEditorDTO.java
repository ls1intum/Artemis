package de.tum.cit.aet.artemis.quiz.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkFromEditorDTO(@NotNull Long competencyId, @NotNull Double weight) {

    public static CompetencyExerciseLinkFromEditorDTO of(CompetencyExerciseLink link) {
        return new CompetencyExerciseLinkFromEditorDTO(link.getCompetency().getId(), link.getWeight());
    }
}
