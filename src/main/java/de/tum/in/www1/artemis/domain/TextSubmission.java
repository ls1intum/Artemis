package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A TextSubmission.
 */
@Entity
@DiscriminatorValue(value = "T")
public class TextSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "text")
    @Lob
    private String text;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.REMOVE)
    @JsonIgnoreProperties("submission")
    private List<TextBlock> blocks = new ArrayList<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public TextSubmission() {
    }

    public TextSubmission(Long id) {
        setId(id);
    }

    public String getText() {
        return text;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TextSubmission textSubmission = (TextSubmission) o;
        if (textSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), textSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "TextSubmission{" + "id=" + getId() + ", text='" + getText() + "'" + ", language='" + getLanguage() + "'" + "}";
    }
}
