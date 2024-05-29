package de.tum.in.www1.artemis.domain.competency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * An entity to store a students Judgement of Learning (JOL) value for a competency.
 * <p>
 * A JOL value is a students self-assessment of their own learning progress for a competency with value 1 to 5
 */
@Entity
@Table(name = "competency_jol")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class CompetencyJol extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "competency_id")
    private Competency competency;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "jol_value")
    private Integer value;

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

    public Integer getValue() {
        return this.value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
