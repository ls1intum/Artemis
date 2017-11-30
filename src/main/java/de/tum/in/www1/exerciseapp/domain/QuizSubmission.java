package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * A QuizSubmission.
 */
@Entity
@DiscriminatorValue(value="Q")
//@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizSubmission extends Submission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "score_in_points")
    private Double scoreInPoints;

    @OneToMany(mappedBy = "submission", cascade= CascadeType.ALL, fetch= FetchType.EAGER, orphanRemoval=true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<SubmittedAnswer> submittedAnswers = new HashSet<>();

    public Double getScoreInPoints() {
        return scoreInPoints;
    }

    public QuizSubmission scoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
        return this;
    }

    public void setScoreInPoints(Double scoreInPoints) {
        this.scoreInPoints = scoreInPoints;
    }

    public Set<SubmittedAnswer> getSubmittedAnswers() {
        return submittedAnswers;
    }

    public QuizSubmission submittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
        return this;
    }

    public QuizSubmission addSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.add(submittedAnswer);
        submittedAnswer.setSubmission(this);
        return this;
    }

    public QuizSubmission removeSubmittedAnswers(SubmittedAnswer submittedAnswer) {
        this.submittedAnswers.remove(submittedAnswer);
        submittedAnswer.setSubmission(null);
        return this;
    }

    public void setSubmittedAnswers(Set<SubmittedAnswer> submittedAnswers) {
        this.submittedAnswers = submittedAnswers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QuizSubmission quizSubmission = (QuizSubmission) o;
        if (quizSubmission.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), quizSubmission.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuizSubmission{" +
            "id=" + getId() +
            ", scoreInPoints='" + getScoreInPoints() + "'" +
            "}";
    }
}
