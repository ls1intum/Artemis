package de.tum.cit.aet.artemis.versioning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.versioning.dto.ExerciseSnapshot;

@Entity
@Table(name = "exercise_version")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ExerciseVersion extends AbstractAuditingEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", updatable = false)
    private Exercise exercise;

    @ManyToOne
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exercise_snapshot", columnDefinition = "json")
    private ExerciseSnapshot exerciseSnapshot;

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

    public ExerciseSnapshot getExerciseSnapshot() {
        return exerciseSnapshot;
    }

    public void setExerciseSnapshot(ExerciseSnapshot exerciseSnapshot) {
        this.exerciseSnapshot = exerciseSnapshot;
    }
}
