package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_PREVIEW_TEXT_MAX_LENGTH;
import static de.tum.in.www1.artemis.config.Constants.LONG_FEEDBACK_MAX_LENGTH;

import java.util.*;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    private static final String DETAIL_TEXT_TRIMMED_MARKER = " [...]";

    @Size(max = 500)
    @Column(name = "text", length = 500)
    private String text;

    @Size(max = FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH)   // this ensures that the detail_text can be stored, even for long feedback
    @Column(name = "detail_text", length = FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH)
    private String detailText;

    @Column(name = "has_long_feedback_text")
    private boolean hasLongFeedbackText = false;

    @OneToMany(mappedBy = "feedback", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore // important, data should only be requested explicitly
    private Set<LongFeedbackText> longFeedbackText = new HashSet<>();

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

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "type")
    private FeedbackType type;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "visibility")
    private Visibility visibility;

    @ManyToOne
    @JsonIgnoreProperties("feedbacks")
    private Result result;

    @ManyToOne
    private GradingInstruction gradingInstruction;

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
     * Sets the detail text by cutting it off at the maximum length the database can store.
     * <p>
     * If you want to store longer feedback, use {@link #setDetailText(String)} instead.
     *
     * @param detailText The detail text for this feedback.
     */
    public void setDetailTextTruncated(@Nullable final String detailText) {
        this.detailText = StringUtils.truncate(detailText, FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
        this.longFeedbackText.clear();
        this.hasLongFeedbackText = false;
    }

    /**
     * Sets the detail text of the feedback.
     * <p>
     * Always stores the whole detail text.
     * In case the feedback is shorter than {@link de.tum.in.www1.artemis.config.Constants#FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH},
     * the feedback is stored directly in the detail text.
     * Otherwise, an associated {@link LongFeedbackText} is attached that holds the full feedback.
     * In this case the actual detail text stored in this feedback only contains a short preview.
     * <p>
     * If you do <emph>not</emph> want a long feedback to be created, use {@link #setDetailTextTruncated(String)} instead.
     *
     * @param detailText the new detail text for the feedback, can be null
     */
    public void setDetailText(@Nullable final String detailText) {
        if (detailText == null || detailText.length() <= FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH) {
            this.detailText = detailText;
            setHasLongFeedbackText(false);
            this.longFeedbackText.clear();
        }
        else {
            final LongFeedbackText longFeedback = buildLongFeedback(detailText);

            this.detailText = trimDetailText(detailText);
            setHasLongFeedbackText(true);
            setLongFeedback(longFeedback);
        }
    }

    private String trimDetailText(final String detailText) {
        final int maxLength = FEEDBACK_PREVIEW_TEXT_MAX_LENGTH - DETAIL_TEXT_TRIMMED_MARKER.length();
        return StringUtils.truncate(detailText, maxLength) + DETAIL_TEXT_TRIMMED_MARKER;
    }

    private LongFeedbackText buildLongFeedback(final String detailText) {
        final LongFeedbackText longFeedback = new LongFeedbackText();
        longFeedback.setText(StringUtils.truncate(detailText, LONG_FEEDBACK_MAX_LENGTH));
        return longFeedback;
    }

    public boolean getHasLongFeedbackText() {
        return hasLongFeedbackText;
    }

    /**
     * Only for JPA, do not use directly. Use {@link #setDetailText(String)} instead.
     *
     * @param hasLongFeedbackText True, if the feedback has a long feedback text.
     */
    public void setHasLongFeedbackText(boolean hasLongFeedbackText) {
        this.hasLongFeedbackText = hasLongFeedbackText;
    }

    @JsonIgnore
    public Optional<LongFeedbackText> getLongFeedback() {
        return getLongFeedbackText().stream().findAny();
    }

    private void setLongFeedback(final LongFeedbackText longFeedbackText) {
        this.longFeedbackText.clear();
        longFeedbackText.setFeedback(this);
        this.longFeedbackText.add(longFeedbackText);
    }

    /**
     * Only for JPA, do not use directly. Use {@link #getLongFeedback()} instead.
     *
     * @return The long feedback this feedback is attached to.
     */
    @JsonIgnore
    public Set<LongFeedbackText> getLongFeedbackText() {
        return longFeedbackText;
    }

    /**
     * Only for JPA, do not use directly. Use {@link #setDetailText(String)} instead.
     *
     * @param longFeedbackText The long feedback text this feedback is linked to.
     */
    @SuppressWarnings("unused")
    public void setLongFeedbackText(final Set<LongFeedbackText> longFeedbackText) {
        this.longFeedbackText = longFeedbackText;
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
     * <p>
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
     *
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

    /**
     * Checks whether the feedback was created by static code analysis
     *
     * @return true if it is static code analysis feedback else false
     */
    @JsonIgnore
    public boolean isStaticCodeAnalysisFeedback() {
        return this.text != null && this.text.startsWith(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER) && this.type == FeedbackType.AUTOMATIC;
    }

    /**
     * Checks whether the feedback was created by a submission policy
     *
     * @return true if it is submission policy feedback else false
     */
    @JsonIgnore
    public boolean isSubmissionPolicyFeedback() {
        return this.text != null && this.text.startsWith(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER) && this.type == FeedbackType.AUTOMATIC;
    }

    /**
     * Checks whether the feedback was created by an automatic test
     *
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
     * Calculates the score over all feedback elements that were set using structured grading instructions (SGI)
     *
     * @param inputScore          totalScore which is summed up.
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

    @Override
    public String toString() {
        return "Feedback{" + "text='" + text + '\'' + ", detailText='" + detailText + '\'' + ", hasLongFeedbackText=" + hasLongFeedbackText + ", reference='" + reference + '\''
                + ", credits=" + credits + ", positive=" + positive + ", type=" + type + ", visibility=" + visibility + '}';
    }
}
