package de.tum.cit.aet.artemis.communication.domain;

import java.util.Locale;

public enum SavedPostStatus {

    IN_PROGRESS, COMPLETED, ARCHIVED;

    public static SavedPostStatus fromString(String value) {
        return SavedPostStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
