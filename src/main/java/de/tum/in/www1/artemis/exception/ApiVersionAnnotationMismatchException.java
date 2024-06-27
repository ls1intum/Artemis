package de.tum.in.www1.artemis.exception;

/**
 * Exception thrown if the versioning annotations are not correctly set.
 */
public class ApiVersionAnnotationMismatchException extends RuntimeException {

    public ApiVersionAnnotationMismatchException() {
        super("Both @VersionRange and @VersionRanges annotations are present on the same method. Please use only one of them.");
    }
}
