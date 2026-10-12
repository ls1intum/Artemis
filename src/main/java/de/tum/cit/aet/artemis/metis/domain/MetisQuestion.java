package de.tum.cit.aet.artemis.metis.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * One question of a Metis comprehension check together with the student's answer to it and the LLM's rating of that
 * answer. Question, answer and rating share one row because they have the same grain: there is exactly one answer per
 * question and one rating per answer.
 *
 * <p>
 * Main questions are generated from the student's git diff, each about one random focus file whose path and changed-line
 * count the row keeps. Follow-up questions are asked when an answer scores at or below the configured threshold; they are rows
 * of the same table pointing at their main question through {@link #parentQuestionId} and get the next free
 * {@link #questionOrder} inside the check.
 *
 * <p>
 * The check is the parent, and a follow-up is additionally removed with its main question. The pair (check, order) is
 * unique in the schema, and the key from a follow-up to its main question covers the check as well, so a follow-up can only
 * point at a question of its own check. The schema does not check that this question is a main question; keeping follow-ups
 * one level deep is up to the code that writes them.
 */
@Entity
@Table(name = "metis_question")
public class MetisQuestion extends DomainObject {

    /** The check the question belongs to. A parent: the question has no meaning without it and cascades away with it. */
    @Column(name = "check_id", nullable = false)
    @Parent
    private long checkId;

    /** Display order inside the check; follow-ups get the next free number. Unique per check. */
    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    /**
     * The main question this is a follow-up to, or null for a main question. The main question belongs to the same check, which
     * the schema enforces, and a follow-up is removed with it.
     */
    @Nullable
    @Column(name = "parent_question_id")
    private Long parentQuestionId;

    /**
     * Repository-relative path of the file in the student's diff the question is about, up to 1024 characters like the paths the
     * repository file endpoints accept. Main questions only; null for follow-ups.
     */
    @Nullable
    @Column(name = "focus_file_path", length = 1024)
    private String focusFilePath;

    /** Added plus deleted lines of the focus file at generation time. Main questions only; null for follow-ups. */
    @Nullable
    @Column(name = "focus_file_changed_lines")
    private Integer focusFileChangedLines;

    /** The question as shown to the student. */
    @Column(name = "question_text", nullable = false)
    private String questionText;

    /** Seconds the student has to answer once the question is shown. Chosen by the LLM, clamped to the configured range. */
    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    /** How often an instructor had this question regenerated during review. */
    @Column(name = "regeneration_count", nullable = false)
    private int regenerationCount = 0;

    /** When an instructor approved the question, when the exercise requires a review; null otherwise or until then. */
    @Nullable
    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    /** When the question was shown to the student, which starts the server-side timer; null until then. */
    @Nullable
    @Column(name = "shown_at")
    private ZonedDateTime shownAt;

    /** The student's answer, or null until the student has answered. */
    @Nullable
    @Column(name = "answer_text")
    private String answerText;

    /** When the student answered, or null until then. */
    @Nullable
    @Column(name = "answered_at")
    private ZonedDateTime answeredAt;

    /** The LLM's rating of the answer, from 1 to the configured number of Likert scale points; null until rated. */
    @Nullable
    @Column(name = "llm_score")
    private Integer llmScore;

    /** The LLM's reasoning for its rating, or null until rated. */
    @Nullable
    @Column(name = "llm_rationale")
    private String llmRationale;

    /** When the LLM rated the answer, or null until then. */
    @Nullable
    @Column(name = "llm_rated_at")
    private ZonedDateTime llmRatedAt;

    public long getCheckId() {
        return checkId;
    }

    public void setCheckId(long checkId) {
        this.checkId = checkId;
    }

    public int getQuestionOrder() {
        return questionOrder;
    }

    public void setQuestionOrder(int questionOrder) {
        this.questionOrder = questionOrder;
    }

    @Nullable
    public Long getParentQuestionId() {
        return parentQuestionId;
    }

    public void setParentQuestionId(@Nullable Long parentQuestionId) {
        this.parentQuestionId = parentQuestionId;
    }

    @Nullable
    public String getFocusFilePath() {
        return focusFilePath;
    }

    public void setFocusFilePath(@Nullable String focusFilePath) {
        this.focusFilePath = focusFilePath;
    }

    @Nullable
    public Integer getFocusFileChangedLines() {
        return focusFileChangedLines;
    }

    public void setFocusFileChangedLines(@Nullable Integer focusFileChangedLines) {
        this.focusFileChangedLines = focusFileChangedLines;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public int getRegenerationCount() {
        return regenerationCount;
    }

    public void setRegenerationCount(int regenerationCount) {
        this.regenerationCount = regenerationCount;
    }

    @Nullable
    public ZonedDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(@Nullable ZonedDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    @Nullable
    public ZonedDateTime getShownAt() {
        return shownAt;
    }

    public void setShownAt(@Nullable ZonedDateTime shownAt) {
        this.shownAt = shownAt;
    }

    @Nullable
    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(@Nullable String answerText) {
        this.answerText = answerText;
    }

    @Nullable
    public ZonedDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(@Nullable ZonedDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    @Nullable
    public Integer getLlmScore() {
        return llmScore;
    }

    public void setLlmScore(@Nullable Integer llmScore) {
        this.llmScore = llmScore;
    }

    @Nullable
    public String getLlmRationale() {
        return llmRationale;
    }

    public void setLlmRationale(@Nullable String llmRationale) {
        this.llmRationale = llmRationale;
    }

    @Nullable
    public ZonedDateTime getLlmRatedAt() {
        return llmRatedAt;
    }

    public void setLlmRatedAt(@Nullable ZonedDateTime llmRatedAt) {
        this.llmRatedAt = llmRatedAt;
    }

    /**
     * Whether this is a follow-up to a main question rather than a main question itself.
     *
     * @return true when the question points at a main question
     */
    @JsonIgnore
    public boolean isFollowUp() {
        return parentQuestionId != null;
    }
}
