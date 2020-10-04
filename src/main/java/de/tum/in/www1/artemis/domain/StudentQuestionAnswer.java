package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A StudentQuestionAnswer.
 */
@Entity
@Table(name = "student_question_answer")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentQuestionAnswer extends DomainObject {

    @Lob
    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "answer_date")
    private ZonedDateTime answerDate;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "tutor_approved")
    private Boolean tutorApproved;

    @ManyToOne
    @JsonIgnoreProperties("questionAnswers")
    private User author;

    @ManyToOne
    @JsonIgnoreProperties("answers")
    private StudentQuestion question;

    public String getAnswerText() {
        return answerText;
    }

    public StudentQuestionAnswer answerText(String answerText) {
        this.answerText = answerText;
        return this;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public ZonedDateTime getAnswerDate() {
        return answerDate;
    }

    public StudentQuestionAnswer answerDate(ZonedDateTime answerDate) {
        this.answerDate = answerDate;
        return this;
    }

    public void setAnswerDate(ZonedDateTime answerDate) {
        this.answerDate = answerDate;
    }

    public Boolean isVerified() {
        return verified;
    }

    public StudentQuestionAnswer verified(Boolean verified) {
        this.verified = verified;
        return this;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean isTutorApproved() {
        return tutorApproved;
    }

    public StudentQuestionAnswer tutorApproved(Boolean tutorApproved) {
        this.tutorApproved = tutorApproved;
        return this;
    }

    public void setTutorApproved(Boolean tutorApproved) {
        this.tutorApproved = tutorApproved;
    }

    public User getAuthor() {
        return author;
    }

    public StudentQuestionAnswer author(User user) {
        this.author = user;
        return this;
    }

    public void setAuthor(User user) {
        this.author = user;
    }

    public StudentQuestion getQuestion() {
        return question;
    }

    public StudentQuestionAnswer question(StudentQuestion studentQuestion) {
        this.question = studentQuestion;
        return this;
    }

    public void setQuestion(StudentQuestion studentQuestion) {
        this.question = studentQuestion;
    }

    @Override
    public String toString() {
        return "StudentQuestionAnswer{" + "id=" + getId() + ", answerText='" + getAnswerText() + "'" + ", answerDate='" + getAnswerDate() + "'" + ", verified='" + isVerified()
                + "'" + ", tutorApproved='" + isTutorApproved() + "'" + "}";
    }
}
