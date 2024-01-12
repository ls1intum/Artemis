package de.tum.in.www1.artemis.domain.enumeration;

/**
 * SUCCESSFUL: the build was successful
 * FAILED: the build failed
 * ERROR: the build produced an error
 * CANCELED: the build was canceled
 */

public enum BuildJobResult {
    SUCCESSFUL, FAILED, ERROR, CANCELLED
}
