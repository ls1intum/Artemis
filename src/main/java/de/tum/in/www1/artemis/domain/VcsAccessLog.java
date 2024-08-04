package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.participation.Participation;

/**
 * A Result.
 */
@Entity
@Table(name = "vcs_access_log")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class VcsAccessLog extends DomainObject {

    @ManyToOne
    private User student;

    @ManyToOne
    private Participation participation;

    @Column(name = "operation")
    private String operation;

    @Column(name = "mechanism")
    private String mechanism;

    @Column(name = "ipAddress")
    private String ipAddress;

    @Column(name = "timestamp")
    private ZonedDateTime timestamp;

    public VcsAccessLog(User user, Participation participation, String operation, String mechanism, String ipAddress) {
        this.student = user;
        this.participation = (Participation) participation;
        this.operation = operation;
        this.mechanism = mechanism;
        this.ipAddress = ipAddress;
    }

    public VcsAccessLog() {
    }
}
