package de.tum.in.www1.artemis.domain.exam;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import de.tum.in.www1.artemis.domain.AbstractAuditingEntity;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "student_exam")
public class StudentExam extends AbstractAuditingEntity {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------

    // no arg constructor required for jpa
    public StudentExam() {
    }

    public StudentExam(Long id, Exam exam, User user, Set<Exercise> exercises) {
        this.id = id;
        this.exam = exam;
        this.user = user;
        this.exercises = exercises;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region BASIC PROPERTIES
    // -----------------------------------------------------------------------------------------------------------------

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region RELATIONSHIPS
    // -----------------------------------------------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        if (this.exam != null) {
            this.exam.removeStudentExam(this);
        }

        this.exam = exam;
        if (!exam.getStudentExams().contains(this)) {
            exam.getStudentExams().add(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        if (this.exam != null) {
            this.exam.removeStudentExam(this);
        }

        this.exam = exam;
        if (!exam.getStudentExams().contains(this)) {
            exam.getStudentExams().add(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ManyToMany
    @JoinTable(name = "student_exam_exercise", joinColumns = @JoinColumn(name = "student_exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    private Set<Exercise> exercises = new HashSet<>();

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
    }
    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region SIMPLE GETTERS AND SETTERS
    // -----------------------------------------------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // endregion
    // -----------------------------------------------------------------------------------------------------------------

    // region HASHCODE AND EQUAL
    // -----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StudentExam that = (StudentExam) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
    // endregion
    // -----------------------------------------------------------------------------------------------------------------
}
