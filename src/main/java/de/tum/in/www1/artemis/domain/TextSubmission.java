package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A TextSubmission.
 */
@Entity
@DiscriminatorValue(value = "T")
public class TextSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_EXCERPT_LENGTH = 100;

    @Column(name = "text")
    @Lob
    private String text;

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

    public TextSubmission blocks(List<TextBlock> textBlocks) {
        this.blocks = textBlocks;
        return this;
    }

    public TextSubmission addBlock(TextBlock textBlock) {
        this.blocks.add(textBlock);
        textBlock.setSubmission(this);
        return this;
    }

    public TextSubmission removeBlock(TextBlock textBlock) {
        this.blocks.remove(textBlock);
        textBlock.setSubmission(null);
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
