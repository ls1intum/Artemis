import { ExportRowBuilder } from 'app/shared/export/export-row-builder';
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
 * Builds Excel rows for exporting student scores.
 */
export class ExcelExportRowBuilder extends ExportRowBuilder {
    /**
     * Creates a new empty Excel row.
     */
    constructor(accuracyOfScores = 1) {
        super(accuracyOfScores);
    }

    /**
     * Stores the given points under the key in the row after converting it to the localized format stored in the Common Spreadsheet Format (CSF).
     * @param key Which should be associated with the given points.
     * @param points That should be placed in the row.
     */
    setPoints(key: string, points: number | undefined) {
        if (points == undefined || isNaN(points)) {
            this.set(key, '-');
        } else {
            const numberCell: CommonSpreadsheetCellObject = {
                t: 'n',
                v: round(points, this.accuracyOfScores),
            };
            this.set(key, numberCell);
        }
    }

    /**
     * Stores the given score under the key in the row after converting it to the localized percentage format stored in the Common Spreadsheet Format (CSF).
     * @param key Which should be associated with the given score.
     * @param score That should be placed in the row.
     */
    setScore(key: string, score: number | undefined) {
        if (score == undefined || isNaN(score)) {
            this.set(key, '-');
        } else {
            const roundedScore = round(score, this.accuracyOfScores);
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
