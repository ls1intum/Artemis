package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSolutionCreateDTO(long tempID, @NotEmpty String text) {

    /**
     * Converts this DTO to a {@link ShortAnswerSolution} domain object.
     * <p>
     * Maps the DTO properties directly to the corresponding fields in the domain object,
     * including temporary ID and text.
     *
     * @return the {@link ShortAnswerSolution} domain object with properties set from this DTO
     */
    public ShortAnswerSolution toDomainObject() {
        ShortAnswerSolution solution = new ShortAnswerSolution();
        solution.setTempID(tempID);
        solution.setText(text);
        return solution;
    }
}
