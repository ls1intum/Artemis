package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedAnswerDTO(Set<ShortAnswerSubmittedTextDTO> submittedTexts, String type) {

    public static ShortAnswerSubmittedAnswerDTO of(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        return new ShortAnswerSubmittedAnswerDTO(shortAnswerSubmittedAnswer.getSubmittedTexts().stream().map(ShortAnswerSubmittedTextDTO::of).collect(Collectors.toSet()),
                "short-answer");
    }

}
