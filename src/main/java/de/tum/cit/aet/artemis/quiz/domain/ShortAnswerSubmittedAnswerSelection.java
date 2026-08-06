package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The submission-side selection of a {@link ShortAnswerSubmittedAnswer}: the list of texts the student submitted, one per answered spot. Stored inside the
 * {@code submitted_answer.selection} JSON column (see {@link SubmittedAnswerSelection}). Mirrors {@link DragAndDropSubmittedAnswerSelection}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ShortAnswerSubmittedAnswerSelection implements SubmittedAnswerSelection {

    @JsonProperty("texts")
    private List<ShortAnswerTextSelection> submittedTexts = new ArrayList<>();

    public List<ShortAnswerTextSelection> getSubmittedTexts() {
        return submittedTexts;
    }

    public void setSubmittedTexts(List<ShortAnswerTextSelection> submittedTexts) {
        this.submittedTexts = submittedTexts != null ? submittedTexts : new ArrayList<>();
    }
}
