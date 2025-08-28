package de.tum.cit.aet.artemis.lecture.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the possible status values returned by the Nebula transcription service.
 */
public enum NebulaTranscriptionStatus {

    /**
     * The transcription job is still being processed.
     */
    PENDING("pending"),

    /**
     * The transcription job is currently running.
     */
    RUNNING("running"),

    /**
     * The transcription job is being processed.
     */
    PROCESSING("processing"),

    /**
     * The transcription job has completed successfully.
     */
    DONE("done"),

    /**
     * The transcription job has failed with an error.
     */
    ERROR("error");

    private final String value;

    NebulaTranscriptionStatus(String value) {
        this.value = value;
    }

    /**
     * Gets the string value used in the API response.
     *
     * @return the string representation of this status
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Converts a string value to the corresponding enum constant.
     *
     * @param value the string value from the API response
     * @return the corresponding enum constant
     * @throws IllegalArgumentException if the value doesn't match any enum constant
     */
    public static NebulaTranscriptionStatus fromValue(String value) {
        for (NebulaTranscriptionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown transcription status: " + value);
    }

    /**
     * Checks if this status indicates the transcription is completed successfully.
     *
     * @return true if status is DONE
     */
    public boolean isCompleted() {
        return this == DONE;
    }

    /**
     * Checks if this status indicates the transcription has failed.
     *
     * @return true if status is ERROR
     */
    public boolean hasFailed() {
        return this == ERROR;
    }

    /**
     * Checks if this status indicates the transcription is still in progress.
     *
     * @return true if status is PENDING, RUNNING, or PROCESSING
     */
    public boolean isInProgress() {
        return this == PENDING || this == RUNNING || this == PROCESSING;
    }

    @Override
    public String toString() {
        return name();
    }
}
