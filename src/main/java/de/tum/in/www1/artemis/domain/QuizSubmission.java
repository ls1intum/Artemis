package de.tum.in.www1.artemis.domain;

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
@Table(name = "quiz_submission")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuizSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "submission")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<SubmittedAnswer> submittedAnswers = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

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
            "}";
    }
}
