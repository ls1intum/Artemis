import { downloadCsv } from 'app/foundation/util/csv-download.util';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';

export type ExportUserInformationRow = {
    [NAME_KEY]: string;
    [USERNAME_KEY]: string;
    [EMAIL_KEY]: string;
    [REGISTRATION_NUMBER_KEY]: string;
};

export function exportUserInformationAsCsv(rows: ExportUserInformationRow[], keys: string[], fileName: string) {
    // downloadCsv guards every string cell against CSV/formula injection, so no extra sanitization is needed here.
    downloadCsv(rows, {
        columnHeaders: keys,
        fileName,
        fieldSeparator: ';',
        quoteStrings: true,
        quoteCharacter: '"',
    });
}
