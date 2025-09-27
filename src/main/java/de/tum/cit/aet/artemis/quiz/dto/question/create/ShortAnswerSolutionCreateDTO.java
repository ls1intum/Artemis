package de.tum.cit.aet.artemis.quiz.dto.question.create;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSolutionCreateDTO(@NotNull Long tempID, @NotEmpty String text) {

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

    /**
     * Creates a {@link ShortAnswerSolutionCreateDTO} from the given {@link ShortAnswerSolution} domain object.
     * <p>
     * Maps the domain object's temporary ID and text to the corresponding DTO fields.
     *
     * @param solution the {@link ShortAnswerSolution} domain object to convert
     * @return the {@link ShortAnswerSolutionCreateDTO} with properties set from the domain object
     */
    public static ShortAnswerSolutionCreateDTO of(ShortAnswerSolution solution) {
        return new ShortAnswerSolutionCreateDTO(solution.getTempID(), solution.getText());
    }
}
