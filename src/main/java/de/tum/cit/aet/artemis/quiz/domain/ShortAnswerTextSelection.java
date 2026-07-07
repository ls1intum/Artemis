package de.tum.cit.aet.artemis.quiz.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single short-answer text a student submitted for one spot: the {@link #text} entered for the spot with id {@link #spotId}, plus the {@link #isCorrect} flag computed by the
 * scoring pass. Stored inside {@link ShortAnswerSubmittedAnswerSelection} in the {@code submitted_answer.selection} JSON column.
 * <p>
 * This is the submission-side counterpart to the question-owned {@link ShortAnswerCorrectMapping} storage entries. It is the persistent source of truth for a submitted text; the
 * object-based {@link ShortAnswerSubmittedText} is the wire/in-memory representation built on demand around such an entry (its {@code text}/{@code isCorrect} accessors delegate
 * here, so the scoring pass writing {@code isCorrect} on a submitted text writes through to the stored selection). Unlike the drag-and-drop mapping selection it is a mutable POJO
 * (not a record) precisely because {@code isCorrect} is mutated in place during scoring.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerTextSelection {

    @JsonProperty("spotId")
    private Long spotId;

    @JsonProperty("text")
    private String text;

    @JsonProperty("isCorrect")
    private Boolean isCorrect;

    public ShortAnswerTextSelection() {
    }

    public ShortAnswerTextSelection(Long spotId, String text, Boolean isCorrect) {
        this.spotId = spotId;
        this.text = text;
        this.isCorrect = isCorrect;
    }

    public Long getSpotId() {
        return spotId;
    }

    public void setSpotId(Long spotId) {
        this.spotId = spotId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }
}
