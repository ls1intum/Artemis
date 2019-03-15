package de.tum.in.www1.artemis.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A QuestionAnswer.
 */
@Entity
@Table(name = "question_answer")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class QuestionAnswer implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "answer_date")
    private ZonedDateTime answerDate;

    @Column(name = "verified")
    private Boolean verified;

    @ManyToOne
    @JsonIgnoreProperties("questionAnswers")
    private User author;

    @ManyToOne
    @JsonIgnoreProperties("answers")
    private StudentQuestion question;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnswerText() {
        return answerText;
    }

    public QuestionAnswer answerText(String answerText) {
        this.answerText = answerText;
        return this;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public ZonedDateTime getAnswerDate() {
        return answerDate;
    }

    public QuestionAnswer answerDate(ZonedDateTime answerDate) {
        this.answerDate = answerDate;
        return this;
    }

    public void setAnswerDate(ZonedDateTime answerDate) {
        this.answerDate = answerDate;
    }

    public Boolean isVerified() {
        return verified;
    }

    public QuestionAnswer verified(Boolean verified) {
        this.verified = verified;
        return this;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }


    public User getAuthor() {
        return author;
    }

    public QuestionAnswer author(User user) {
        this.author = user;
        return this;
    }

    public void setAuthor(User user) {
        this.author = user;
    }

    public StudentQuestion getQuestion() {
        return question;
    }

    public QuestionAnswer question(StudentQuestion studentQuestion) {
        this.question = studentQuestion;
        return this;
    }

    public void setQuestion(StudentQuestion studentQuestion) {
        this.question = studentQuestion;
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
        QuestionAnswer questionAnswer = (QuestionAnswer) o;
        if (questionAnswer.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), questionAnswer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "QuestionAnswer{" +
            "id=" + getId() +
            ", answerText='" + getAnswerText() + "'" +
            ", answerDate='" + getAnswerDate() + "'" +
            ", verified='" + isVerified() + "'" +
            "}";
    }
}
