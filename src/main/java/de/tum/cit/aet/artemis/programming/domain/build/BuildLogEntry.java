package de.tum.cit.aet.artemis.programming.domain.build;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Created by Josias Montag on 11.11.16.
 */
@Entity
@Table(name = "build_log_entry")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildLogEntry extends DomainObject {

    // Maximum characters for the attribute log as defined in the database model
    private static final int MAX_LOG_LENGTH = 255;

    @Column(name = "time")
    private ZonedDateTime time;

    @Column(name = "log")
    private String log;

    // The name of the build container that produced this log line, or null for a submission built by a single container.
    // It lets the build logs of a multi-container build be attributed to the container they came from.
    @Column(name = "container_name")
    private String containerName;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(nullable = false)
    private ProgrammingSubmission programmingSubmission;

    public BuildLogEntry(ZonedDateTime time, String log) {
        this.time = time;
        this.log = log;
    }

    public BuildLogEntry(ZonedDateTime time, String log, ProgrammingSubmission programmingSubmission) {
        this.time = time;
        this.log = log;
        this.programmingSubmission = programmingSubmission;
    }

    public BuildLogEntry() {
        // added for Hibernate and Jackson, because we have custom constructors
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

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public ProgrammingSubmission getProgrammingSubmission() {
        return programmingSubmission;
    }

    public void setProgrammingSubmission(ProgrammingSubmission programmingSubmission) {
        this.programmingSubmission = programmingSubmission;
    }

    /**
     * Truncates the log to the maximum size allowed by the database model
     */
    public void truncateLogToMaxLength() {
        if (log != null && log.length() > MAX_LOG_LENGTH) {
            log = log.substring(0, MAX_LOG_LENGTH);
        }
    }

    @Override
    public String toString() {
        return "BuildLogEntry{" + "time=" + time + ", log='" + log + '\'' + '}';
    }
}
