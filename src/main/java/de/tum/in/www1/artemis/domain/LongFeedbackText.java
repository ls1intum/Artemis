package de.tum.in.www1.artemis.domain;

import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

public class LongFeedbackText extends DomainObject {

    @Column(name = "feedback_text", nullable = false)
    private String text;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "feedback_id")
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
        return "LongFeedbackText{text='" + text + '\'' + ", feedback=" + feedback + '}';
    }
}
