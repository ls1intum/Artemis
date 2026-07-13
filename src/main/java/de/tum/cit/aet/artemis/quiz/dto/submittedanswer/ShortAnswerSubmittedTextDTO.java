package de.tum.cit.aet.artemis.quiz.dto.submittedanswer;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.dto.ShortAnswerSpotDTO;

/**
 * Submitted text of a short-answer question.
 * <p>
 * NOTE: this no longer carries an {@code id}. Submitted texts moved into the submitted answer's JSON {@code selection} column, where they are plain POJOs without a database
 * row, so there is no stable identifier left to expose. Clients must address a submitted text through its {@link ShortAnswerSpotDTO#id() spot id} instead.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShortAnswerSubmittedTextDTO(String text, Boolean isCorrect, ShortAnswerSpotDTO spot) {

    public static ShortAnswerSubmittedTextDTO of(final ShortAnswerSubmittedText submittedText) {
        return new ShortAnswerSubmittedTextDTO(submittedText.getText(), submittedText.isIsCorrect(), ShortAnswerSpotDTO.of(submittedText.getSpot()));
    }

    /**
     * Creates a representation for responses sent before evaluation details may be published.
     *
     * @param submittedText the submitted short answer text
     * @return the raw submitted text and spot without the derived correctness value
     */
    public static ShortAnswerSubmittedTextDTO beforeEvaluation(final ShortAnswerSubmittedText submittedText) {
        return new ShortAnswerSubmittedTextDTO(submittedText.getText(), null, ShortAnswerSpotDTO.of(submittedText.getSpot()));
    }

}
