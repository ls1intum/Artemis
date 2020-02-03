package de.tum.in.www1.artemis.util;

import org.eclipse.jgit.lib.ObjectId;

public class TestConstants {

    public static final String COMMIT_HASH_STRING = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";

    public static final ObjectId COMMIT_HASH_OBJECT_ID = ObjectId.fromString(COMMIT_HASH_STRING);

    private TestConstants() {
    }
}
