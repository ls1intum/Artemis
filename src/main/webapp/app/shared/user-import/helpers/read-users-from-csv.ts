import { parse } from 'papaparse';
import { cleanString } from 'app/shared/util/utils';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { ExamUserDTO } from 'app/exam/shared/entities/exam-user-dto.model';

export type StudentParsingResult = { ok: true; students: StudentDTO[] } | { ok: false; invalidRowIndices: number[] };
export type ExamUserParsingResult = { ok: true; examUsers: ExamUserDTO[] } | { ok: false; invalidRowIndices: number[] };
interface CsvUser {
    [key: string]: string;
}

const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername'];
const POSSIBLE_EMAIL_HEADERS = ['email', 'e-mail', 'mail'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];
const POSSIBLE_ROOM_HEADERS = ['actualroom', 'actualRoom', 'raum', 'room', 'Room'];
const POSSIBLE_SEAT_HEADERS = ['actualseat', 'actualSeat', 'sitzplatz', 'sitz', 'seat', 'Seat'];

export async function readStudentDTOsFromCSVFile(csvFile: File): Promise<StudentParsingResult> {
    const csvUsers: CsvUser[] = await parseCsvUserFromFile(csvFile);

    const invalidRowIndices: number[] = computeInvalidRowIndices(csvUsers);
    if (invalidRowIndices.length > 0) {
        return {
            ok: false,
            invalidRowIndices: invalidRowIndices,
        };
    }

    const usedHeaders = Object.keys(csvUsers.first() || []);
    const registrationNumberHeader = usedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
    const loginHeader = usedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
    const emailHeader = usedHeaders.find((value) => POSSIBLE_EMAIL_HEADERS.includes(value)) || '';
    const firstNameHeader = usedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
    const lastNameHeader = usedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';

    const students = csvUsers.map((user: CsvUser) => {
        return {
            registrationNumber: user[registrationNumberHeader]?.trim() || '',
            login: user[loginHeader]?.trim() || '',
            email: user[emailHeader]?.trim() || '',
            firstName: user[firstNameHeader]?.trim() || '',
            lastName: user[lastNameHeader]?.trim() || '',
        };
    });
    return {
        ok: true,
        students: students,
    };
}

export async function readExamUserDTOsFromCSVFile(csvFile: File): Promise<ExamUserParsingResult> {
    const csvUsers: CsvUser[] = await parseCsvUserFromFile(csvFile);

    const invalidRowIndices = computeInvalidRowIndices(csvUsers);
    if (invalidRowIndices.length > 0) {
        return {
            ok: false,
            invalidRowIndices: invalidRowIndices,
        };
    }

    const usedHeaders = Object.keys(csvUsers.first() || []);
    const registrationNumberHeader = usedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
    const loginHeader = usedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
    const emailHeader = usedHeaders.find((value) => POSSIBLE_EMAIL_HEADERS.includes(value)) || '';
    const firstNameHeader = usedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
    const lastNameHeader = usedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';
    const roomHeader = usedHeaders.find((value) => POSSIBLE_ROOM_HEADERS.includes(value)) || '';
    const seatHeader = usedHeaders.find((value) => POSSIBLE_SEAT_HEADERS.includes(value)) || '';

    const students = csvUsers.map((user: CsvUser) => {
        return {
            registrationNumber: user[registrationNumberHeader]?.trim() || '',
            login: user[loginHeader]?.trim() || '',
            email: user[emailHeader]?.trim() || '',
            firstName: user[firstNameHeader]?.trim() || '',
            lastName: user[lastNameHeader]?.trim() || '',
            room: user[roomHeader]?.trim() || '',
            seat: user[seatHeader]?.trim() || '',
        };
    });
    return {
        ok: true,
        examUsers: students,
    };
}

function parseCsvUserFromFile(csvFile: File): Promise<CsvUser[]> {
    return new Promise((resolve, reject) => {
        parse<CsvUser>(csvFile, {
            header: true,
            transformHeader: (header: string) => cleanString(header),
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
