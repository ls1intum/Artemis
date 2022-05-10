import { ExerciseType } from 'app/entities/exercise.model';
import { EMAIL_KEY, NAME_KEY, POINTS_KEY, REGISTRATION_NUMBER_KEY, SCORE_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';

export type ExportRow = any;

/**
 * Builds rows for exporting student scores.
 */
export abstract class ExportRowBuilder {
    private exportRow = {};

    readonly accuracyOfScores: number;

    /**
     * Creates a new row builder.
     * @param accuracyOfScores The accuracy of fraction digits that should be used for numbers.
     */
    protected constructor(accuracyOfScores = 1) {
        this.accuracyOfScores = accuracyOfScores;
    }

    /**
     * Constructs and returns the actual row data.
     */
    build(): ExportRow {
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
     * Stores the given points under the key in the row after converting it to the localized format.
     * @param key Which should be associated with the given points.
     * @param points That should be placed in the row.
     */
    abstract setPoints(key: string, points: number | undefined): void;

    /**
     * Stores the given score under the key in the row after converting it to the localized percentage format.
     * @param key Which should be associated with the given score.
     * @param score That should be placed in the row.
     */
    abstract setScore(key: string, score: number | undefined): void;

    /**
     * Adds information about the student user to the row.
     * @param name The name of the student that should be saved.
     * @param username The username of the student that should be saved.
     * @param email The email of the student that should be saved.
     * @param registrationNumber The registration number of the student that should be saved.
     */
    setUserInformation(name?: string, username?: string, email?: string, registrationNumber?: string) {
        this.set(NAME_KEY, name?.trim());
        this.set(USERNAME_KEY, username?.trim());
        this.set(EMAIL_KEY, email?.trim());
        this.set(REGISTRATION_NUMBER_KEY, registrationNumber?.trim());
    }

    /**
     * Adds the points for the given exercise type to the row.
     * @param exerciseType The type of the exercise.
     * @param points The number of points for this exercise type, alternatively already converted to its localized format.
     */
    setExerciseTypePoints(exerciseType: ExerciseType, points: number | string) {
        const key = ExportRowBuilder.getExerciseTypeKey(exerciseType, POINTS_KEY);
        if (typeof points === 'number') {
            this.setPoints(key, points);
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
        const key = ExportRowBuilder.getExerciseTypeKey(exerciseType, SCORE_KEY);
        if (typeof score === 'number') {
            this.setScore(key, score);
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
        const exerciseTypeName = ExportRowBuilder.capitalizeFirstLetter(exerciseType);
        return `${exerciseTypeName} ${suffix}`;
    }

    private static capitalizeFirstLetter(string: string): string {
        return string.charAt(0).toUpperCase() + string.slice(1);
    }
}
