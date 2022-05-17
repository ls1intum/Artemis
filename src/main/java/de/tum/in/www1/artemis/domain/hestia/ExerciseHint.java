package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An ExerciseHint.
 */
@Entity
@Table(name = "exercise_hint")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("T")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ExerciseHint.class, name = "text"), @JsonSubTypes.Type(value = CodeHint.class, name = "code") })
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseHint extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private ProgrammingExerciseTask task;

    public String getTitle() {
        return title;
    }

    public ExerciseHint title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public ExerciseHint content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public ExerciseHint exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public ProgrammingExerciseTask getProgrammingExerciseTask() {
        return task;
    }

    public void setProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public void removeContent() {
        this.content = null;
        this.title = null;
    }

    @Override
    public String toString() {
        return "ExerciseHint{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", content='" + getContent() + "'" + "}";
    }
}
