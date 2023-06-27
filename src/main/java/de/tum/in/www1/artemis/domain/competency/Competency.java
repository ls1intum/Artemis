package de.tum.in.www1.artemis.domain.competency;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@Entity
@Table(name = "learning_goal")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Competency extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "mastery_threshold")
    private Integer masteryThreshold;

    /**
     * The type of competency according to Bloom's revised taxonomy.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Bloom%27s_taxonomy">Wikipedia</a>
     */
    @Column(name = "taxonomy")
    @Convert(converter = CompetencyTaxonomy.TaxonomyConverter.class)
    @JsonInclude
    private CompetencyTaxonomy taxonomy;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<Exercise> exercises = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties("competencies")
    private Set<LectureUnit> lectureUnits = new HashSet<>();

    /**
     * A set of courses for which this competency is a prerequisite for.
     */
    @ManyToMany
    @JoinTable(name = "learning_goal_course", joinColumns = @JoinColumn(name = "learning_goal_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "course_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Course> consecutiveCourses = new HashSet<>();

    @OneToMany(mappedBy = "learningGoal", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties({ "user", "competency" })
    private Set<CompetencyProgress> userProgress = new HashSet<>();

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

    public int getMasteryThreshold() {
        return masteryThreshold == null ? 100 : this.masteryThreshold;
    }

    public void setMasteryThreshold(Integer masteryThreshold) {
        this.masteryThreshold = masteryThreshold;
    }

    public CompetencyTaxonomy getTaxonomy() {
        return taxonomy;
    }

    public void setTaxonomy(CompetencyTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.getCompetencies().add(this);
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getCompetencies().remove(this);
    }

    public Set<LectureUnit> getLectureUnits() {
        return lectureUnits;
    }

    public void setLectureUnits(Set<LectureUnit> lectureUnits) {
        this.lectureUnits = lectureUnits;
    }

    /**
     * Adds the lecture unit to the competency (bidirectional)
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise (see {@link #addExercise(Exercise)})
     *
     * @param lectureUnit The lecture unit to add
     */
    public void addLectureUnit(LectureUnit lectureUnit) {
        if (lectureUnit instanceof ExerciseUnit) {
            // The competencies of ExerciseUnits are taken from the corresponding exercise
            throw new IllegalArgumentException("ExerciseUnits can not be connected to competencies");
        }
        this.lectureUnits.add(lectureUnit);
        lectureUnit.getCompetencies().add(this);
    }

    /**
     * Removes the lecture unit from the competency (bidirectional)
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise (see {@link #removeExercise(Exercise)})
     *
     * @param lectureUnit The lecture unit to remove
     */
    public void removeLectureUnit(LectureUnit lectureUnit) {
        if (lectureUnit instanceof ExerciseUnit) {
            // The competencies of ExerciseUnits are taken from the corresponding exercise
            throw new IllegalArgumentException("ExerciseUnits can not be disconnected from competencies");
        }
        this.lectureUnits.remove(lectureUnit);
        lectureUnit.getCompetencies().remove(this);
    }

    public Set<Course> getConsecutiveCourses() {
        return consecutiveCourses;
    }

    public void setConsecutiveCourses(Set<Course> consecutiveCourses) {
        this.consecutiveCourses = consecutiveCourses;
    }

    public Set<CompetencyProgress> getUserProgress() {
        return userProgress;
    }

    public void setUserProgress(Set<CompetencyProgress> userProgress) {
        this.userProgress = userProgress;
    }

    /**
     * Ensure that exercise units are connected to competencies through the corresponding exercise
     */
    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.lectureUnits.removeIf(lectureUnit -> lectureUnit instanceof ExerciseUnit);
    }

    public enum CompetencySearchColumn {

        ID("id"), TITLE("title"), COURSE_TITLE("course.title"), SEMESTER("course.semester");

        private final String mappedColumnName;

        CompetencySearchColumn(String mappedColumnName) {
            this.mappedColumnName = mappedColumnName;
        }

        public String getMappedColumnName() {
            return mappedColumnName;
        }
    }
}
