package de.tum.in.www1.artemis.domain;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

/**
 * A Feedback.
 */
@Entity
@Table(name = "feedback")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Feedback extends DomainObject {

    public static final int MAX_REFERENCE_LENGTH = 2000;

    public static final String STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER = "SCAFeedbackIdentifier:";

    @Size(max = 500)
    @Column(name = "text")
    private String text;

    @Size(max = 5000)   // this ensures that the detail_text can be stored, even for long feedback
    @Column(name = "detail_text")
    private String detailText;

    /**
     * Reference to the assessed element (e.g. model element id or text element string)
     */
    @Size(max = MAX_REFERENCE_LENGTH)
    @Column(name = "reference")
    private String reference;

    /**
     * Absolute score for the assessed element (e.g. +0.5, -1.0, +2.0, etc.)
     */
    @Column(name = "credits")
    private Double credits;

    @Column(name = "positive")
    private Boolean positive;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private FeedbackType type;

    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private Result result;

    @ManyToOne
    private GradingInstruction gradingInstruction;

    // TODO: JP remove these two references as they are not really needed
    @OneToMany(mappedBy = "firstFeedback", orphanRemoval = true)
    private List<FeedbackConflict> firstConflicts = new ArrayList<>();

    @OneToMany(mappedBy = "secondFeedback", orphanRemoval = true)
    private List<FeedbackConflict> secondConflicts = new ArrayList<>();

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

    public String getReference() {
        return reference;
    }

    public Feedback reference(String reference) {
        this.reference = reference;
        return this;
    }

    public boolean hasReference() {
        return reference != null && !reference.isEmpty();
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    /**
     * For modeling submissions the reference looks like "<umlElementType>:<jsonElementId>". This function tries to split the reference string at ':' and returns the second part
     * (i.e. the jsonElementId).
     *
     * @return the jsonElementId for modeling submissions or null if the reference string does not contain ':'
     */
    @JsonIgnore
    public String getReferenceElementId() {
        if (reference == null || !reference.contains(":")) {
            return null;
        }
        return reference.split(":")[1];
    }

    /**
     * For modeling submissions the reference looks like "<umlElementType>:<jsonElementId>". This function tries to split the reference string at ':' and returns the first part
     * (i.e. the umlElementType).
     *
     * @return the umlElementType for modeling submissions or null if the reference string does not contain ':'
     */
    @JsonIgnore
    public String getReferenceElementType() {
        if (!reference.contains(":")) {
            return null;
        }
        return reference.split(":")[0];
    }

    public Double getCredits() {
        return credits;
    }

    public Feedback credits(Double credits) {
        this.credits = credits;
        return this;
    }

    public void setCredits(Double credits) {
        this.credits = credits;
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

    public GradingInstruction getGradingInstruction() {
        return gradingInstruction;
    }

    public void setGradingInstruction(GradingInstruction gradingInstruction) {
        this.gradingInstruction = gradingInstruction;
    }

    public List<FeedbackConflict> getFirstConflicts() {
        return firstConflicts;
    }

    public void setFirstConflicts(List<FeedbackConflict> firstConflicts) {
        this.firstConflicts = firstConflicts;
    }

    public List<FeedbackConflict> getSecondConflicts() {
        return secondConflicts;
    }

    public void setSecondConflicts(List<FeedbackConflict> secondConflicts) {
        this.secondConflicts = secondConflicts;
    }

    /**
     * Checks whether the feedback was created by static code analysis
     * @return true if the it is static code analysis feedback else false
     */
    @JsonIgnore
    public boolean isStaticCodeAnalysisFeedback() {
        return this.text != null && this.text.startsWith(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER) && this.type == FeedbackType.AUTOMATIC;
    }

    /**
     * Checks whether the feedback is not an automatically generated feedback (test cases or SCA). We check for
     * manual (FeedbackType.MANUAL), unreferenced (FeedbackType.MANUAL_UNREFERENCED) and general (null) feedback.
     * @return true if the it is not an automatically generated feedback else false
     */
    @JsonIgnore
    public boolean isNotAutomaticFeedback() {
        return this.type != FeedbackType.AUTOMATIC || this.type == null;
    }

    public boolean referenceEquals(Feedback otherFeedback) {
        return reference.equals(otherFeedback.reference);
    }

    @Override
    public String toString() {
        return "Feedback{" + "id=" + getId() + ", text='" + getText() + "'" + ", detailText='" + getDetailText() + "'" + ", reference='" + getReference() + "'" + ", positive='"
                + isPositive() + "'" + ", type='" + getType() + ", gradingInstruction='" + getGradingInstruction() + "'" + "}";
    }
}
