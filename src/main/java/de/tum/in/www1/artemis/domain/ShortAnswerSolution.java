package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import de.tum.in.www1.artemis.domain.view.QuizView;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A ShortAnswerSolution.
 */
@Entity
@Table(name = "short_answer_solution")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ShortAnswerSolution implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonView(QuizView.Before.class)
    private Long id;

    @Column(name = "text")
    @JsonView(QuizView.Before.class)
    private String text;

    @Column(name = "invalid")
    @JsonView(QuizView.Before.class)
    private Boolean invalid;

    @ManyToOne
    @JsonIgnore
    private ShortAnswerQuestion question;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public ShortAnswerSolution text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean isInvalid() {
        return invalid;
    }

    public ShortAnswerSolution invalid(Boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public void setInvalid(Boolean invalid) {
        this.invalid = invalid;
    }

    public ShortAnswerQuestion getQuestion() {
        return question;
    }

    public ShortAnswerSolution question(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
        return this;
    }

    public void setQuestion(ShortAnswerQuestion shortAnswerQuestion) {
        this.question = shortAnswerQuestion;
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
        ShortAnswerSolution shortAnswerSolution = (ShortAnswerSolution) o;
        if (shortAnswerSolution.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), shortAnswerSolution.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "ShortAnswerSolution{" +
            "id=" + getId() +
            ", text='" + getText() + "'" +
            ", invalid='" + isInvalid() + "'" +
            "}";
    }
}
