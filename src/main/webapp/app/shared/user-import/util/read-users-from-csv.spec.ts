import { describe, expect, it } from 'vitest';
import { readExamUserDTOsFromCSVFile, readStudentDTOsFromCSVFile } from 'app/shared/user-import/util/read-users-from-csv';

function createCsvFile(content: string, fileName = 'users.csv'): File {
    return new File([content], fileName, { type: 'text/csv' });
}

describe('read-users-from-csv', () => {
    describe('readStudentDTOsFromCSVFile', () => {
        it('should parse a valid student csv with canonical headers', async () => {
            const csv = ['registrationnumber,firstname,lastname,login,email', ' 123456 , Ada , Lovelace , ada , ada@example.com '].join('\n');
            const result = await readStudentDTOsFromCSVFile(createCsvFile(csv));
            expect(result).toEqual({
                ok: true,
                students: [
                    {
                        registrationNumber: '123456',
                        firstName: 'Ada',
                        lastName: 'Lovelace',
                        login: 'ada',
                        email: 'ada@example.com',
                    },
                ],
            });
        });

        it('should parse a valid student csv with alternative cleaned headers and skip empty lines', async () => {
            const csv = ['Matrikelnummer,Vorname,Familienname,Benutzername,E-Mail', '', ' 7654321 , Grace , Hopper , ghopper , grace@example.com ', ''].join('\n');
            const result = await readStudentDTOsFromCSVFile(createCsvFile(csv));
            expect(result).toEqual({
                ok: true,
                students: [
                    {
                        registrationNumber: '7654321',
                        firstName: 'Grace',
                        lastName: 'Hopper',
                        login: 'ghopper',
                        email: 'grace@example.com',
                    },
                ],
            });
        });

        it('should return invalid row indices for rows without registration number, login, or email', async () => {
            const csv = ['registrationnumber,firstname,lastname,login,email', ',Ada,Lovelace,,', '123,Bob,Ross,,', ',Grace,Hopper,ghopper,', ',Alan,Turing,,alan@example.com'].join(
                '\n',
            );
            const result = await readStudentDTOsFromCSVFile(createCsvFile(csv));
            expect(result).toEqual({
                ok: false,
                invalidRowIndices: [2],
            });
        });

        it('should return an empty student list for a header-only csv', async () => {
            const csv = 'registrationnumber,firstname,lastname,login,email';
            const result = await readStudentDTOsFromCSVFile(createCsvFile(csv));
            expect(result).toEqual({
                ok: true,
                students: [],
            });
        });

        it('should default missing optional headers to empty strings', async () => {
            const csv = ['login', 'ada'].join('\n');
            const result = await readStudentDTOsFromCSVFile(createCsvFile(csv));
            expect(result).toEqual({
                ok: true,
                students: [
                    {
                        registrationNumber: '',
                        firstName: '',
                        lastName: '',
                        login: 'ada',
                        email: '',
                    },
                ],
            });
        });
    });

    describe('readExamUserDTOsFromCSVFile', () => {
        it('should parse a valid exam csv including room and seat information', async () => {
            const csv = ['registrationnumber,firstname,lastname,login,email,room,seat', ' 2222 , Emmy , Noether , enoether , emmy@example.com , MI 1.01 , 42A '].join('\n');
            const result = await readExamUserDTOsFromCSVFile(createCsvFile(csv, 'exam-users.csv'));
            expect(result).toEqual({
                ok: true,
                examUsers: [
                    {
                        registrationNumber: '2222',
                        firstName: 'Emmy',
                        lastName: 'Noether',
                        login: 'enoether',
                        email: 'emmy@example.com',
                        room: 'MI 1.01',
                        seat: '42A',
                    },
                ],
            });
        });

        it('should parse a valid exam csv with alternative room and seat headers', async () => {
            const csv = ['number,forename,surname,user,mail,raum,sitzplatz', ' 3333 , Katherine , Johnson , kjohnson , katherine@example.com , HS 2 , 7C '].join('\n');
            const result = await readExamUserDTOsFromCSVFile(createCsvFile(csv, 'exam-users.csv'));
            expect(result).toEqual({
                ok: true,
                examUsers: [
                    {
                        registrationNumber: '3333',
                        firstName: 'Katherine',
                        lastName: 'Johnson',
                        login: 'kjohnson',
                        email: 'katherine@example.com',
                        room: 'HS 2',
                        seat: '7C',
                    },
                ],
            });
        });

        it('should return invalid row indices for invalid exam rows', async () => {
            const csv = ['registrationnumber,login,email,room,seat', ',,,MI 1.01,42A', '3333,,,MI 1.01,42B', ',kjohnson,,MI 1.01,42C', ',,katherine@example.com,MI 1.01,42D'].join(
                '\n',
            );
            const result = await readExamUserDTOsFromCSVFile(createCsvFile(csv, 'exam-users.csv'));
            expect(result).toEqual({
                ok: false,
                invalidRowIndices: [2],
            });
        });

        it('should default all missing optional exam fields to empty strings when headers are absent', async () => {
            const csv = ['registrationnumber', '4444'].join('\n');
            const result = await readExamUserDTOsFromCSVFile(createCsvFile(csv, 'exam-users.csv'));
            expect(result).toEqual({
                ok: true,
                examUsers: [
                    {
                        registrationNumber: '4444',
                        firstName: '',
                        lastName: '',
                        login: '',
                        email: '',
                        room: '',
                        seat: '',
                    },
                ],
            });
        });
    });
});
