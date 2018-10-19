package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

/**
 * A Feedback.
 */
@Entity
@Table(name = "feedback")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Feedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String text;

    @Column(name = "detail_text")
    private String detailText;

    @Column(name = "positive")
    private Boolean positive;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private FeedbackType type;

    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private ExerciseResult result;

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

    public Feedback text(String text) {
        this.text = text;
        return this;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDetailText() {
        return detailText;
    }

    public Feedback detailText(String detailText) {
        this.detailText = detailText;
        return this;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText;
    }

    public Boolean isPositive() {
        return positive;
    }

    public Feedback positive(Boolean positive) {
        this.positive = positive;
        return this;
    }

    public void setPositive(Boolean positive) {
        this.positive = positive;
    }

    public FeedbackType getType() {
        return type;
    }

    public Feedback type(FeedbackType type) {
        this.type = type;
        return this;
    }

    public void setType(FeedbackType type) {
        this.type = type;
    }

    public ExerciseResult getResult() {
        return result;
    }

    public Feedback result(ExerciseResult exerciseResult) {
        this.result = exerciseResult;
        return this;
    }

    public void setResult(ExerciseResult exerciseResult) {
        this.result = exerciseResult;
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
        Feedback feedback = (Feedback) o;
        if (feedback.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), feedback.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Feedback{" +
            "id=" + getId() +
            ", text='" + getText() + "'" +
            ", detailText='" + getDetailText() + "'" +
            ", positive='" + isPositive() + "'" +
            ", type='" + getType() + "'" +
            "}";
    }
}
