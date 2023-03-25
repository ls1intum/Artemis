package de.tum.in.www1.artemis.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;

@Entity
@Table(name = "long_feedback_text")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LongFeedbackText implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Note: we cannot extend {@link DomainObject} since the id is *not* autogenerated here.
     */
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "feedback_text", nullable = false)
    @Size(max = Constants.LONG_FEEDBACK_MAX_LENGTH)
    private String text;

    @MapsId
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id")
    private Feedback feedback;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @JsonIgnore
    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    public LongFeedbackText copy() {
        final LongFeedbackText longFeedbackText = new LongFeedbackText();
        longFeedbackText.setText(getText());
        return longFeedbackText;
    }

    /**
     * this method checks for database equality based on the id
     *
     * @param obj another object
     * @return whether this and the other object are equal based on the database id
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        else if (obj instanceof LongFeedbackText longFeedbackText) {
            final boolean bothIdsNull = getId() == null && longFeedbackText.getId() == null;
            return !bothIdsNull && Objects.equals(getId(), longFeedbackText.getId());
        }
        else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "LongFeedbackText{id=" + id + ", text='" + text + '\'' + '}';
    }
}
