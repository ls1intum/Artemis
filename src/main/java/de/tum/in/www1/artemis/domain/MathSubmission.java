package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;

/**
 * A MathSubmission.
 */
@Entity
@DiscriminatorValue(value = "M")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathSubmission extends Submission {

    @Override
    public String getSubmissionExerciseType() {
        return "math";
    }

    private static final int MAX_EXCERPT_LENGTH = 100;

    @Column(name = "text")
    @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The text submission is too large.")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    public MathSubmission() {
    }

    public MathSubmission(Long id) {
        setId(id);
    }

    public String getText() {
        return text;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    /**
     * Excerpt of Text, used for toString() so log messages do not get too long.
     *
     * @return excerpt of text, maximum String length of 104 characters
     */
    @JsonIgnore()
    public String getExcerpt() {
        if (getText() == null) {
            return "";
        }
        if (getText().length() > MAX_EXCERPT_LENGTH) {
            return getText().substring(0, MAX_EXCERPT_LENGTH) + " ...";
        }
        else {
            return getText();
        }
    }

    public MathSubmission text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public String toString() {
        return "MathSubmission{" + "id=" + getId() + ", text='" + getExcerpt() + "'" + ", language='" + getLanguage() + "'" + "}";
    }

}
