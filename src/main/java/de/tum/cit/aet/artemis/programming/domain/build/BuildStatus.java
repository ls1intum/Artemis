package de.tum.cit.aet.artemis.programming.domain.build;

/**
 * SUCCESSFUL: the build was successful
 * FAILED: the build failed
 * ERROR: the build produced an error
 * CANCELED: the build was canceled
 * QUEUED: the build is queued
 * BUILDING: the build is currently building
 * MISSING: the build is missing (i.e.
 */

public enum BuildStatus {
    SUCCESSFUL, FAILED, ERROR, CANCELLED, QUEUED, BUILDING, TIMEOUT, MISSING
}
