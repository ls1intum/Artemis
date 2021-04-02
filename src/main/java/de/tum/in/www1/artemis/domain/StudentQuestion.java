package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A StudentQuestion.
 */
@Entity
@Table(name = "student_question")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentQuestion extends DomainObject {

    @Size(max = 1000)
    @Column(name = "question_text", length = 1000)
    private String questionText;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate;

    @Column(name = "visible_for_students")
    private Boolean visibleForStudents;

    @Column(name = "votes", columnDefinition = "integer default 0")
    private Integer votes = 0;

    @OneToMany(mappedBy = "question", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<StudentQuestionAnswer> answers = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private User author;

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("studentQuestions")
    private Lecture lecture;

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public Boolean isVisibleForStudents() {
        return visibleForStudents;
    }

    public void setVisibleForStudents(Boolean visibleForStudents) {
        this.visibleForStudents = visibleForStudents;
    }

    public Integer getVotes() {
        return votes;
    }

    public void setVotes(Integer votes) {
        this.votes = votes;
    }

    public Set<StudentQuestionAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<StudentQuestionAnswer> studentQuestionAnswers) {
        this.answers = studentQuestionAnswers;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User user) {
        this.author = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    /**
     * Convenience method to retrieve the relevant course from linked Lecture or Exercise.
     * @return related Course object
     */
    public Course getCourse() {
        if (getLecture() != null) {
            return getLecture().getCourse();
        }
        else if (getExercise() != null) {
            return getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        return null;
    }

    @Override
    public String toString() {
        return "StudentQuestion{" + "id=" + getId() + ", questionText='" + getQuestionText() + "'" + ", creationDate='" + getCreationDate() + "'" + ", visibleForStudents='"
                + isVisibleForStudents() + "'" + ", votes='" + getVotes() + "'" + "}";
    }
}
