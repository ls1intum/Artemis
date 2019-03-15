package de.tum.in.www1.artemis.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A StudentQuestion.
 */
@Entity
@Table(name = "student_question")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class StudentQuestion implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_text")
    private String questionText;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate;

    @Column(name = "visible_for_students")
    private Boolean visibleForStudents;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<QuestionAnswer> answers = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private User author;

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private Lecture lecture;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public StudentQuestion questionText(String questionText) {
        this.questionText = questionText;
        return this;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public StudentQuestion creationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public Boolean isVisibleForStudents() {
        return visibleForStudents;
    }

    public StudentQuestion visibleForStudents(Boolean visibleForStudents) {
        this.visibleForStudents = visibleForStudents;
        return this;
    }

    public void setVisibleForStudents(Boolean visibleForStudents) {
        this.visibleForStudents = visibleForStudents;
    }

    public Set<QuestionAnswer> getAnswers() {
        return answers;
    }

    public StudentQuestion answers(Set<QuestionAnswer> questionAnswers) {
        this.answers = questionAnswers;
        return this;
    }

    public StudentQuestion addAnswers(QuestionAnswer questionAnswer) {
        this.answers.add(questionAnswer);
        questionAnswer.setQuestion(this);
        return this;
    }

    public StudentQuestion removeAnswers(QuestionAnswer questionAnswer) {
        this.answers.remove(questionAnswer);
        questionAnswer.setQuestion(null);
        return this;
    }

    public void setAnswers(Set<QuestionAnswer> questionAnswers) {
        this.answers = questionAnswers;
    }

    public User getAuthor() {
        return author;
    }

    public StudentQuestion author(User user) {
        this.author = user;
        return this;
    }

    public void setAuthor(User user) {
        this.author = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public StudentQuestion exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public StudentQuestion lecture(Lecture lecture) {
        this.lecture = lecture;
        return this;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
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
        StudentQuestion studentQuestion = (StudentQuestion) o;
        if (studentQuestion.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), studentQuestion.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "StudentQuestion{" +
            "id=" + getId() +
            ", questionText='" + getQuestionText() + "'" +
            ", creationDate='" + getCreationDate() + "'" +
            ", visibleForStudents='" + isVisibleForStudents() + "'" +
            "}";
    }
}
