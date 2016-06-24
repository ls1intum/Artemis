package de.tum.in.www1.exerciseapp.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Participation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "clone_url")
    private String cloneUrl;

    @Column(name = "repository_slug")
    private String repositorySlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    private ParticipationState initializationState;

    @ManyToOne
    private User student;

    @OneToMany(mappedBy = "participation", cascade = CascadeType.REMOVE)
    @JsonIgnore
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Result> results = new HashSet<>();

    @ManyToOne
    private Exercise exercise;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public void setRepositorySlug(String repositorySlug) {
        this.repositorySlug = repositorySlug;
    }

    public ParticipationState getInitializationState() {
        return initializationState;
    }

    public void setInitializationState(ParticipationState initializationState) {
        this.initializationState = initializationState;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User user) {
        this.student = user;
    }

    public Set<Result> getResults() {
        return results;
    }

    public void setResults(Set<Result> results) {
        this.results = results;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Participation participation = (Participation) o;
        if(participation.id == null || id == null) {
            return false;
        }
        return Objects.equals(id, participation.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Participation{" +
            "id=" + id +
            ", cloneUrl='" + cloneUrl + "'" +
            ", repositorySlug='" + repositorySlug + "'" +
            ", initializationState='" + initializationState + "'" +
            '}';
    }
}
