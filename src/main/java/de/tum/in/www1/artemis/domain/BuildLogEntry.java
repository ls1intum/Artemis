package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by Josias Montag on 11.11.16.
 */
@Entity
@Table(name = "build_log_entry")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildLogEntry extends DomainObject {

    @Column(name = "time")
    private ZonedDateTime time;

    @Column(name = "log")
    private String log;

    @ManyToOne
    @JsonIgnore
    private ProgrammingSubmission programmingSubmission;

    public BuildLogEntry() {
        // Required for Hibernate
    }

    public BuildLogEntry(ZonedDateTime time, String log) {
        this.time = time;
        this.log = log;
    }

    public BuildLogEntry(ZonedDateTime time, String log, ProgrammingSubmission programmingSubmission) {
        this.time = time;
        this.log = log;
        this.programmingSubmission = programmingSubmission;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public ProgrammingSubmission getProgrammingSubmission() {
        return programmingSubmission;
    }

    public void setProgrammingSubmission(ProgrammingSubmission programmingSubmission) {
        this.programmingSubmission = programmingSubmission;
    }

    @Override
    public String toString() {
        return "BuildLogEntry{" + "time=" + time + ", log='" + log + '\'' + '}';
    }
}
