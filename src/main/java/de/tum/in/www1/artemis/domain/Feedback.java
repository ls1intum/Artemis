package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Objects;

/**
 * A Feedback.
 */
@Entity
@Table(name = "feedback")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Feedback implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(max = 500)
    @Column(name = "text")
    private String text;

    @Size(max = 5000)   // this ensures that the detail_text can be stored, even for long feedback
    @Column(name = "detail_text")
    private String detailText;

    /**
     * Reference to the assessed element (e.g. model element id or text element string)
     */
    @Size(max = 2000)
    @Column(name = "reference")
    private String reference;

    /**
     * Absolute score for the assessed element (e.g. +0.5, -1.0, +2.0, etc.)
     */
    @Column(name = "credits ")
    private Double credits;

    @Column(name = "positive")
    private Boolean positive;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private FeedbackType type;

    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private Result result;

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

    public String getReference() { return reference; }

    public Feedback reference(String reference) {
        this.reference = reference;
        return this;
    }

    public void setReference(String reference) { this.reference = reference; }

    /**
     * For modeling submissions the reference looks like "<umlElementType>:<jsonElementId>". This function tries to
     * split the reference string at ':' and returns the second part (i.e. the jsonElementId).
     *
     * @return the jsonElementId for modeling submissions or null if the reference string does not contain ':'
     */
    public String getReferenceElementId() {
        if (!reference.contains(":")) {
            return null;
        }
        return reference.split(":")[1];
    }

    /**
     * For modeling submissions the reference looks like "<umlElementType>:<jsonElementId>". This function tries to
     * split the reference string at ':' and returns the first part (i.e. the umlElementType).
     *
     * @return the umlElementType for modeling submissions or null if the reference string does not contain ':'
     */
    public String getReferenceElementType() {
        if (!reference.contains(":")) {
            return null;
        }
        return reference.split(":")[0];
    }

    public Double getCredits() { return credits; }

    public Feedback credits(Double credits) {
        this.credits = credits;
        return this;
    }

    public void setCredits(Double credits) { this.credits = credits; }

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

    public Result getResult() {
        return result;
    }

    public Feedback result(Result result) {
        this.result = result;
        return this;
    }

    public void setResult(Result result) {
        this.result = result;
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

    public boolean referenceEquals(Feedback otherFeedback) {
        return reference.equals(otherFeedback.reference);
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
