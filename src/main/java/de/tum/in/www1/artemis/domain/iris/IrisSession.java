package de.tum.in.www1.artemis.domain.iris;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "iris_session")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSession extends DomainObject {

    @ManyToOne
    @JsonIgnore
    private ProgrammingExercise exercise;

    @ManyToOne
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
    private List<IrisMessage> messages = new ArrayList<>();

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<IrisMessage> getMessages() {
        return messages;
    }
}
