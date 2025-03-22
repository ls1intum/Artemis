package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.AnswerOptionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceSubmittedAnswerWithSolutionDTO(Set<AnswerOptionWithSolutionDTO> selectedOptions, String type) {

    public static MultipleChoiceSubmittedAnswerWithSolutionDTO of(MultipleChoiceSubmittedAnswer multipleChoiceSubmittedAnswer) {
        return new MultipleChoiceSubmittedAnswerWithSolutionDTO(
                multipleChoiceSubmittedAnswer.getSelectedOptions().stream().map(AnswerOptionWithSolutionDTO::of).collect(Collectors.toSet()), "multiple-choice");
    }

}
