package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Lecture.
 */
@Entity
@Table(name = "lecture")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Lecture extends DomainObject {

    @Column(name = "title")
    private String title;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonIgnoreProperties(value = "lecture", allowSetters = true)
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("lecture")
    private Set<StudentQuestion> studentQuestions = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("lectures")
    private Course course;

    @ManyToMany(mappedBy = "lectures")
    @JsonIgnoreProperties("lectures")
    public Set<LearningGoal> learningGoals = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public Lecture title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public Lecture description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public Lecture addAttachments(Attachment attachment) {
        this.attachments.add(attachment);
        attachment.setLecture(this);
        return this;
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Set<StudentQuestion> getStudentQuestions() {
        return studentQuestions;
    }

    public void setStudentQuestions(Set<StudentQuestion> studentQuestions) {
        this.studentQuestions = studentQuestions;
    }

    public Course getCourse() {
        return course;
    }

    public Lecture course(Course course) {
        this.course = course;
        return this;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<LearningGoal> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        this.learningGoals = learningGoals;
    }

    public Lecture addLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.add(learningGoal);
        if (!learningGoal.getLectures().contains(this)) {
            learningGoal.getLectures().add(this);
        }
        return this;
    }

    public Lecture removeLearningGoal(LearningGoal learningGoal) {
        this.learningGoals.remove(learningGoal);
        if (learningGoal.getLectures().contains(this)) {
            learningGoal.getLectures().remove(this);
        }
        return this;
    }

    @Override
    public String toString() {
        return "Lecture{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", description='" + getDescription() + "'" + ", startDate='" + getStartDate() + "'" + ", endDate='"
                + getEndDate() + "'" + "}";
    }
}
