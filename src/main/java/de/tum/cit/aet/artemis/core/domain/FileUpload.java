package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.service.file.FileUploadScheduleService;

@Entity
@Table(name = "file_upload")
public class FileUpload extends DomainObject {

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "server_file_path", nullable = false)
    private String serverFilePath;

    @Column(name = "filename", nullable = false)
    private String filename;

    /**
     * This represents the id of the entity connected to this FileUpload.
     * Nullable in case file uploads happen before the creation of
     * the corresponding entity. Warning: If left null, the FileUpload
     * will be cleaned up after a timeframe specified in
     * {{@link FileUploadScheduleService}}.
     */
    @Column(name = "entity_id")
    private Long entityId;

    /**
     * This represents the type of the entity that is connected to this
     * FileUpload. The same as above applies to this value.
     */
    @Enumerated
    @Column(name = "entity_type")
    private FileUploadEntityType entityType;

    @Column(name = "creation_date", nullable = false)
    private ZonedDateTime creationDate;

    // Constructors
    public FileUpload() {
    }

    public FileUpload(String path, String serverFilePath, String filename, Long entityId, FileUploadEntityType entityType) {
        this.path = path;
        this.filename = filename;
        this.serverFilePath = serverFilePath;
        this.entityId = entityId;
        this.entityType = entityType;
        this.creationDate = ZonedDateTime.now();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getServerFilePath() {
        return serverFilePath;
    }

    public void setServerFilePath(String serverFilePath) {
        this.serverFilePath = serverFilePath;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public FileUploadEntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(FileUploadEntityType entityType) {
        this.entityType = entityType;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime createdAt) {
        this.creationDate = createdAt;
    }

    @Override
    public String toString() {
        return "FileUpload{" + "id=" + getId() + ", path='" + path + '\'' + ", filename='" + filename + '\'' + ", entityId=" + entityId + ", entityType=" + entityType
                + ", createdAt=" + creationDate + '}';
    }
}
