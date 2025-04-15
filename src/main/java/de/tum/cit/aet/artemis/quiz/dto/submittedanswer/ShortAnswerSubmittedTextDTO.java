package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedTextDTO(Long id, String text, Boolean isCorrect, ShortAnswerSpotDTO spot) {

    public static ShortAnswerSubmittedTextDTO of(final ShortAnswerSubmittedText submittedText) {
        return new ShortAnswerSubmittedTextDTO(submittedText.getId(), submittedText.getText(), submittedText.isIsCorrect(), ShortAnswerSpotDTO.of(submittedText.getSpot()));
    }

}
