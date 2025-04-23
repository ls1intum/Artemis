package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.AnswerOptionWithoutSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceSubmittedAnswerWithoutSolutionDTO(Set<AnswerOptionWithoutSolutionDTO> selectedOptions, String type) {

    public static MultipleChoiceSubmittedAnswerWithoutSolutionDTO of(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
        return new MultipleChoiceSubmittedAnswerWithoutSolutionDTO(
                multipleChoiceSubmittedAnswer.getSelectedOptions().stream().map(AnswerOptionWithoutSolutionDTO::of).collect(Collectors.toSet()), "multiple-choice");
    }

}
