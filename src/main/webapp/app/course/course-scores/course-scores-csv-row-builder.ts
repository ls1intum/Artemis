import { CourseScoresRowBuilder } from 'app/course/course-scores/course-scores-row-builder';
import { CsvDecimalSeparator } from 'app/shared/export/export-modal.component';
import { round } from 'app/shared/util/utils';

/**
 * Builds CSV rows for the course scores export.
 */
export class CourseScoresCsvRowBuilder extends CourseScoresRowBuilder {
    private readonly decimalSeparator: CsvDecimalSeparator;

    /**
     * Creates a new empty CSV row.
     * @param decimalSeparator The separator that should be used for numbers.
     * @param accuracyOfScores The accuracy of fraction digits that should be used for numbers.
     */
    constructor(decimalSeparator: CsvDecimalSeparator, accuracyOfScores = 1) {
        super(accuracyOfScores);
        this.decimalSeparator = decimalSeparator;
    }

    /**
     * Stores the given value under the key in the row after converting it to the format using the specified decimal separator.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalized(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            this.set(key, round(value, this.accuracyOfScores).toString().replace(/\./, this.decimalSeparator));
        }
    }

    /**
     * Stores the given value under the key in the row after converting it to the percentage format using the specified decimal separator.
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalizedPercent(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            this.set(key, `${round(value, this.accuracyOfScores).toString().replace(/\./, this.decimalSeparator)}%`);
        }
    }
}
