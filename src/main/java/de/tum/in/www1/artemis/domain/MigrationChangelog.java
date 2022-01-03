package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "migration_changelog")
public class MigrationChangelog extends DomainObject {
    // ID documents the execution order

    @Column(name = "author")
    private String author;

    // documents when it was executed
    @Column(name = "date_executed")
    private ZonedDateTime dateExecuted;

    // document what was executed, hashed in some way if possible
    @Column(name = "date_string")
    private String dateString;

    // document with what system version it was executed
    @Column(name = "system_version")
    private String systemVersion;

    // document in what server startup period it was executed (same hash, same period). Could be simply datetime hashed
    @Column(name = "deployment_id")
    private String deploymentId;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public ZonedDateTime getDateExecuted() {
        return dateExecuted;
    }

    public void setDateExecuted(ZonedDateTime dateExecuted) {
        this.dateExecuted = dateExecuted;
    }

    public String getDateString() {
        return dateString;
    }

    public void setDateString(String dateString) {
        this.dateString = dateString;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }
}
