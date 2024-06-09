package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.annotation.Nullable;
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

    @Nullable
    @Column(name = "auto_ingest_on_lecture_attachment_upload")
    private Boolean autoIngestOnLectureAttachmentUpload;

    @Nullable
    public Boolean getAutoIngestOnLectureAttachmentUpload() {
        return autoIngestOnLectureAttachmentUpload;
    }

    public void setAutoIngestOnLectureAttachmentUpload(@Nullable Boolean autoIngestOnLectureAttachmentUpload) {
        this.autoIngestOnLectureAttachmentUpload = autoIngestOnLectureAttachmentUpload;
    }

}
