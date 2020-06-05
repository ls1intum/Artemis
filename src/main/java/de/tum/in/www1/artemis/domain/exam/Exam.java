package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExerciseGroup;

@Entity
@Table(name = "exam")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Course course;

    @OneToMany(mappedBy = "exam", fetch = FetchType.LAZY)
    private Set<StudentExam> studentExams;

    @OneToMany(mappedBy = "exam", fetch = FetchType.EAGER)
    private Set<ExerciseGroup> exerciseGroups;

    @Column(name = "release date")
    private ZonedDateTime releaseDate;

    @Column(name = "due date")
    private ZonedDateTime dueDate;

    @Column(name = "start_text")
    private String startText;

    @Column(name = "end_text")
    private String endText;

    @Column(name = "confirmation_start_text")
    private String confirmationStartText;

    @Column(name = "confirmation_end_text")
    private String confirmationEndText;

    @Column(name = "max_points")
    private Long maxPoints;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<StudentExam> getStudentExams() {
        return studentExams;
    }

    public void setStudentExams(Set<StudentExam> studentExams) {
        this.studentExams = studentExams;
    }

    public Set<ExerciseGroup> getExerciseGroups() {
        return exerciseGroups;
    }

    public void setExerciseGroups(Set<ExerciseGroup> exerciseGroups) {
        this.exerciseGroups = exerciseGroups;
    }

    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public String getStartText() {
        return startText;
    }

    public void setStartText(String startText) {
        this.startText = startText;
    }

    public String getEndText() {
        return endText;
    }

    public void setEndText(String endText) {
        this.endText = endText;
    }

    public String getConfirmationStartText() {
        return confirmationStartText;
    }

    public void setConfirmationStartText(String confirmationStartText) {
        this.confirmationStartText = confirmationStartText;
    }

    public String getConfirmationEndText() {
        return confirmationEndText;
    }

    public void setConfirmationEndText(String confirmationEndText) {
        this.confirmationEndText = confirmationEndText;
    }

    public Long getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Long maxPoints) {
        this.maxPoints = maxPoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Exam exam = (Exam) o;
        return Objects.equals(id, exam.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
