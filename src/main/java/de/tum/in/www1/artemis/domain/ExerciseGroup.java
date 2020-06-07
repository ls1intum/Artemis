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

import de.tum.in.www1.artemis.domain.exam.Exam;

@Entity
@Table(name = "exercise_group")
public class ExerciseGroup extends AbstractAuditingEntity {

    // region CONSTRUCTORS
    // -----------------------------------------------------------------------------------------------------------------
    // no arg constructor required for jpa
    public ExerciseGroup() {
    }

    public ExerciseGroup(Long id, String title, Boolean isMandatory, Integer positionInExam, Exam exam, Set<Exercise> exercises) {
        this.id = id;
        this.title = title;
        this.isMandatory = isMandatory;
        this.positionInExam = positionInExam;
        this.exam = exam;
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

    @Column(name = "title")
    private String title;

    /**
     * Mandatory exercise groups need to be included in the exam
     */
    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory = true;

    /**
     * Specifies the position of this exercise group if it is used in an exam
     */
    @Column(name = "position_in_exam", nullable = false)
    private Integer positionInExam = 1;

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
            this.exam.removeExerciseGroup(this);
        }
        this.exam = exam;
        if (!exam.getExerciseGroups().contains(this)) {
            exam.getExerciseGroups().add(this);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @OneToMany(mappedBy = "exerciseGroup")
    private Set<Exercise> exercises = new HashSet<>();

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        if (exercise.getExerciseGroup() != this) {
            exercise.setExerciseGroup(this);
        }
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        if (exercise.getExerciseGroup() == this) {
            exercise.setExerciseGroup(null);
        }
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
        isMandatory = isMandatory;
    }

    public Integer getPositionInExam() {
        return positionInExam;
    }

    public void setPositionInExam(Integer positionInExam) {
        this.positionInExam = positionInExam;
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
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // endregion
}
