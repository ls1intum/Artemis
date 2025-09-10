package de.tum.cit.aet.artemis.core.domain;

import java.util.Arrays;

public enum FileUploadEntityType {

    CONVERSATION((short) 0);

    private final short databaseKey;

    FileUploadEntityType(short databaseKey) {
        this.databaseKey = databaseKey;
    }

    public short getDatabaseKey() {
        return databaseKey;
    }

    public static FileUploadEntityType fromDatabaseKey(short databaseKey) {
        return Arrays.stream(FileUploadEntityType.values()).filter(type -> type.getDatabaseKey() == databaseKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
