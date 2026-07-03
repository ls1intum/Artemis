import { parse } from 'papaparse';
import { cleanString } from 'app/foundation/util/utils';
import { type StudentParsingResult, readStudentDTOsFromCSVFile } from 'app/shared-ui/user-import/util/read-users-from-csv';

interface CsvUser {
    [key: string]: string;
}

const POSSIBLE_PASSWORD_HEADERS = ['password', 'passwort', 'pwd'];

export async function readUserImportDTOsFromCSVFile(csvFile: File): Promise<StudentParsingResult> {
    const result = await readStudentDTOsFromCSVFile(csvFile);
    if (!result.ok) {
        return result;
    }

    const passwords = await parsePasswordsFromFile(csvFile);
    return {
        ok: true,
        students: result.students.map((student, index) =>
            passwords[index]
                ? {
                      registrationNumber: student.registrationNumber,
                      login: student.login,
                      email: student.email,
                      firstName: student.firstName,
                      lastName: student.lastName,
                      password: passwords[index],
                  }
                : student,
        ),
    };
}

function parsePasswordsFromFile(csvFile: File): Promise<string[]> {
    return new Promise((resolve, reject) => {
        parse<CsvUser>(csvFile, {
            header: true,
            transformHeader: (header: string) => cleanString(header),
            transform: (value: string) => value.trim(),
            skipEmptyLines: true,
            complete: (results) => {
                const passwordHeader = Object.keys(results.data[0] || {}).find((value) => POSSIBLE_PASSWORD_HEADERS.includes(value));
                resolve(results.data.map((user) => (passwordHeader ? user[passwordHeader]?.trim() || '' : '')));
            },
            error: (error) => reject(error),
        });
    });
}
