package de.tum.cit.aet.artemis.communication.domain;

import java.util.Arrays;

public enum SavedPostStatus {

    IN_PROGRESS("progress"), COMPLETED("completed"), ARCHIVED("archived");

    private final String databaseKey;

    SavedPostStatus(String databaseKey) {
        this.databaseKey = databaseKey;
    }

    public String getDatabaseKey() {
        return databaseKey;
    }

    public static SavedPostStatus fromDatabaseKey(String databaseKey) {
        return Arrays.stream(SavedPostStatus.values()).filter(type -> type.getDatabaseKey().equalsIgnoreCase(databaseKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
