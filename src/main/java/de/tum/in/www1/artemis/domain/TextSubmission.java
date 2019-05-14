package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.enumeration.Language;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "language")
    private Language language;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

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

    public Language getLanguage() {
        return language;
    }

    public TextSubmission language(Language language) {
        this.language = language;
        return this;
    }

    public void setLanguage(Language language) {
        this.language = language;
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
