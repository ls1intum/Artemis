package de.tum.cit.aet.artemis.communication.domain;

import java.util.Arrays;

public enum PostingType {

    POST((short) 0), ANSWER((short) 1);

    private final short databaseKey;

    PostingType(short databaseKey) {
        this.databaseKey = databaseKey;
    }

    public short getDatabaseKey() {
        return databaseKey;
    }

    public static PostingType fromDatabaseKey(short databaseKey) {
        return Arrays.stream(PostingType.values()).filter(type -> type.getDatabaseKey() == databaseKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
