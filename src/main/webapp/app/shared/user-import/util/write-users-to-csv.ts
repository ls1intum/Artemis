import { download, generateCsv, mkConfig } from 'export-to-csv';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';

export type ExportUserInformationRow = {
    [NAME_KEY]: string;
    [USERNAME_KEY]: string;
    [EMAIL_KEY]: string;
    [REGISTRATION_NUMBER_KEY]: string;
};

export function exportUserInformationAsCsv(rows: ExportUserInformationRow[], keys: string[], fileName: string) {
    const sanitizedRows = rows.map((row) => sanitizeRow(row));
    const options = {
        fieldSeparator: ';',
        quoteStrings: true,
        quoteCharacter: '"',
        showLabels: true,
        showTitle: false,
        filename: fileName,
        useTextFile: false,
        useBom: true,
        columnHeaders: keys,
    };
    const csvExportConfig = mkConfig(options);
    const csvData = generateCsv(csvExportConfig)(sanitizedRows);
    download(csvExportConfig)(csvData);
}

function sanitizeRow(row: ExportUserInformationRow): ExportUserInformationRow {
    return {
        [NAME_KEY]: sanitizeValue(row[NAME_KEY]),
        [USERNAME_KEY]: sanitizeValue(row[USERNAME_KEY]),
        [EMAIL_KEY]: sanitizeValue(row[EMAIL_KEY]),
        [REGISTRATION_NUMBER_KEY]: sanitizeValue(row[REGISTRATION_NUMBER_KEY]),
    };
}

/**
 * Prefixes values that look like spreadsheet formulas so Excel and similar tools
 * treat them as plain text instead of executing them. This prevents CSV injection
 * because exported fields contain user-controlled input from students, while
 * the CSV is typically opened by instructors or admins.
 */
function sanitizeValue(value: string): string {
    return /^\s*[=+\-@]/.test(value) ? `'${value}` : value;
}
