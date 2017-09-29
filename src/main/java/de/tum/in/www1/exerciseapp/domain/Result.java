package de.tum.in.www1.exerciseapp.domain;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A Result.
 */
@Entity
@Table(name = "result")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Result implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "result_string")
    private String resultString;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Column(name = "build_successful")
    private Boolean buildSuccessful;

    @ManyToOne
    private Participation participation;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResultString() {
        return resultString;
    }

    public void setResultString(String resultString) {
        this.resultString = resultString;
    }

    public ZonedDateTime getBuildCompletionDate() {
        return buildCompletionDate;
    }

    public void setBuildCompletionDate(ZonedDateTime buildCompletionDate) {
        this.buildCompletionDate = buildCompletionDate;
    }

    public Boolean isBuildSuccessful() {
        return buildSuccessful;
    }

    public void setBuildSuccessful(Boolean buildSuccessful) {
        this.buildSuccessful = buildSuccessful;
    }

    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
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
        Result result = (Result) o;
        if (result.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), result.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "Result{" +
            "id=" + getId() +
            ", resultString='" + getResultString() + "'" +
            ", buildCompletionDate='" + getBuildCompletionDate() + "'" +
            ", buildSuccessful='" + isBuildSuccessful() + "'" +
            "}";
    }
}
