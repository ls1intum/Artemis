package de.tum.cit.aet.artemis.communication.domain;

import java.util.Arrays;

public enum PostingType {

    POST("post"), ANSWER("answer");

    private final String databaseKey;

    PostingType(String databaseKey) {
        this.databaseKey = databaseKey;
    }

    public String getDatabaseKey() {
        return databaseKey;
    }

    public static PostingType fromDatabaseKey(String databaseKey) {
        return Arrays.stream(PostingType.values()).filter(type -> type.getDatabaseKey().equalsIgnoreCase(databaseKey)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
