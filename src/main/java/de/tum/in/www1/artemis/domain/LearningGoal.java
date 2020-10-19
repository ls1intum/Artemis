package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.lecture_module.LectureModule;

/**
 * Entity that represents a learning goal students should try to achieve
 */
@Entity
@Table(name = "learning_goal")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LearningGoal extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    @Lob
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToMany
    @JoinTable(name = "learning_goal_exercise", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "exercise_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    private Set<Exercise> exercises = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "learning_goal_lecture_module", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "lecture_module_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    private Set<LectureModule> lectureModules = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Course getCourse() {
        return course;
    }

    /**
     * Sets the course property
     *
     * @param course entity to set the property to
     */
    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<LectureModule> getLectureModules() {
        return lectureModules;
    }

    public void setLectureModules(Set<LectureModule> lectureModules) {
        this.lectureModules = lectureModules;
    }

    /**
     * Adds an lecture module to the learning goal. Also handles the other side of the relationship
     *
     * @param lectureModule the lecture module to add
     * @return learning goal with lecture module added
     */
    public LearningGoal addLectureModule(LectureModule lectureModule) {
        this.lectureModules.add(lectureModule);
        if (!lectureModule.getLearningGoals().contains(this)) {
            lectureModule.getLearningGoals().add(this);
        }
        return this;
    }

    /**
     * Removes a lecture module from the learning goal. Also handles the other side of the relationship.
     *
     * @param lectureModule the lectureModule to remove
     * @return learning goal with lectureModule removed
     */
    public LearningGoal removeLectureModule(LectureModule lectureModule) {
        this.lectureModules.remove(lectureModule);
        lectureModule.getLearningGoals().remove(this);
        return this;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    /**
     * Adds an exercise to the learning goal. Also handles the other side of the relationship
     *
     * @param exercise the exercise to add
     * @return learning goal with exercise added
     */
    public LearningGoal addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        if (!exercise.getLearningGoals().contains(this)) {
            exercise.getLearningGoals().add(this);
        }
        return this;
    }

    /**
     * Removes an exercise from the learning goal. Also handles the other side of the relationship.
     *
     * @param exercise exercise to remove from the learning goal
     * @return learning goal with exercise removed
     */
    public LearningGoal removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getLearningGoals().remove(this);
        return this;
    }

}
