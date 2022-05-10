import { ExportRowBuilder } from 'app/shared/export/export-row-builder';
import { CsvDecimalSeparator } from 'app/shared/export/export-modal.component';
import { round } from 'app/shared/util/utils';

/**
 * Builds CSV rows for exporting student scores.
 */
export class CsvExportRowBuilder extends ExportRowBuilder {
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
     * Stores the given points under the key in the row after converting it to the format using the specified decimal separator.
     * @param key Which should be associated with the given points.
     * @param points That should be placed in the row.
     */
    setPoints(key: string, points: number | undefined) {
        if (points == undefined || isNaN(points)) {
            this.set(key, '-');
        } else {
            this.set(key, round(points, this.accuracyOfScores).toString().replace(/\./, this.decimalSeparator));
        }
    }

    /**
     * Stores the given score under the key in the row after converting it to the percentage format using the specified decimal separator.
     * @param key Which should be associated with the given score.
     * @param score That should be placed in the row.
     */
    setScore(key: string, score: number | undefined) {
        if (score == undefined || isNaN(score)) {
            this.set(key, '-');
        } else {
            this.set(key, `${round(score, this.accuracyOfScores).toString().replace(/\./, this.decimalSeparator)}%`);
        }
    }
}
