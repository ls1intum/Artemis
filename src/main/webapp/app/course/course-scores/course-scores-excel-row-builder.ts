import { CourseScoresRowBuilder } from 'app/course/course-scores/course-scores-row-builder';
import { round } from 'app/shared/util/utils';

/**
 * Builds Excel rows for the course scores export.
 */
export class CourseScoresExcelRowBuilder extends CourseScoresRowBuilder {
    /**
     * Creates a new empty Excel row.
     */
    constructor(accuracyOfScores = 1) {
        super(accuracyOfScores);
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalized(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            const numberCell = {
                t: 'n',
                v: round(value, this.accuracyOfScores),
            };
            this.set(key, numberCell);
        }
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized percentage format.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalizedPercent(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            const roundedScore = round(value, this.accuracyOfScores);
            const percentageFormat = Number.isInteger(roundedScore) ? '0%' : '0.' + Array(this.accuracyOfScores + 1).join('0') + '%';
            const percentageCell = {
                t: 'n',
                v: round(roundedScore / 100, this.accuracyOfScores + 3),
                z: percentageFormat,
            };
            this.set(key, percentageCell);
        }
    }
}
