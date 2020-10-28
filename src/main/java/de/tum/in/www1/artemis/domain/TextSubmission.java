package de.tum.in.www1.artemis.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

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
    @Lob
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties("submission")
    private List<TextBlock> blocks = new ArrayList<>();

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

    public List<TextBlock> getBlocks() {
        return blocks;
    }

    public TextSubmission addBlock(TextBlock textBlock) {
        this.blocks.add(textBlock);
        textBlock.setSubmission(this);
        return this;
    }

    public void setBlocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
    }

    public boolean isEmpty() {
        return text == null || text.isEmpty();
    }

    @Override
    public String toString() {
        return "TextSubmission{" + "id=" + getId() + ", text='" + getExcerpt() + "'" + ", language='" + getLanguage() + "'" + "}";
    }
}
