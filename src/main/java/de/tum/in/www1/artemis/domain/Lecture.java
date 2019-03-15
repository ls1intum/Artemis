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
 * A Lecture.
 */
@Entity
@Table(name = "lecture")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Lecture implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @OneToMany(mappedBy = "lecture", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("lecture")
    private Set<Attachment> attachments = new HashSet<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("exercise")
    private Set<StudentQuestion> studentQuestions = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("lectures")
    private Course course;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Lecture startDate(ZonedDateTime startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public Lecture endDate(ZonedDateTime endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public Set<Attachment> getAttachments() {
        return attachments;
    }

    public Lecture attachments(Set<Attachment> attachments) {
        this.attachments = attachments;
        return this;
    }

    public Lecture addAttachments(Attachment attachment) {
        this.attachments.add(attachment);
        attachment.setLecture(this);
        return this;
    }

    public Lecture removeAttachments(Attachment attachment) {
        this.attachments.remove(attachment);
        attachment.setLecture(null);
        return this;
    }

    public void setAttachments(Set<Attachment> attachments) {
        this.attachments = attachments;
    }

    public Set<StudentQuestion> getStudentQuestions() {
        return studentQuestions;
    }

    public Lecture studentQuestions(Set<StudentQuestion> studentQuestions) {
        this.studentQuestions = studentQuestions;
        return this;
    }

    public Lecture addStudentQuestions(StudentQuestion studentQuestion) {
        this.studentQuestions.add(studentQuestion);
        studentQuestion.setLecture(this);
        return this;
    }

    public Lecture removeStudentQuestions(StudentQuestion studentQuestion) {
        this.studentQuestions.remove(studentQuestion);
        studentQuestion.setLecture(null);
        return this;
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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Lecture lecture = (Lecture) o;
        if (lecture.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), lecture.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Lecture{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", description='" + getDescription() + "'" +
            ", startDate='" + getStartDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            "}";
    }
}
