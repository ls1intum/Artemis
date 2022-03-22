import { CourseScoresCsvRowBuilder } from 'app/course/course-scores/course-scores-csv-row-builder';
import { CourseScoresStudentStatistics } from 'app/course/course-scores/course-scores-student-statistics';
import { User } from 'app/core/user/user.model';
import { EMAIL_KEY, NAME_KEY, POINTS_KEY, REGISTRATION_NUMBER_KEY, SCORE_KEY, USERNAME_KEY } from 'app/course/course-scores/course-scores.component';
import { ExerciseType } from 'app/entities/exercise.model';

describe('The CourseScoresCsvRowBuilder', () => {
    const localizer = (value: number): string => `${value}l`;
    const percentageLocalizer = (value: number): string => `${value}%`;

    let csvRow: CourseScoresCsvRowBuilder;

    beforeEach(() => {
        csvRow = new CourseScoresCsvRowBuilder(localizer, percentageLocalizer);
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
        csvRow.setLocalized('n', 100);
        expect(csvRow.build()['n']).toBe('100l');
    });

    it('should convert percentage numbers to their localized format', () => {
        csvRow.setLocalizedPercent('n', 5);
        expect(csvRow.build()['n']).toBe('5%');
    });

    it('should trim all user values when storing them', () => {
        const user = new User();
        user.name = 'Testuser ';
        user.login = ' login ';
        user.email = 'mail@example.com ';
        user.registrationNumber = ' 123456789  ';
        const student = new CourseScoresStudentStatistics(user);

        csvRow.setUserInformation(student);

        const row = csvRow.build();
        expect(row[NAME_KEY]).toBe('Testuser');
        expect(row[USERNAME_KEY]).toBe('login');
        expect(row[EMAIL_KEY]).toBe('mail@example.com');
        expect(row[REGISTRATION_NUMBER_KEY]).toBe('123456789');
    });

    it('should allow for empty student information', () => {
        const user = new User();
        const student = new CourseScoresStudentStatistics(user);

        csvRow.setUserInformation(student);

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
        expect(csvRow.build()[key]).toBe('100l');

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
