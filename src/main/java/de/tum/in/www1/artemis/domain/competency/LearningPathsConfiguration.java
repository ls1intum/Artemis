package de.tum.in.www1.artemis.domain.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "learning_paths_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LearningPathsConfiguration extends DomainObject {

    @OneToOne(mappedBy = "learningPathsConfiguration")
    @JsonIgnoreProperties(value = "learningPathsConfiguration", allowSetters = true)
    private Course course;

    /**
     * Note: String to prevent Hibernate from converting it to UTC
     */
    @Column(name = "include_all_graded_exercises")
    private boolean includeAllGradedExercises;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean getIncludeAllGradedExercises() {
        return includeAllGradedExercises;
    }

    public void setIncludeAllGradedExercises(boolean includeAllGradedExercises) {
        this.includeAllGradedExercises = includeAllGradedExercises;
    }
}
