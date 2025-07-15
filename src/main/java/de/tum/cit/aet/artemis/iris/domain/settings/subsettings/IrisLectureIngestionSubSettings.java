package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * Represents the specific ingestion sub-settings of lectures for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for lecture data ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisLectureIngestionSubSettings extends IrisSubSettings {

    private boolean autoIngestOnLectureAttachmentUpload = true;

    public boolean getAutoIngestOnLectureAttachmentUpload() {
        return autoIngestOnLectureAttachmentUpload;
    }

    public void setAutoIngestOnLectureAttachmentUpload(boolean autoIngestOnLectureAttachmentUpload) {
        this.autoIngestOnLectureAttachmentUpload = autoIngestOnLectureAttachmentUpload;
    }

}
