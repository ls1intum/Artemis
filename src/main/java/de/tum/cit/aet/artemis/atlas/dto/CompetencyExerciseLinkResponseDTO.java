package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompetencyExerciseLinkResponseDTO(double weight, ExerciseForCompetencyDTO exercise) {

    @Nullable
    public static CompetencyExerciseLinkResponseDTO of(@Nullable CompetencyExerciseLink link) {
        if (link == null) {
            return null;
        }
        return new CompetencyExerciseLinkResponseDTO(link.getWeight(), ExerciseForCompetencyDTO.of(link.getExercise()));
    }
}
