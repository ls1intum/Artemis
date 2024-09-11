package de.tum.cit.aet.artemis.domain.iris.settings;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the specific ingestion sub-settings of lectures for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for lecture data ingestion.
 */
@Entity
@DiscriminatorValue("LECTURE_INGESTION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureIngestionSubSettings extends IrisSubSettings {

    @Column(name = "auto_ingest_on_lecture_attachment_upload")
    private boolean autoIngestOnLectureAttachmentUpload;

    public boolean getAutoIngestOnLectureAttachmentUpload() {
        return autoIngestOnLectureAttachmentUpload;
    }

    public void setAutoIngestOnLectureAttachmentUpload(boolean autoIngestOnLectureAttachmentUpload) {
        this.autoIngestOnLectureAttachmentUpload = autoIngestOnLectureAttachmentUpload;
    }

}
