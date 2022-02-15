package de.tum.in.www1.artemis.domain.enumeration;

public enum ExerciseType {

    TEXT, PROGRAMMING, MODELING, FILE_UPLOAD, QUIZ;

    /**
     * Used for human-readable string manipulations e.g. for notifications texts
     * @return the exercise type as a lower case String without any special characters (e.g. FILE_UPLOAD -> "file upload")
     */
    public String getExerciseTypeAsReadableString() {
        return this.toString().toLowerCase().replace('_', ' ');
    }
}
