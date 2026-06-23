import { beforeEach, describe, expect, it, vi } from 'vitest';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { exportUserInformationAsCsv } from 'app/shared-ui/user-import/util/write-users-to-csv';

const { downloadCsvMock } = vi.hoisted(() => ({ downloadCsvMock: vi.fn() }));
vi.mock('app/foundation/util/csv-download.util', () => ({
    downloadCsv: downloadCsvMock,
}));

describe('write-users-to-csv', () => {
    beforeEach(() => downloadCsvMock.mockClear());

    it('should delegate to downloadCsv with the expected options', () => {
        const rows = [
            {
                [NAME_KEY]: 'Ada Lovelace',
                [USERNAME_KEY]: 'ada',
                [EMAIL_KEY]: 'ada@example.com',
                [REGISTRATION_NUMBER_KEY]: '123456',
            },
        ];
        const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];

        exportUserInformationAsCsv(rows, keys, 'user-export');

        expect(downloadCsvMock).toHaveBeenCalledWith(rows, {
            columnHeaders: keys,
            fileName: 'user-export',
            fieldSeparator: ';',
            quoteStrings: true,
            quoteCharacter: '"',
        });
    });
});
