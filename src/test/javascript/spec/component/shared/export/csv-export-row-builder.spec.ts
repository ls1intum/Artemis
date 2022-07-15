import { CourseScoresStudentStatistics } from 'app/course/course-scores/course-scores-student-statistics';
import { User } from 'app/core/user/user.model';
import { EMAIL_KEY, NAME_KEY, POINTS_KEY, REGISTRATION_NUMBER_KEY, SCORE_KEY, USERNAME_KEY } from 'app/shared/export/export-constants';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExportRowBuilder } from 'app/shared/export/export-row-builder';
import { CsvExportRowBuilder } from 'app/shared/export/csv-export-row-builder';
import { CsvDecimalSeparator } from 'app/shared/export/export-modal.component';

describe('The CsvExportRowBuilder', () => {
    let csvRow: ExportRowBuilder;

    beforeEach(() => {
        csvRow = new CsvExportRowBuilder(CsvDecimalSeparator.PERIOD);
    });

    it('should set a string', () => {
        const value = 'some value';
        csvRow.set('a', value);
        expect(csvRow.build()['a']).toBe(value);
    });

    it('should set an empty string instead of undefined', () => {
        csvRow.set('a', undefined);
        expect(csvRow.build()['a']).toBe('');
    });

    it('should convert numbers to their localized format', () => {
        csvRow.setPoints('n', 100);
        expect(csvRow.build()['n']).toBe('100');
        csvRow.setPoints('n', 25.5);
        expect(csvRow.build()['n']).toBe('25.5');
        csvRow.setPoints('n', 1000.23);
        expect(csvRow.build()['n']).toBe('1000.2');
    });

    it('should convert percentage numbers to their localized format', () => {
        csvRow.setScore('n', 5);
        expect(csvRow.build()['n']).toBe('5%');
        csvRow.setScore('n', 5.5);
        expect(csvRow.build()['n']).toBe('5.5%');
    });

    it('should return a hyphen for NaN values', () => {
        csvRow.setPoints('n', NaN);
        expect(csvRow.build()['n']).toBe('-');
        csvRow.setScore('p', NaN);
        expect(csvRow.build()['p']).toBe('-');
    });

    describe('Test the CsvExportRowBuilder with a comma as a decimal separator', () => {
        beforeEach(() => {
            csvRow = new CsvExportRowBuilder(CsvDecimalSeparator.COMMA);
        });

        it('should convert numbers to their localized format', () => {
            csvRow.setPoints('n', 100);
            expect(csvRow.build()['n']).toBe('100');
            csvRow.setPoints('n', 25.5);
            expect(csvRow.build()['n']).toBe('25,5');
            csvRow.setPoints('n', 1000.23);
            expect(csvRow.build()['n']).toBe('1000,2');
        });

        it('should convert percentage numbers to their localized format', () => {
            csvRow.setScore('n', 5);
            expect(csvRow.build()['n']).toBe('5%');
            csvRow.setScore('n', 5.5);
            expect(csvRow.build()['n']).toBe('5,5%');
        });
    });

    describe('Test the CsvExportRowBuilder with a specific accuracyOfScores', () => {
        beforeEach(() => {
            csvRow = new CsvExportRowBuilder(CsvDecimalSeparator.PERIOD, 3);
        });

        it('should convert numbers to their localized format respecting the accuracyOfScores', () => {
            csvRow.setPoints('n', 100.12345);
            expect(csvRow.build()['n']).toBe('100.123');
            csvRow.setPoints('n', 99.9999);
            expect(csvRow.build()['n']).toBe('100');
            csvRow.setPoints('n', 25.5678);
            expect(csvRow.build()['n']).toBe('25.568');
            csvRow.setPoints('n', 1000.2345);
            expect(csvRow.build()['n']).toBe('1000.235');
        });

        it('should convert percentage numbers to their localized format', () => {
            csvRow.setScore('n', 5.12345);
            expect(csvRow.build()['n']).toBe('5.123%');
            csvRow.setScore('n', 99.9999);
            expect(csvRow.build()['n']).toBe('100%');
            csvRow.setScore('n', 51.9999);
            expect(csvRow.build()['n']).toBe('52%');
            csvRow.setScore('n', 25.5678);
            expect(csvRow.build()['n']).toBe('25.568%');
        });
    });

    it('should trim all user values when storing them', () => {
        const user = new User();
        user.name = 'Testuser ';
        user.login = ' login ';
        user.email = 'mail@example.com ';
        user.visibleRegistrationNumber = ' 123456789  ';
        const student = new CourseScoresStudentStatistics(user);

        csvRow.setUserInformation(student.user.name, student.user.login, student.user.email, student.user.visibleRegistrationNumber);

        const row = csvRow.build();
        expect(row[NAME_KEY]).toBe('Testuser');
        expect(row[USERNAME_KEY]).toBe('login');
        expect(row[EMAIL_KEY]).toBe('mail@example.com');
        expect(row[REGISTRATION_NUMBER_KEY]).toBe('123456789');
    });

    it('should allow for empty student information', () => {
        const user = new User();
        const student = new CourseScoresStudentStatistics(user);

        csvRow.setUserInformation(student.user.name, student.user.login, student.user.email, student.user.visibleRegistrationNumber);

        const row = csvRow.build();
        expect(row[NAME_KEY]).toBe('');
        expect(row[USERNAME_KEY]).toBe('');
        expect(row[EMAIL_KEY]).toBe('');
        expect(row[REGISTRATION_NUMBER_KEY]).toBe('');
    });

    it('should set the exercise type points', () => {
        const exerciseType = ExerciseType.PROGRAMMING;
        const key = `Programming ${POINTS_KEY}`;

        csvRow.setExerciseTypePoints(exerciseType, 100);
        expect(csvRow.build()[key]).toBe('100');

        // should take the value as is if it is a string
        csvRow.setExerciseTypePoints(exerciseType, '');
        expect(csvRow.build()[key]).toBe('');
    });

    it('should set the exercise type score', () => {
        const exerciseType = ExerciseType.PROGRAMMING;
        const key = `Programming ${SCORE_KEY}`;

        csvRow.setExerciseTypeScore(exerciseType, 100);
        expect(csvRow.build()[key]).toBe('100%');

        // should take the value as is if it is a string
        csvRow.setExerciseTypeScore(exerciseType, '');
        expect(csvRow.build()[key]).toBe('');
    });
});
