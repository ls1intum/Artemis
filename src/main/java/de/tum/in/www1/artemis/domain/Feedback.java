package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS;

import java.util.*;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;

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

    public static final String SUBMISSION_POLICY_FEEDBACK_IDENTIFIER = "SubPolFeedbackIdentifier:";

    @Size(max = 500)
    @Column(name = "text", length = 500)
    private String text;

    @Size(max = FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS)   // this ensures that the detail_text can be stored, even for long feedback
    @Column(name = "detail_text", length = FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS)
    private String detailText;

    /**
     * Reference to the assessed element (e.g. model element id or text element string)
     */
    @Size(max = MAX_REFERENCE_LENGTH)
    @Column(name = "reference", length = MAX_REFERENCE_LENGTH)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private Visibility visibility;

    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private Result result;

    @ManyToOne
    private GradingInstruction gradingInstruction;

    /**
     * Represents the reference of the previously assessed block, whose feedback we are reusing
     */
    @Transient
    @JsonSerialize
    private String suggestedFeedbackReference;

    /**
     * Represents the submission of the previously assessed block, whose feedback we are reusing
     */
    @Transient
    @JsonSerialize
    private Long suggestedFeedbackOriginSubmissionReference;

    /**
     * Represents the participation reference of the submission to which the previously assessed block being reused belongs to
     */
    @Transient
    @JsonSerialize
    private Long suggestedFeedbackParticipationReference;

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

    public Feedback detailText(@Nullable String detailText) {
        this.setDetailText(detailText);
        return this;
    }

    /**
     * sets the detail text of the feedback. In case the detail text is longer than 5000 characters, the additional characters are cut off to avoid database issues
     * @param detailText the new detail text for the feedback, can be null
     */
    public void setDetailText(@Nullable String detailText) {
        if (detailText == null || detailText.length() <= FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS) {
            this.detailText = detailText;
        }
        else {
            this.detailText = detailText.substring(0, FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS);
        }
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

    /**
     * Returns if this is a positive feedback.
     *
     * This value can actually be {@code null} for feedbacks that are neither positive nor negative, e.g. when this is a
     * feedback for a programming exercise test case that has not been executed for the submission.
     *
     * @return true, if this is a positive feedback.
     */
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

    public void setPositiveViaCredits() {
        this.positive = credits != null && credits >= 0;
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

    public Visibility getVisibility() {
        return visibility;
    }

    @JsonIgnore
    public boolean isAfterDueDate() {
        return this.visibility == Visibility.AFTER_DUE_DATE;
    }

    @JsonIgnore
    public boolean isInvisible() {
        return this.visibility == Visibility.NEVER;
    }

    public Feedback visibility(Visibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public Result getResult() {
        return result;
    }

    public Feedback result(Result result) {
        this.result = result;
        return this;
    }

    /**
     * be careful when using this method as it might result in org.hibernate.HibernateException: null index column for collection: de.tum.in.www1.artemis.domain.Result.feedbacks
     * when saving the result. The result object is the container that owns the feedback and uses CascadeType.ALL and orphanRemoval
     * @param result the result container object that owns the feedback
     */
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

    public String getSuggestedFeedbackReference() {
        return suggestedFeedbackReference;
    }

    public void setSuggestedFeedbackOriginBlock(String suggestedFeedbackOriginBlockId) {
        this.suggestedFeedbackReference = suggestedFeedbackOriginBlockId;
    }

    public Long getSuggestedFeedbackOriginSubmissionReference() {
        return suggestedFeedbackOriginSubmissionReference;
    }

    public void setSuggestedFeedbackOriginSubmission(Long suggestedFeedbackOriginSubmission) {
        this.suggestedFeedbackOriginSubmissionReference = suggestedFeedbackOriginSubmission;
    }

    public Long getSuggestedFeedbackParticipationReference() {
        return suggestedFeedbackParticipationReference;
    }

    public void setSuggestedFeedbackParticipationReference(Long suggestedFeedbackParticipationReference) {
        this.suggestedFeedbackParticipationReference = suggestedFeedbackParticipationReference;
    }

    /**
     *  This function sets the described parameters and then returns the current instance with the updated references.
     *
     * @param suggestedFeedbackOriginBlockReference - Block reference of the suggested (automatic) feedback
     * @param submissionId - Submission reference where the suggested feedback was generated from
     * @param suggestedFeedbackParticipationId - respective participation reference
     * @return updated Feedback
     */
    public Feedback suggestedFeedbackOrigin(String suggestedFeedbackOriginBlockReference, Long submissionId, Long suggestedFeedbackParticipationId) {
        this.suggestedFeedbackReference = suggestedFeedbackOriginBlockReference;
        this.suggestedFeedbackOriginSubmissionReference = submissionId;
        this.suggestedFeedbackParticipationReference = suggestedFeedbackParticipationId;
        return this;
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
     * @return true if it is static code analysis feedback else false
     */
    @JsonIgnore
    public boolean isStaticCodeAnalysisFeedback() {
        return this.text != null && this.text.startsWith(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER) && this.type == FeedbackType.AUTOMATIC;
    }

    /**
     * Checks whether the feedback was created by a submission policy
     * @return true if it is submission policy feedback else false
     */
    @JsonIgnore
    public boolean isSubmissionPolicyFeedback() {
        return this.text != null && this.text.startsWith(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER) && this.type == FeedbackType.AUTOMATIC;
    }

    /**
     * Checks whether the feedback was created by an automatic test
     * @return true if it is a test feedback else false
     */
    @JsonIgnore
    public boolean isTestFeedback() {
        return this.type == FeedbackType.AUTOMATIC && !isStaticCodeAnalysisFeedback() && !isSubmissionPolicyFeedback();
    }

    /**
     * Returns the Artemis static code analysis category to which this feedback belongs. The method returns an empty
     * String, if the feedback is not static code analysis feedback.
     *
     * @return The Artemis static code analysis category to which this feedback belongs
     */
    @JsonIgnore
    public String getStaticCodeAnalysisCategory() {
        if (isStaticCodeAnalysisFeedback()) {
            return this.getText().substring(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length());
        }
        return "";
    }

    /**
     * Copies an automatic feedback to be used for the manual result of a programming exercise
     * @return Copy of the automatic feedback without its original ID
     */
    public Feedback copyFeedback() {
        var feedback = new Feedback();
        feedback.setDetailText(getDetailText());
        feedback.setType(getType());
        // For manual result each feedback needs to have a credit. If no credit is set, we set it to 0.0
        feedback.setCredits(Objects.requireNonNullElse(getCredits(), 0.0));
        feedback.setText(getText());
        feedback.setPositive(isPositive());
        feedback.setReference(getReference());
        feedback.setVisibility(getVisibility());
        feedback.setGradingInstruction(getGradingInstruction());
        return feedback;
    }

    @Override
    public String toString() {
        return "Feedback{" + "id=" + getId() + ", text='" + getText() + "'" + ", detailText='" + getDetailText() + "'" + ", reference='" + getReference() + "'" + ", positive='"
                + isPositive() + "'" + ", type='" + getType() + ", visibility=" + getVisibility() + ", gradingInstruction='" + getGradingInstruction() + "'" + "}";
    }

    /**
     * Calculates the score over all feedback elements that were set using structured grading instructions (SGI)
     * @param inputScore totalScore which is summed up.
     * @param gradingInstructions empty grading instruction Map to collect the used gradingInstructions
     * @return calculated total score from feedback elements set by SGI
     */
    @JsonIgnore
    public double computeTotalScore(double inputScore, Map<Long, Integer> gradingInstructions) {
        double totalScore = inputScore;
        if (gradingInstructions.get(getGradingInstruction().getId()) != null) {
            // We Encountered this grading instruction before
            var maxCount = getGradingInstruction().getUsageCount();
            var encounters = gradingInstructions.get(getGradingInstruction().getId());
            if (maxCount > 0) {
                if (encounters >= maxCount) {
                    // the structured grading instruction was applied on assessment models more often that the usageCount limit allows, so we don't sum the feedback credit
                    gradingInstructions.put(getGradingInstruction().getId(), encounters + 1);
                }
                else {
                    // the usageCount limit was not exceeded yet, so we add the credit and increase the nrOfEncounters counter
                    gradingInstructions.put(getGradingInstruction().getId(), encounters + 1);
                    totalScore += getGradingInstruction().getCredits();
                }
            }
            else {
                totalScore += getCredits();
            }
        }
        else {
            // First time encountering the grading instruction
            gradingInstructions.put(getGradingInstruction().getId(), 1);
            totalScore += getCredits();
        }
        return totalScore;
    }
}
