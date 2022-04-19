import { CourseScoresRowBuilder } from 'app/course/course-scores/course-scores-row-builder';

/**
 * A function that converts a number into the localized representation for the user.
 */
type Localizer = (value: number) => string;

/**
 * Builds CSV rows for the course scores export.
 */
export class CourseScoresCsvRowBuilder extends CourseScoresRowBuilder {
    private readonly localizer: Localizer;
    private readonly percentLocalizer: Localizer;

    /**
     * Creates a new empty CSV row.
     * @param localizer The function that should be used to convert numbers into their localized representations.
     * @param percentLocalizer The function that should be used to convert percentage values into their localized representations.
     * @param accuracyOfScores The accuracy of fraction digits that should be used for numbers.
     */
    constructor(localizer: Localizer, percentLocalizer: Localizer, accuracyOfScores = 1) {
        super(accuracyOfScores);
        this.localizer = localizer;
        this.percentLocalizer = percentLocalizer;
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalized(key: string, value: number) {
        this.set(key, this.localizer(value));
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized percentage format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalizedPercent(key: string, value: number) {
        this.set(key, this.percentLocalizer(value));
    }
}
