package de.tum.cit.aet.artemis.communication.domain;

import java.util.Arrays;

public enum SavedPostStatus {

    IN_PROGRESS((short) 0), COMPLETED((short) 1), ARCHIVED((short) 2);

    private final short databaseKey;

    SavedPostStatus(short databaseKey) {
        this.databaseKey = databaseKey;
    }

    public short getDatabaseKey() {
        return databaseKey;
    }

    public static SavedPostStatus fromDatabaseKey(short databaseKey) {
        return Arrays.stream(SavedPostStatus.values()).filter(type -> type.getDatabaseKey() == databaseKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
