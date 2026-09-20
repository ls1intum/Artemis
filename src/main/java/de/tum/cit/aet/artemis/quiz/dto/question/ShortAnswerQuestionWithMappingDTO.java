package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerQuestionWithMappingDTO(@JsonUnwrapped ShortAnswerQuestionWithoutMappingDTO shortAnswerQuestionWithoutMappingDTO,
        List<ShortAnswerMappingDTO> correctMappings) {

    public static ShortAnswerQuestionWithMappingDTO of(ShortAnswerQuestion question) {
        // correctMappings is null on a question that has been masked for students (solutions/mappings stripped before
        // results are published); treat that as no mappings instead of dereferencing null.
        List<ShortAnswerMappingDTO> correctMappings = question.getCorrectMappings() == null ? null : question.getCorrectMappings().stream().map(ShortAnswerMappingDTO::of).toList();
        return new ShortAnswerQuestionWithMappingDTO(ShortAnswerQuestionWithoutMappingDTO.of(question), correctMappings);
    }

}
