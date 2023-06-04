package de.tum.in.www1.artemis.domain.enumeration;

/**
 * An enum representing the state of a data export, which is used to determine which actions are currently available.
 */
public enum DataExportState {

    REQUESTED, IN_CREATION, EMAIL_SENT, DOWNLOADED, DOWNLOADED_DELETED, DELETED, FAILED;

    public boolean isDownloadable() {
        return this == DOWNLOADED || this == EMAIL_SENT;
    }

    public boolean hasBeenDownloaded() {
        return this == DOWNLOADED || this == DOWNLOADED_DELETED;
    }

    public boolean hasFailed() {
        return this == FAILED;
    }
}
