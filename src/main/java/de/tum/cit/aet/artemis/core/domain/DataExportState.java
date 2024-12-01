package de.tum.cit.aet.artemis.core.domain;

/**
 * An enum representing the state of a data export, which is used to determine which actions are currently available.
 * The order of the enum values is important as the enum is mapped to a number representing the enum value starting with 0
 */
public enum DataExportState {

    REQUESTED, IN_CREATION, EMAIL_SENT, DOWNLOADED, DOWNLOADED_DELETED, DELETED, FAILED;

    /**
     * Checks if the data export can be downloaded.
     * <p>
     * The data export can be downloaded if its state is either EMAIL_SENT or DOWNLOADED.
     * The state is EMAIL_SENT if the data export has been created and the user has been notified via email.
     * The state is DOWNLOADED if the user has downloaded the data export at least once.
     *
     * @return true if the data export can be downloaded, false otherwise
     */
    public boolean isDownloadable() {
        return this == DOWNLOADED || this == EMAIL_SENT;
    }

    /**
     * Checks if the data export has been downloaded.
     * <p>
     * The data export has been downloaded if its state is either DOWNLOADED or DOWNLOADED_DELETED.
     * The state is DOWNLOADED if the user has downloaded the data export at least once, but it has not been deleted yet.
     * The state is DOWNLOADED_DELETED if the user has downloaded the data export at least once, and it has been deleted.
     *
     * @return true if the data export has been downloaded, false otherwise
     */
    public boolean hasBeenDownloaded() {
        return this == DOWNLOADED || this == DOWNLOADED_DELETED;
    }

}
