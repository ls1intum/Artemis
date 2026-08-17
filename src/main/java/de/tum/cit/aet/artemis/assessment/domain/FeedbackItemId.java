package de.tum.cit.aet.artemis.assessment.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Composite primary key for feedback rows that belong to a {@link Result}: the owning result plus a
 * per-result sequence number.
 * <p>
 * Used by {@link TestCaseFeedback} and {@link ScaFeedback}. A composite key (instead of an auto-increment
 * surrogate id) saves the id column and one secondary index per table on the largest tables in the
 * database. The sequence is NOT semantically meaningful — it only makes rows of one result distinct;
 * in particular {@code (result, testCase)} is deliberately not unique, because a build can report the
 * same test case more than once.
 */
@Embeddable
public class FeedbackItemId implements Serializable {

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "seq")
    private int seq;

    public FeedbackItemId() {
        // for JPA
    }

    public FeedbackItemId(Long resultId, int seq) {
        this.resultId = resultId;
        this.seq = seq;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FeedbackItemId otherId)) {
            return false;
        }
        return seq == otherId.seq && Objects.equals(resultId, otherId.resultId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultId, seq);
    }

    @Override
    public String toString() {
        return "FeedbackItemId{resultId=" + resultId + ", seq=" + seq + '}';
    }
}
