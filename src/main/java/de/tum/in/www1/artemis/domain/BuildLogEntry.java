package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Objects;

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
public class BuildLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        BuildLogEntry that = (BuildLogEntry) object;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        else if (time != null && that.time != null) {
            return Objects.equals(time.toInstant(), that.time.toInstant()) && Objects.equals(log, that.log);
        }
        else if (time == null && that.time == null) {
            return Objects.equals(log, that.log);
        }
        else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
