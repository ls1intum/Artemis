import { parse } from 'papaparse';
import { cleanString } from 'app/foundation/util/utils';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';

export type StudentParsingResult = { ok: true; students: StudentDTO[] } | { ok: false; invalidRowIndices: number[] };
export type ExamUserParsingResult = { ok: true; examUsers: ExamUserDTO[] } | { ok: false; invalidRowIndices: number[] };
interface CsvUser {
    [key: string]: string;
}

type CsvUserParsingResult = { ok: true; users: CsvUser[]; header: (aliases: string[]) => string } | { ok: false; invalidRowIndices: number[] };

const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_EMAIL_HEADERS = ['email', 'e-mail', 'mail'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];
const POSSIBLE_ROOM_HEADERS = ['actualroom', 'actualRoom', 'raum', 'room', 'Room'];
const POSSIBLE_SEAT_HEADERS = ['actualseat', 'actualSeat', 'sitzplatz', 'sitz', 'seat', 'Seat'];
// Deliberately not the bare 'test', which would collide with unrelated columns.
const POSSIBLE_IS_TEST_USER_HEADERS = ['istestuser', 'testuser', 'testaccount'];
const TRUTHY_CELL_VALUES = ['true', '1', 'yes', 'x', 'ja', 'wahr'];

function parseBooleanCell(value?: string): boolean {
    return value !== undefined && TRUTHY_CELL_VALUES.includes(value.trim().toLowerCase());
}

export async function readStudentDTOsFromCSVFile(csvFile: File): Promise<StudentParsingResult> {
    const parsed = await readValidatedCsvUsers(csvFile);
    if (!parsed.ok) {
        return parsed;
    }
    const readIdentity = csvUserIdentityReader(parsed.header);
    const testUserHeader = parsed.header(POSSIBLE_IS_TEST_USER_HEADERS);
    const students = parsed.users.map((user) => {
        const student = readIdentity(user);
        // An absent column must not change an existing user's flag during import.
        student.isTestUser = testUserHeader ? parseBooleanCell(user[testUserHeader]) : undefined;
        return student;
    });
    return {
        ok: true,
        students: students,
    };
}

export async function readExamUserDTOsFromCSVFile(csvFile: File): Promise<ExamUserParsingResult> {
    const parsed = await readValidatedCsvUsers(csvFile);
    if (!parsed.ok) {
        return parsed;
    }
    const readIdentity = csvUserIdentityReader(parsed.header);
    const roomHeader = parsed.header(POSSIBLE_ROOM_HEADERS);
    const seatHeader = parsed.header(POSSIBLE_SEAT_HEADERS);
    const students = parsed.users.map((user) => {
        const examUser: ExamUserDTO = readIdentity(user);
        examUser.room = user[roomHeader]?.trim() || '';
        examUser.seat = user[seatHeader]?.trim() || '';
        return examUser;
    });
    return {
        ok: true,
        examUsers: students,
    };
}

async function readValidatedCsvUsers(csvFile: File): Promise<CsvUserParsingResult> {
    const users = await parseCsvUserFromFile(csvFile);
    const invalidRowIndices = computeInvalidRowIndices(users);
    if (invalidRowIndices.length) {
        return { ok: false, invalidRowIndices };
    }
    const headers = Object.keys(users.first() || []);
    return { ok: true, users, header: (aliases) => headers.find((name) => aliases.includes(name)) || '' };
}

function csvUserIdentityReader(header: (aliases: string[]) => string): (user: CsvUser) => StudentDTO {
    const registrationNumber = header(POSSIBLE_REGISTRATION_NUMBER_HEADERS);
    const login = header(POSSIBLE_LOGIN_HEADERS);
    const email = header(POSSIBLE_EMAIL_HEADERS);
    const firstName = header(POSSIBLE_FIRST_NAME_HEADERS);
    const lastName = header(POSSIBLE_LAST_NAME_HEADERS);
    return (user) => ({
        registrationNumber: user[registrationNumber]?.trim() || '',
        login: user[login]?.trim() || '',
        email: user[email]?.trim() || '',
        firstName: user[firstName]?.trim() || '',
        lastName: user[lastName]?.trim() || '',
    });
}

function parseCsvUserFromFile(csvFile: File): Promise<CsvUser[]> {
    return new Promise((resolve, reject) => {
        parse<CsvUser>(csvFile, {
            header: true,
            transformHeader: (header: string) => cleanString(header),
            transform: (value: string) => value.trim(),
            skipEmptyLines: true,
            complete: (results) => resolve(results.data),
            error: (error) => reject(error),
        });
    });
}

function computeInvalidRowIndices(csvUsers: CsvUser[]): number[] {
    const invalidList: number[] = [];
    for (const [i, user] of csvUsers.entries()) {
        const hasLogin = checkIfEntryContainsKey(user, POSSIBLE_LOGIN_HEADERS);
        const hasRegistrationNumber = checkIfEntryContainsKey(user, POSSIBLE_REGISTRATION_NUMBER_HEADERS);
        const hasEmail = checkIfEntryContainsKey(user, POSSIBLE_EMAIL_HEADERS);

        if (!hasLogin && !hasRegistrationNumber && !hasEmail) {
            // '+ 2' instead of '+ 1' due to the header column in the csv file
            invalidList.push(i + 2);
        }
    }
    return invalidList;
}

function checkIfEntryContainsKey(entry: CsvUser, keys: string[]): boolean {
    return keys.some((key) => entry[key] !== undefined && entry[key] !== '');
}
