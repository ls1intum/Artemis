package de.tum.cit.aet.artemis.service.archival;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.domain.Exercise;

public class ArchivalReportEntry {

    @Nullable
    private final Exercise exercise;

    private final String exerciseName;

    private final int participants;

    private final int successfulExports;

    private final int participantsWithoutSubmission;

    private final int failedExports;

    /**
     * Generates a new entry with the given data
     */
    public ArchivalReportEntry(@Nullable Exercise exercise, String exerciseName, int participants, int successfulExports, int participantsWithoutSubmission, int failedExports) {
        this.exercise = exercise;
        this.exerciseName = exerciseName;
        this.participants = participants;
        this.successfulExports = successfulExports;
        this.participantsWithoutSubmission = participantsWithoutSubmission;
        this.failedExports = failedExports;
    }

    /**
     * Shortcut for {@link ArchivalReportEntry} but calculates the amount of fails
     */
    public ArchivalReportEntry(Exercise exercise, String exerciseName, int participants, int successfulExports, int participantsWithoutSubmission) {
        this(exercise, exerciseName, participants, successfulExports, participantsWithoutSubmission, participants - successfulExports - participantsWithoutSubmission);
    }

    /**
     * @return a line of a csv file for this entry
     */
    @Override
    public String toString() {
        return (exercise != null ? exercise.getId() + "," + exercise.getClass().getSimpleName() : ",") + "," + exerciseName + "," + participants + "," + successfulExports + ","
                + participantsWithoutSubmission + "," + failedExports;
    }

    /**
     * @return the headline of a csv file containing entries of this class
     */
    public static String getHeadline() {
        return "id,type,name,participants,successful exports,participants without submission,failed exports";
    }
}
