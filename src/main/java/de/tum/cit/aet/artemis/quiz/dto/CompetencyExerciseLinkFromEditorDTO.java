package de.tum.cit.aet.artemis.quiz.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkFromEditorDTO(@NotNull Long competencyId, @NotNull Double weight) {

}
