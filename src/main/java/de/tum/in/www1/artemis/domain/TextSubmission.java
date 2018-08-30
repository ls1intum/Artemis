package de.tum.in.www1.artemis.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * A TextSubmission.
 */
@Entity
@DiscriminatorValue(value="T")
public class TextSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "text")
    @Lob
    private String text;

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
        return "TextSubmission{" +
            "id=" + getId() +
            ", text='" + getText() + "'" +
            "}";
    }
}
