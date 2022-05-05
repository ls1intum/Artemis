import { CourseScoresRowBuilder } from 'app/course/course-scores/course-scores-row-builder';
import { round } from 'app/shared/util/utils';

/**
 * This interface represents a cell object, which conforms to the Common Spreadsheet Format (CSF).
 * https://github.com/sheetjs/sheetjs#common-spreadsheet-format
 */
export interface CommonSpreadsheetCellObject {
    t: string; // type -> b: Boolean, e: Error, n: Number, d: Date, s: Text, z: Stub
    v: number; // raw value
    z?: string; // number format string associated with the cell (if requested)
}

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
     * Stores the given value under the key in the row after converting it to the localized format stored in the Common Spreadsheet Format (CSF).
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalized(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            const numberCell: CommonSpreadsheetCellObject = {
                t: 'n',
                v: round(value, this.accuracyOfScores),
            };
            this.set(key, numberCell);
        }
    }

    /**
     * Stores the given value under the key in the row after converting it to the localized percentage format stored in the Common Spreadsheet Format (CSF).
     * @param key Which should be associated with the given value.
     * @param value That should be placed in the row.
     */
    setLocalizedPercent(key: string, value: number) {
        if (isNaN(value)) {
            this.set(key, '-');
        } else {
            const roundedScore = round(value, this.accuracyOfScores);
            const percentageFormat = Number.isInteger(roundedScore) ? '0%' : '0.' + Array(this.accuracyOfScores + 1).join('0') + '%';
            const percentageCell: CommonSpreadsheetCellObject = {
                t: 'n',
                v: round(roundedScore / 100, this.accuracyOfScores + 3),
                z: percentageFormat,
            };
            this.set(key, percentageCell);
        }
    }
}
