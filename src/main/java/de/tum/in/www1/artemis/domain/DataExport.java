package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.DataExportState;

/**
 * A data export for user data
 **/
@Entity
@Table(name = "data_export")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DataExport extends DomainObject {

    @Enumerated(EnumType.ORDINAL)
    private DataExportState dataExportState;

    @Column(name = "request_date")
    private ZonedDateTime requestDate;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate;

    @Column(name = "download_date")
    private ZonedDateTime downloadDate;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private User user;

    @Column(name = "file_path")
    private String filePath;

    public ZonedDateTime getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(ZonedDateTime requestDate) {
        this.requestDate = requestDate;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
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
