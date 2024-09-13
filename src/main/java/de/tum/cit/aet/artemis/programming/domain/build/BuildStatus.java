package de.tum.cit.aet.artemis.programming.domain.build;

/**
 * SUCCESSFUL: the build was successful
 * FAILED: the build failed
 * ERROR: the build produced an error
 * CANCELED: the build was canceled
 */

public enum BuildStatus {
    SUCCESSFUL, FAILED, ERROR, CANCELLED
}
