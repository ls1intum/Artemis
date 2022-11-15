package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.MAX_SUBMISSION_TEXT_LENGTH;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.Language;

/**
 * A TextSubmission.
 */
@Entity
@DiscriminatorValue(value = "T")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextSubmission extends Submission {

    private static final int MAX_EXCERPT_LENGTH = 100;

    @Column(name = "text")
    @Size(max = MAX_SUBMISSION_TEXT_LENGTH, message = "The text submission is too large.")
    // @Lob
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties("submission")
    private Set<TextBlock> blocks = new HashSet<>();

    public TextSubmission() {
    }

    public TextSubmission(Long id) {
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

    public TextSubmission text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Set<TextBlock> getBlocks() {
        return blocks;
    }

    public void addBlock(TextBlock textBlock) {
        this.blocks.add(textBlock);
        textBlock.setSubmission(this);
    }

    public void setBlocks(Set<TextBlock> textBlocks) {
        this.blocks = textBlocks;
    }

    @Override
    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public String toString() {
        return "TextSubmission{" + "id=" + getId() + ", text='" + getExcerpt() + "'" + ", language='" + getLanguage() + "'" + "}";
    }

    /**
     * counts the number of words in the text of the text submission in case
     * @return the number of words
     */
    public int countWords() {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String[] words = text.split("\\s+");
        return words.length;
    }
}
