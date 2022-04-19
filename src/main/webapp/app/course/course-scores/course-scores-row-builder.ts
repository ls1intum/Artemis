import { ExerciseType } from 'app/entities/exercise.model';
import { EMAIL_KEY, NAME_KEY, POINTS_KEY, REGISTRATION_NUMBER_KEY, SCORE_KEY, USERNAME_KEY } from 'app/course/course-scores/course-scores.component';
import { CourseScoresStudentStatistics } from 'app/course/course-scores/course-scores-student-statistics';

export type CourseScoresExportRow = any;

/**
 * Builds CSV rows for the course scores export.
 */
export abstract class CourseScoresRowBuilder {
    private exportRow = {};

    readonly accuracyOfScores: number;

    /**
     * Creates a new row builder.
     * @param accuracyOfScores The accuracy of fraction digits that should be used for numbers.
     */
    constructor(accuracyOfScores = 1) {
        this.accuracyOfScores = accuracyOfScores;
    }

    /**
     * Constructs and returns the actual row data.
     */
    build(): CourseScoresExportRow {
        return this.exportRow;
    }

    /**
     * Stores the given value under the key in the row.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row. Replaced by the empty string if undefined.
     */
    set<T>(key: string, value: T) {
        this.exportRow[key] = value ?? '';
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    abstract setLocalized(key: string, value: number): void;

    /**
     * Stores the given value under the key in the row after converting it to the localized percentage format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    abstract setLocalizedPercent(key: string, value: number): void;

    /**
     * Adds information about the student user to the row.
     * @param student A student of which the data should be saved.
     */
    setUserInformation(student: CourseScoresStudentStatistics) {
        this.set(NAME_KEY, student.user.name?.trim());
        this.set(USERNAME_KEY, student.user.login?.trim());
        this.set(EMAIL_KEY, student.user.email?.trim());
        this.set(REGISTRATION_NUMBER_KEY, student.user.visibleRegistrationNumber?.trim());
    }

    /**
     * Adds the points for the given exercise type to the row.
     * @param exerciseType The type of the exercise.
     * @param points The number of points for this exercise type, alternatively already converted to its localized format.
     */
    setExerciseTypePoints(exerciseType: ExerciseType, points: number | string) {
        const key = CourseScoresRowBuilder.getExerciseTypeKey(exerciseType, POINTS_KEY);
        if (typeof points === 'number') {
            this.setLocalized(key, points);
        } else {
            this.set(key, points);
        }
    }

    /**
     * Adds the score for the given exercise type to the row.
     * @param exerciseType The type of the exercise.
     * @param score The score for this exercise type, alternatively already converted to its localized percentage format.
     */
    setExerciseTypeScore(exerciseType: ExerciseType, score: number | string) {
        const key = CourseScoresRowBuilder.getExerciseTypeKey(exerciseType, SCORE_KEY);
        if (typeof score === 'number') {
            this.setLocalizedPercent(key, score);
        } else {
            this.set(key, score);
        }
    }

    /**
     * Generates the proper key under which information related to the exercise type should be stored.
     * @param exerciseType The exercise type.
     * @param suffix A suffix that should be appended to the key.
     */
    static getExerciseTypeKey(exerciseType: ExerciseType, suffix: string): string {
        const exerciseTypeName = CourseScoresRowBuilder.capitalizeFirstLetter(exerciseType);
        return `${exerciseTypeName} ${suffix}`;
    }

    private static capitalizeFirstLetter(string: string): string {
        return string.charAt(0).toUpperCase() + string.slice(1);
    }
}
