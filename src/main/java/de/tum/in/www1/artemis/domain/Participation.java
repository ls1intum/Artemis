package de.tum.in.www1.artemis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;

/**
 * A Participation.
 */
@Entity
@Table(name = "participation")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Participation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "build_plan_id")
    private String buildPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "initialization_state")
    private ParticipationState initializationState;

    @Column(name = "initialization_date")
    private ZonedDateTime initializationDate;

    @OneToMany(mappedBy = "participation")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Result> results = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("")
    private User student;

    @ManyToOne
    @JsonIgnoreProperties("participations")
    private Exercise exercise;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public Participation repositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
        return this;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getBuildPlanId() {
        return buildPlanId;
    }

    public Participation buildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
        return this;
    }

    public void setBuildPlanId(String buildPlanId) {
        this.buildPlanId = buildPlanId;
    }

    public ParticipationState getInitializationState() {
        return initializationState;
    }

    public Participation initializationState(ParticipationState initializationState) {
        this.initializationState = initializationState;
        return this;
    }

    public void setInitializationState(ParticipationState initializationState) {
        this.initializationState = initializationState;
    }

    public ZonedDateTime getInitializationDate() {
        return initializationDate;
    }

    public Participation initializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
        return this;
    }

    public void setInitializationDate(ZonedDateTime initializationDate) {
        this.initializationDate = initializationDate;
    }

    public Set<Result> getResults() {
        return results;
    }

    public Participation results(Set<Result> results) {
        this.results = results;
        return this;
    }

    public Participation addResults(Result result) {
        this.results.add(result);
        result.setParticipation(this);
        return this;
    }

    public Participation removeResults(Result result) {
        this.results.remove(result);
        result.setParticipation(null);
        return this;
    }

    public void setResults(Set<Result> results) {
        this.results = results;
    }

    public User getStudent() {
        return student;
    }

    public Participation student(User user) {
        this.student = user;
        return this;
    }

    public void setStudent(User user) {
        this.student = user;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public Participation exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Participation participation = (Participation) o;
        if (participation.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), participation.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Participation{" +
            "id=" + getId() +
            ", repositoryUrl='" + getRepositoryUrl() + "'" +
            ", buildPlanId='" + getBuildPlanId() + "'" +
            ", initializationState='" + getInitializationState() + "'" +
            ", initializationDate='" + getInitializationDate() + "'" +
            "}";
    }
}
