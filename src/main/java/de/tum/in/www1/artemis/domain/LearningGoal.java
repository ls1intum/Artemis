package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entity that represents a learning goal students should try to achieve
 */
@Entity
@Table(name = "learning_goal")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
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
    @JoinTable(name = "learning_goal_lecture", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "lecture_id", referencedColumnName = "id"))
    @JsonIgnoreProperties("learningGoals")
    private Set<Lecture> lectures = new HashSet<>();

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

    public Set<Lecture> getLectures() {
        return lectures;
    }

    public void setLectures(Set<Lecture> lectures) {
        this.lectures = lectures;
    }

    /**
     * Adds an lecture to the learning goal. Also handles the other side of the relationship
     *
     * @param lecture the lecture to add
     * @return learning goal with lecture added
     */
    public LearningGoal addLecture(Lecture lecture) {
        this.lectures.add(lecture);
        if (!lecture.getLearningGoals().contains(this)) {
            lecture.getLearningGoals().add(this);
        }
        return this;
    }

    /**
     * Removes a lecture from the learning goal. Also handles the other side of the relationship.
     *
     * @param lecture the lecture to remove
     * @return learning goal with lecture removed
     */
    public LearningGoal removeLecture(Lecture lecture) {
        this.lectures.remove(lecture);
        lecture.getLearningGoals().remove(this);
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
     * @param exercise the exercise to remove from the learning goal
     * @return learning goal with exercise removed
     */
    public LearningGoal removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getLearningGoals().remove(this);
        return this;
    }

}
