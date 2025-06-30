package de.tum.cit.aet.artemis.exercise.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "exercise_version")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@DiscriminatorValue(value = "E")
public class ExerciseVersion extends AbstractAuditingEntity {

    @ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    /**
     * This field contains a JSON representation of the fields in Exercise.
     * The content of this field is used to create a JSON representation of the Exercise object.
     * The content of this field is used to display the history of changes made to the exercise.
     * The content of this field is used to compare the current state of the exercise with the previous state.
     */
    @Column(name = "content")
    private String content;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
