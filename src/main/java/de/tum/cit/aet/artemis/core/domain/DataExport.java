package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A data export for user data
 * We use the creation_date of the AbstractAuditingEntity as the date when the export was requested
 **/
@Entity
@Table(name = "data_export")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DataExport extends AbstractAuditingEntity {

    @Enumerated(EnumType.ORDINAL)
    private DataExportState dataExportState;

    @Column(name = "creation_finished_date")
    private ZonedDateTime creationFinishedDate;

    @Column(name = "download_date")
    private ZonedDateTime downloadDate;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "file_path")
    private String filePath;

    public ZonedDateTime getCreationFinishedDate() {
        return creationFinishedDate;
    }

    public void setCreationFinishedDate(ZonedDateTime creationDate) {
        this.creationFinishedDate = creationDate;
    }

    public ZonedDateTime getDownloadDate() {
        return downloadDate;
    }

    public void setDownloadDate(ZonedDateTime downloadDate) {
        this.downloadDate = downloadDate;
    }

    public DataExportState getDataExportState() {
        return dataExportState;
    }

    public void setDataExportState(DataExportState dataExportState) {
        this.dataExportState = dataExportState;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
