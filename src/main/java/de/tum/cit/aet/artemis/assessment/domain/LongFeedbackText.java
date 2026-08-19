package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "long_feedback_text")
public class LongFeedbackText extends DomainObject {

    @Column(name = "feedback_text", nullable = false)
    private String text;

    /**
     * The feedback this long text belongs to. A long feedback text is overflow storage for a single
     * {@link Feedback} (1:1 via the unique index on {@code feedback_id}) and is never meaningful on its own.
     * <p>
     * On the database side {@code fk_long_feedback_to_feedback} is {@code ON DELETE CASCADE}: deleting a feedback
     * row removes its long feedback text as well. This matters for the database-level cascade that deletes
     * feedback together with a {@code programming_exercise_test_case} (see {@link Feedback#testCase}); that path
     * bypasses JPA, so without the database cascade a long feedback text would block the feedback delete. In the
     * normal, JPA-driven path the child is already removed via {@code cascade = ALL, orphanRemoval = true} on
     * {@link Feedback#getLongFeedbackText()}.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
    @JsonIgnore
    private Feedback feedback;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    @Override
    public String toString() {
        return "LongFeedbackText{id=" + getId() + ", text='" + text + '\'' + '}';
    }
}
