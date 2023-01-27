package de.tum.in.www1.artemis.config;

import java.util.List;

/**
 * Overriding class to only replace the {@link #API_VERSIONS} for testing purposes.
 * This is necessary because the {@link #API_VERSIONS} is final and cannot be changed.
 * Also, we don't want to make this configurable in the configuration files as we define the available versions, not the administrator.
 * Redefining the Bean is not sufficient because we use the versions in classes that are not managed by Spring.
 */
public class VersioningConfiguration {

    // Fake API versions for testing purposes
    public final static List<Integer> API_VERSIONS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

}
