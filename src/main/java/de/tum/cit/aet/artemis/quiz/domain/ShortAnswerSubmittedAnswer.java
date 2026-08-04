package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.compare.SAMapping;

/**
 * A ShortAnswerSubmittedAnswer.
 * <p>
 * The submitted texts are stored inside the {@code submitted_answer.selection} JSON column (as {@link ShortAnswerTextSelection} entries in a
 * {@link ShortAnswerSubmittedAnswerSelection}) instead of the former {@code @OneToMany} {@code short_answer_submitted_text} rows. The public {@code getSubmittedTexts()} accessor
 * keeps its original signature/shape: it resolves each entry's spot id against the owning question and wraps the (mutable) entry in a {@link ShortAnswerSubmittedText} whose
 * {@code text}/{@code isCorrect} write through to the stored entry, so the REST/websocket wire format is preserved and the scoring pass's {@code isCorrect} mutation still
 * persists.
 * Mirrors {@link DragAndDropSubmittedAnswer}.
 */
@Entity
@DiscriminatorValue(value = "SA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ShortAnswerSubmittedAnswer extends SubmittedAnswer {

    private ShortAnswerSubmittedAnswerSelection saSelection() {
        if (getSelection() instanceof ShortAnswerSubmittedAnswerSelection shortAnswerSelection) {
            return shortAnswerSelection;
        }
        ShortAnswerSubmittedAnswerSelection created = new ShortAnswerSubmittedAnswerSelection();
        setSelection(created);
        return created;
    }

    private ShortAnswerQuestion shortAnswerQuestion() {
        return getQuizQuestion() instanceof ShortAnswerQuestion question ? question : null;
    }

    /**
     * Wraps a stored selection entry in a {@link ShortAnswerSubmittedText}, resolving its spot against the owning question and setting the back-reference. The wrapper's
     * {@code text}/{@code isCorrect} delegate to the entry, so mutations write through to the stored selection.
     *
     * @param entry    the backing selection entry
     * @param question the owning question (may be null)
     * @return the wire-shaped submitted text
     */
    private ShortAnswerSubmittedText wrap(ShortAnswerTextSelection entry, ShortAnswerQuestion question) {
        ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText(entry);
        submittedText.setSubmittedAnswer(this);
        ShortAnswerSpot spot = question != null ? question.findSpotById(entry.getSpotId()) : null;
        if (spot != null) {
            // resolves the spot object without clobbering the stored spot id (findSpotById matched it)
            submittedText.setSpot(spot);
        }
        return submittedText;
    }

    /**
     * The submitted texts resolved into object-based {@link ShortAnswerSubmittedText}s (with their spot object) against the owning question. Built on demand from the stored
     * selection; each wrapper shares its backing entry, so {@code setIsCorrect} on a returned submitted text writes through to the stored selection.
     *
     * @return the resolved submitted texts
     */
    public Set<ShortAnswerSubmittedText> getSubmittedTexts() {
        Set<ShortAnswerSubmittedText> result = new HashSet<>();
        if (!(getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
            return result;
        }
        ShortAnswerQuestion question = shortAnswerQuestion();
        if (question == null) {
            return result;
        }
        for (ShortAnswerTextSelection entry : selection.getSubmittedTexts()) {
            // Skip texts whose spot no longer exists on the question (deleted during re-evaluation / orphaned entry): the wire, DTO and data-export consumers dereference the spot,
            // so a null spot would NPE. Mirrors the DnD getMappings() and MC getSelectedOptions() null-skip.
            if (question.findSpotById(entry.getSpotId()) == null) {
                continue;
            }
            result.add(wrap(entry, question));
        }
        return result;
    }

    /**
     * Replaces the submitted texts, storing them as normalized entries in the JSON selection.
     *
     * @param shortAnswerSubmittedTexts the submitted texts whose backing entries are stored
     */
    public void setSubmittedTexts(Set<ShortAnswerSubmittedText> shortAnswerSubmittedTexts) {
        List<ShortAnswerTextSelection> entries = new ArrayList<>();
        if (shortAnswerSubmittedTexts != null) {
            for (ShortAnswerSubmittedText submittedText : shortAnswerSubmittedTexts) {
                // skip null elements reaching this public API so a malformed submission cannot NPE while normalizing into the JSON selection
                if (submittedText != null) {
                    entries.add(submittedText.getEntry());
                }
            }
        }
        saSelection().setSubmittedTexts(entries);
    }

    /**
     * Adds a submitted text, storing its backing entry in the JSON selection and wiring the back-reference. A {@code null} argument is ignored.
     *
     * @param shortAnswerSubmittedText the submitted text to add
     * @return this submitted answer for fluent chaining
     */
    public ShortAnswerSubmittedAnswer addSubmittedTexts(ShortAnswerSubmittedText shortAnswerSubmittedText) {
        if (shortAnswerSubmittedText == null) {
            return this;
        }
        saSelection().getSubmittedTexts().add(shortAnswerSubmittedText.getEntry());
        shortAnswerSubmittedText.setSubmittedAnswer(this);
        return this;
    }

    /**
     * Removes the submitted text for the same spot from the JSON selection and clears its back-reference. A {@code null} argument is ignored.
     *
     * @param shortAnswerSubmittedText the submitted text to remove
     * @return this submitted answer for fluent chaining
     */
    public ShortAnswerSubmittedAnswer removeSubmittedTexts(ShortAnswerSubmittedText shortAnswerSubmittedText) {
        if (shortAnswerSubmittedText == null) {
            return this;
        }
        ShortAnswerTextSelection entry = shortAnswerSubmittedText.getEntry();
        saSelection().getSubmittedTexts().removeIf(existing -> existing == entry || Objects.equals(existing.getSpotId(), entry.getSpotId()));
        shortAnswerSubmittedText.setSubmittedAnswer(null);
        return this;
    }

    /**
     * Delete all references to question, solutions and spots if the question was changed
     *
     * @param quizExercise the changed quizExercise-object
     */
    @Override
    public void checkAndDeleteReferences(QuizExercise quizExercise) {
        // Delete all references if the question was deleted
        if (getQuizQuestion() == null || !quizExercise.getQuizQuestions().contains(getQuizQuestion())) {
            setQuizQuestion(null);
            setSelection(null);
            return;
        }
        // Check if a spot was deleted and remove the affected submitted texts
        if (quizExercise.findQuestionById(getQuizQuestion().getId()) instanceof ShortAnswerQuestion question
                && getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection) {
            Set<Long> spotIds = question.getSpots().stream().map(ShortAnswerSpot::getId).collect(Collectors.toSet());
            selection.getSubmittedTexts().removeIf(entry -> !spotIds.contains(entry.getSpotId()));
        }
    }

    /**
     * Gets a ShortAnswerSubmittedText, that corresponds to a given spot
     *
     * @param spot the ShortAnswerSpot for which the ShortAnswerSubmittedText should be determined
     * @return the ShortAnswerSubmittedText or null if nothing was found
     */
    public ShortAnswerSubmittedText getSubmittedTextForSpot(ShortAnswerSpot spot) {
        if (spot == null || spot.getId() == null || !(getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
            return null;
        }
        ShortAnswerQuestion question = shortAnswerQuestion();
        for (ShortAnswerTextSelection entry : selection.getSubmittedTexts()) {
            if (spot.getId().equals(entry.getSpotId())) {
                return wrap(entry, question);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ShortAnswerSubmittedAnswer{" + "id=" + getId() + "}";
    }

    @Override
    public void filterOutCorrectAnswers() {
        super.filterOutCorrectAnswers();
        this.getSubmittedTexts().forEach(submittedText -> submittedText.setIsCorrect(null));
    }

    public Set<SAMapping> toSAMappings() {
        if (!(getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
            return new HashSet<>();
        }
        return selection.getSubmittedTexts().stream().map(entry -> new SAMapping(entry.getSpotId(), entry.getText())).collect(Collectors.toSet());
    }

}
