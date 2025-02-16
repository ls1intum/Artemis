package de.tum.cit.aet.artemis.communication.domain.push_notification;

import java.util.Arrays;

public enum PushNotificationApiType {

    DEFAULT((short) 0), IOS_V2((short) 1);

    private final short databaseKey;

    PushNotificationApiType(short databaseKey) {
        this.databaseKey = databaseKey;
    }

    public short getDatabaseKey() {
        return databaseKey;
    }

    public static PushNotificationApiType fromDatabaseKey(short databaseKey) {
        return Arrays.stream(PushNotificationApiType.values()).filter(type -> type.getDatabaseKey() == databaseKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
