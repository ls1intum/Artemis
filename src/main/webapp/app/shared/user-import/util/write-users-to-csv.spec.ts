import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import { exportUserInformationAsCsv } from 'app/shared/user-import/util/write-users-to-csv';

const exportToCsvMocks = vi.hoisted(() => {
    const generateCsvExecutor = vi.fn();
    const downloadExecutor = vi.fn();
    const mkConfig = vi.fn((options) => ({ ...options, marker: 'config' }));
    const generateCsv = vi.fn(() => generateCsvExecutor);
    const download = vi.fn(() => downloadExecutor);

    return { generateCsvExecutor, downloadExecutor, mkConfig, generateCsv, download };
});

vi.mock('export-to-csv', () => ({
    mkConfig: exportToCsvMocks.mkConfig,
    generateCsv: exportToCsvMocks.generateCsv,
    download: exportToCsvMocks.download,
}));

describe('write-users-to-csv', () => {
    beforeEach(() => {
        exportToCsvMocks.mkConfig.mockClear();
        exportToCsvMocks.generateCsv.mockClear();
        exportToCsvMocks.download.mockClear();
        exportToCsvMocks.generateCsvExecutor.mockReset();
        exportToCsvMocks.downloadExecutor.mockReset();
    });

    it('should export user information rows as csv with the expected config', () => {
        const rows = [
            {
                [NAME_KEY]: 'Ada Lovelace',
                [USERNAME_KEY]: 'ada',
                [EMAIL_KEY]: 'ada@example.com',
                [REGISTRATION_NUMBER_KEY]: '123456',
            },
        ];
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
        exportToCsvMocks.generateCsvExecutor.mockReturnValue('csv-data');

        exportUserInformationAsCsv(rows, keys, 'user-export');

        expect(exportToCsvMocks.mkConfig).toHaveBeenCalledWith({
            fieldSeparator: ';',
            quoteStrings: true,
            quoteCharacter: '"',
            showLabels: true,
            showTitle: false,
            filename: 'user-export',
            useTextFile: false,
            useBom: true,
            columnHeaders: keys,
        });
        expect(exportToCsvMocks.generateCsv).toHaveBeenCalledWith(expect.objectContaining({ filename: 'user-export', columnHeaders: keys }));
        expect(exportToCsvMocks.generateCsvExecutor).toHaveBeenCalledWith(rows);
        expect(exportToCsvMocks.download).toHaveBeenCalledWith(expect.objectContaining({ filename: 'user-export', columnHeaders: keys }));
        expect(exportToCsvMocks.downloadExecutor).toHaveBeenCalledWith('csv-data');
    });

    it('should sanitize spreadsheet formulas before generating csv data', () => {
        const rows = [
            {
                [NAME_KEY]: '=HYPERLINK("https://example.com")',
                [USERNAME_KEY]: '+ada',
                [EMAIL_KEY]: ' normal@example.com',
                [REGISTRATION_NUMBER_KEY]: '@123456',
            },
        ];
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
        exportToCsvMocks.generateCsvExecutor.mockReturnValue('csv-data');

        exportUserInformationAsCsv(rows, keys, 'user-export');

        expect(exportToCsvMocks.generateCsvExecutor).toHaveBeenCalledWith([
            {
                [NAME_KEY]: '\'=HYPERLINK("https://example.com")',
                [USERNAME_KEY]: "'+ada",
                [EMAIL_KEY]: ' normal@example.com',
                [REGISTRATION_NUMBER_KEY]: "'@123456",
            },
        ]);
    });
});
