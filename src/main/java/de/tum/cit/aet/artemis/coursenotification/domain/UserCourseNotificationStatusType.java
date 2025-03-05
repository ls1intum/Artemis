package de.tum.cit.aet.artemis.coursenotification.domain;

import java.util.Arrays;

public enum UserCourseNotificationStatusType {

    UNSEEN((short) 0), SEEN((short) 1), ARCHIVED((short) 2);

    private final short databaseKey;

    UserCourseNotificationStatusType(short databaseKey) {
        this.databaseKey = databaseKey;
    }

    public short getDatabaseKey() {
        return databaseKey;
    }

    public static UserCourseNotificationStatusType fromDatabaseKey(short databaseKey) {
        return Arrays.stream(UserCourseNotificationStatusType.values()).filter(type -> type.getDatabaseKey() == databaseKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown database key: " + databaseKey));
    }
}
