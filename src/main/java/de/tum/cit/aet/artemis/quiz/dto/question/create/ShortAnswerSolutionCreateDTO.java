package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSolutionCreateDTO(long tempID, @NotEmpty String text) {

    public ShortAnswerSolution toDomainObject() {
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setTempID(tempID);
        solution.setText(text);
        return solution;
    }
}
