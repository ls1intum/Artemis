package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

/**
 * Automatic feedback of one executed test case for one {@link Result}.
 * <p>
 * This is the compact successor of the former test-case rows in the {@code feedback} table (previously
 * ~88% of all feedback rows). The row stores only what cannot be derived:
 * <ul>
 * <li>{@code positive} — whether the test passed ({@code null} = "test was not executed", matching the
 * tri-state semantics of {@link Feedback#isPositive()}),</li>
 * <li>{@code message} — the deduplicated failure/output message, {@code null} for plain pass markers.</li>
 * </ul>
 * Credits and visibility are <b>derived from the test case at read time</b> (weight formula respectively
 * {@link ProgrammingExerciseTestCase#getVisibility()}). This matches re-evaluation semantics: a
 * re-evaluation always overwrote both from the current test-case configuration anyway; grading-config
 * changes are recorded in the audit log, and {@code result.score} remains the stored, authoritative grade.
 * <p>
 * The {@code test_case_id} foreign key is {@code ON DELETE CASCADE} at the database level (same safety net
 * as the former {@code feedback.test_case_id}, changelog {@code 20260713120000}): it closes the race
 * between async build-result processing and a concurrent programming-exercise deletion. Rows of one result
 * are found through the index on {@code result_id}, which also supports the foreign key to {@code result}.
 */
@Entity
@Table(name = "test_case_feedback")
public class TestCaseFeedback extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id")
    @JsonIgnore
    private Result result;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id")
    private ProgrammingExerciseTestCase testCase;

    /**
     * Deliberately no database foreign key: referential integrity to the immutable, content-addressed
     * {@link FeedbackMessage} is application-enforced, and unreferenced messages are garbage-collected by
     * the admin orphan cleanup. A constraint would force an index over tens of millions of rows that no
     * query uses.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @JsonIgnore
    private FeedbackMessage message;

    @Column(name = "positive")
    private Boolean positive;

    public Result getResult() {
        return result;
    }

    /**
     * Wires this feedback to its owning result. Like {@link Feedback#setResult(Result)}, the result is the
     * container that owns the feedback via {@code CascadeType.ALL} and {@code orphanRemoval}.
     *
     * @param result the owning result
     */
    public void setResult(Result result) {
        this.result = result;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return testCase;
    }

    public void setTestCase(ProgrammingExerciseTestCase testCase) {
        this.testCase = testCase;
    }

    public FeedbackMessage getMessage() {
        return message;
    }

    public void setMessage(FeedbackMessage message) {
        this.message = message;
    }

    /**
     * @return the deduplicated message text, or {@code null} for plain pass markers. May trigger lazy
     *         initialization of the message.
     */
    @JsonIgnore
    public String getMessageText() {
        return message == null ? null : message.getText();
    }

    /**
     * See {@link Feedback#isPositive()}: {@code null} means the test was not executed for this submission.
     *
     * @return true if the test passed
     */
    public Boolean isPositive() {
        return positive;
    }

    public void setPositive(Boolean positive) {
        this.positive = positive;
    }

    /**
     * The visibility is derived from the test case (not stored): re-evaluation always overwrote the stored
     * value with the test-case visibility, so deriving makes every read behave as if re-evaluated.
     *
     * @return the visibility of the associated test case
     */
    @JsonIgnore
    public Visibility getVisibility() {
        return testCase == null ? null : testCase.getVisibility();
    }

    @JsonIgnore
    public boolean isInvisible() {
        return getVisibility() == Visibility.NEVER;
    }

    @JsonIgnore
    public boolean isAfterDueDate() {
        return getVisibility() == Visibility.AFTER_DUE_DATE;
    }

    /**
     * Constant hash code for the same reason as {@link Feedback#hashCode()}: the id is only assigned during
     * flush, and {@code Result.testCaseFeedbacks} is a {@link java.util.HashSet} — the id-based hash of
     * {@link DomainObject} would break set membership across the unsaved → persisted transition. The
     * id-based {@code equals} inherited from {@link DomainObject} still tells the rows apart.
     */
    @Override
    public int hashCode() {
        return TestCaseFeedback.class.hashCode();
    }

    @Override
    public String toString() {
        return "TestCaseFeedback{id=" + getId() + ", positive=" + positive + '}';
    }
}
