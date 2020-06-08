package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

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

@Entity
@Table(name = "student_exam")
public class StudentExam {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------

    // no arg constructor required for jpa
    public StudentExam() {
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
        this.exam = exam;
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // -----------------------------------------------------------------------------------------------------------------
    @ManyToMany
    @JoinTable(name = "student_exam_exercise", joinColumns = @JoinColumn(name = "student_exam_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    private List<Exercise> exercises = new HashSet<>();

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
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
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
    // endregion
    // -----------------------------------------------------------------------------------------------------------------
}
