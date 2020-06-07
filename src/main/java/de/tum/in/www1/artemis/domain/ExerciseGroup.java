package de.tum.in.www1.artemis.domain;

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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table(name = "exercise_group")
public class ExerciseGroup {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------
    // no arg constructor required for jpa
    public ExerciseGroup() {
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region BASIC PROPERTIES
    // -----------------------------------------------------------------------------------------------------------------
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    /**
     * Mandatory exercise groups need to be included in the exam
     */
    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory = true;

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
    @OneToMany
    @JoinColumn(name = "exercise_group_id")
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getIsMandatory() {
        return isMandatory;
    }

    public void setIsMandatory(Boolean isMandatory) {
        this.isMandatory = isMandatory;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion

    // region EQUALS AND HASHCODE
    // -----------------------------------------------------------------------------------------------------------------
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExerciseGroup that = (ExerciseGroup) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion
}
