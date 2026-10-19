package de.tum.cit.aet.artemis.quiz.domain;

import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import me.xdrop.fuzzywuzzy.FuzzySearch;

/**
 * A ShortAnswerSubmittedText.
 * <p>
 * This is the in-memory / wire representation of a single submitted text; it is built on demand by {@link ShortAnswerSubmittedAnswer#getSubmittedTexts()} and serialized to the
 * client (its {@code spot} object, {@code text} and {@code isCorrect}). It is <b>not</b> persisted directly: a submission's texts are stored inside
 * {@link ShortAnswerSubmittedAnswerSelection} as normalized, id-based {@link ShortAnswerTextSelection} entries in the {@code submitted_answer.selection} JSON column.
 * <p>
 * Every instance is backed by a mutable {@link ShortAnswerTextSelection} entry: {@link #getText()}/{@link #setText} and {@link #isIsCorrect()}/{@link #setIsCorrect} delegate to
 * it. This preserves the pre-JSON behavior that the scoring pass ({@code ScoringStrategyShortAnswerUtil}) mutates {@code isCorrect} on a submitted text and the change persists —
 * because the wire object writes through to the stored selection entry. The {@code spot} (resolved object) and {@code submittedAnswer} back-reference are transient and never
 * stored in the entry; {@code submittedAnswer} lets {@link #isSubmittedTextCorrect} reach the owning question for its similarity settings.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedText extends DomainObject {

    @JsonIgnore
    private final transient ShortAnswerTextSelection entry;

    private transient ShortAnswerSpot spot;

    @JsonIgnore
    private transient ShortAnswerSubmittedAnswer submittedAnswer;

    public ShortAnswerSubmittedText() {
        this.entry = new ShortAnswerTextSelection();
    }

    /**
     * Wraps an existing stored selection entry so that mutations (e.g. {@code setIsCorrect} during scoring) write through to it.
     *
     * @param entry the backing selection entry
     */
    public ShortAnswerSubmittedText(ShortAnswerTextSelection entry) {
        this.entry = entry != null ? entry : new ShortAnswerTextSelection();
    }

    /**
     * @return the backing selection entry (used by {@link ShortAnswerSubmittedAnswer} to store this submitted text)
     */
    @JsonIgnore
    ShortAnswerTextSelection getEntry() {
        return entry;
    }

    public String getText() {
        return entry.getText();
    }

    public void setText(String text) {
        entry.setText(text);
    }

    public Boolean isIsCorrect() {
        return entry.getIsCorrect();
    }

    public void setIsCorrect(Boolean isCorrect) {
        entry.setIsCorrect(isCorrect);
    }

    public ShortAnswerSpot getSpot() {
        return spot;
    }

    public void setSpot(ShortAnswerSpot shortAnswerSpot) {
        this.spot = shortAnswerSpot;
        entry.setSpotId(shortAnswerSpot != null ? shortAnswerSpot.getId() : null);
    }

    @JsonIgnore
    public ShortAnswerSubmittedAnswer getSubmittedAnswer() {
        return submittedAnswer;
    }

    public void setSubmittedAnswer(ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer) {
        this.submittedAnswer = shortAnswerSubmittedAnswer;
    }

    /**
     * This function checks if the submittedText (typos included) matches the solution. <a href="https://github.com/xdrop/fuzzywuzzy">...</a>
     *
     * @param submittedText for a short answer question
     * @param solution      of the short answer question
     * @return boolean true if submittedText fits the restrictions above, false when not
     */
    public boolean isSubmittedTextCorrect(String submittedText, String solution) {
        if (Objects.equals(submittedText, solution)) {
            // when both values are identical, we can return early
            return true;
        }
        if (submittedText == null) {
            // prevent null pointer exceptions
            return false;
        }
        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) submittedAnswer.getQuizQuestion();
        int similarityValue = Objects.requireNonNullElse(saQuestion.getSimilarityValue(), 85); // default value
        if (Boolean.TRUE.equals(saQuestion.getMatchLetterCase())) {
            // only trim whitespace left and right
            return FuzzySearch.ratio(submittedText.trim(), solution.trim()) >= similarityValue;
        }
        // also use lowercase to allow different cases in the submitted text
        return FuzzySearch.ratio(submittedText.toLowerCase(Locale.ROOT).trim(), solution.toLowerCase(Locale.ROOT).trim()) >= similarityValue;
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedText{" + "id=" + getId() + ", text='" + getText() + "'" + ", isCorrect='" + isIsCorrect() + "'" + "}";
    }

}
