import { download, generateCsv, mkConfig } from 'export-to-csv';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';

export type ExportUserInformationRow = {
    [NAME_KEY]: string;
    [USERNAME_KEY]: string;
    [EMAIL_KEY]: string;
    [REGISTRATION_NUMBER_KEY]: string;
};

export function exportUserInformationAsCsv(rows: ExportUserInformationRow[], keys: string[], fileName: string) {
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
    const csvData = generateCsv(csvExportConfig)(rows);
    download(csvExportConfig)(csvData);
}
