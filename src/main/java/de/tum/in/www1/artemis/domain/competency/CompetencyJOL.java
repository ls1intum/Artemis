package de.tum.in.www1.artemis.domain.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "competency_jol")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class CompetencyJOL extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "competency_id")
    @JsonIgnoreProperties({ "competencyJOLs" })
    private Competency competency;

    @ManyToOne
    private User user;

    @Column(name = "value")
    private Double value;

    public Competency getCompetency() {
        return this.competency;
    }

    public void setCompetency(Competency competency) {
        this.competency = competency;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Double getValue() {
        return this.value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
