package de.tum.in.www1.artemis.domain.iris;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;

/**
 * An IrisSessionExerciseConnector is used if one student has multiple IrisSessions for one exercise
 */
@Entity
@Table(name = "iris_session_exercise_connector")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSessionExerciseConnector extends DomainObject {

    @OneToOne
    @JsonIgnore
    private IrisSession session;

    @OneToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate = ZonedDateTime.now();

    public IrisSession getSession() {
        return session;
    }

    public void setSession(IrisSession session) {
        this.session = session;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }
}
