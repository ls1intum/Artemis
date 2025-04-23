package de.tum.cit.aet.artemis.communication.domain;

public enum SavedPostStatus {

    IN_PROGRESS, COMPLETED, ARCHIVED;

    public static SavedPostStatus fromString(String value) {
        return SavedPostStatus.valueOf(value.toUpperCase());
    }
}
