package de.tum.in.www1.artemis.domain.enumeration;

public enum ExerciseType {

    TEXT, PROGRAMMING, MODELING, FILE_UPLOAD, QUIZ;

    // this enum was created to get quick access to the name of the exercise type as a String to use in (notification)text
    @Override
    public String toString() {
        return switch (this) {
            case FILE_UPLOAD -> "file upload";
            default -> this.name().toLowerCase();
        };
    }
}
