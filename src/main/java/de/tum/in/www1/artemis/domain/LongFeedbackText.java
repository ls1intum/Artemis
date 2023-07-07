package de.tum.in.www1.artemis.domain;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.config.Constants;

@Entity
@Table(name = "long_feedback_text")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class LongFeedbackText extends DomainObject {

    @Column(name = "feedback_text", nullable = false)
    @Size(max = Constants.LONG_FEEDBACK_MAX_LENGTH)
    private String text;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id", nullable = false)
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
