package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DifficultyAssessmentDTO(String suggested, // EASY, MEDIUM, HARD
        String reasoning, boolean matchesDeclared) {
}
