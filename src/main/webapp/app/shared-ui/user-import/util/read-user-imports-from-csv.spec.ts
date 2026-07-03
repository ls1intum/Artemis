import { describe, expect, it } from 'vitest';
import { readUserImportDTOsFromCSVFile } from 'app/shared-ui/user-import/util/read-user-imports-from-csv';

function createCsvFile(content: string): File {
    return new File([content], 'users.csv', { type: 'text/csv' });
}

describe('readUserImportDTOsFromCSVFile', () => {
    it('should read the optional password column for admin user imports', async () => {
        const result = await readUserImportDTOsFromCSVFile(createCsvFile(['login,password', 'ada,secret123', 'grace, '].join('\n')));

        expect(result).toEqual({
            ok: true,
            students: [
                {
                    registrationNumber: '',
                    firstName: '',
                    lastName: '',
                    login: 'ada',
                    email: '',
                    password: 'secret123',
                },
                {
                    registrationNumber: '',
                    firstName: '',
                    lastName: '',
                    login: 'grace',
                    email: '',
                },
            ],
        });
    });

    it('should accept password header aliases', async () => {
        const result = await readUserImportDTOsFromCSVFile(createCsvFile(['login,Passwort', 'ada,init-pw'].join('\n')));

        expect(result.ok).toBe(true);
        if (result.ok) {
            expect(result.students[0].password).toBe('init-pw');
        }
    });
});
