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

    /**
     * Creates the student-facing answer representation used before evaluation details may be published.
     *
     * @param shortAnswerSubmittedAnswer the submitted answer
     * @return the submitted texts without derived correctness values
     */
    public static ShortAnswerSubmittedAnswerDTO beforeEvaluation(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        return new ShortAnswerSubmittedAnswerDTO(
                shortAnswerSubmittedAnswer.getSubmittedTexts().stream().map(ShortAnswerSubmittedTextDTO::beforeEvaluation).collect(Collectors.toSet()), "short-answer");
    }

}
