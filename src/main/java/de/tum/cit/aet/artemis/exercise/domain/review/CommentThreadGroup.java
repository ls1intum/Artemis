package de.tum.cit.aet.artemis.exercise.domain.review;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Entity
@Table(name = "comment_thread_group")
public class CommentThreadGroup extends DomainObject {

    @ManyToOne(optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private Set<CommentThread> threads = new HashSet<>();

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<CommentThread> getThreads() {
        return threads;
    }

    public void setThreads(Set<CommentThread> threads) {
        this.threads = threads;
    }
}
