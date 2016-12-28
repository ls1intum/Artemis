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
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "result_string")
    private String resultString;

    @Column(name = "build_completion_date")
    private ZonedDateTime buildCompletionDate;

    @Column(name = "build_successful")
    private Boolean buildSuccessful;

    @Column(name = "score")
    private Long score;

    @ManyToOne
    private Participation participation;

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

    public Long getScore() {
        return score;
    }

    public void setScore(Long score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Result result = (Result) o;
        if(result.id == null || id == null) {
            return false;
        }
        return Objects.equals(id, result.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Result{" +
            "id=" + id +
            ", resultString='" + resultString + "'" +
            ", buildCompletionDate='" + buildCompletionDate + "'" +
            ", buildSuccessful='" + buildSuccessful + "'" +
            ", score='" + score + "'" +
            '}';
    }
}
