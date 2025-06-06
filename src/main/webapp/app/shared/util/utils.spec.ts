import {
    average,
    getAsMutableObject,
    isExamExercise,
    removeSpecialCharacters,
    round,
    roundScorePercentSpecifiedByCourseSettings,
    roundToNextMultiple,
    roundValueSpecifiedByCourseSettings,
    stringifyIgnoringFields,
} from 'app/shared/util/utils';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Range } from 'app/shared/util/utils';

describe('Round', () => {
    it('Decimal length', () => {
        expect(round(14.821354, 4)).toBe(14.8214);
        expect(round(14.821354, 3)).toBe(14.821);
        expect(round(14.821354, 2)).toBe(14.82);
        expect(round(14.821354, 1)).toBe(14.8);
        expect(round(14.821354, 0)).toBe(15);
    });

    it('Turning points', () => {
        expect(round(2.4999999, 0)).toBe(2);
        expect(round(2.5, 0)).toBe(3);
        expect(round(2.55, 1)).toBe(2.6);
        expect(round(2.555, 2)).toBe(2.56);
        expect(round(2.5555, 3)).toBe(2.556);
        expect(round(2.55555, 4)).toBe(2.5556);
    });

    it('Other', () => {
        expect(round(9.99999999, 0)).toBe(10);
        expect(round(9.99999999, 1)).toBe(10);
        expect(round(5.55555555, 0)).toBe(6);
        expect(round(5.55555555, 1)).toBe(5.6);
        expect(round(1.00000001, 0)).toBe(1);
        expect(round(1.00000001, 1)).toBe(1.0);
    });

    it('should return NaN', () => {
        expect(round(Number.NaN, 2)).toBeNaN();
        expect(round(Number.NaN, 1)).toBeNaN();
        expect(round(9.9999, 0.5)).toBeNaN();
    });
});

describe('Rounding of scores', () => {
    it('RoundScore', () => {
        expect(roundValueSpecifiedByCourseSettings(13.821354, { accuracyOfScores: 4 })).toBe(13.8214);
        expect(roundValueSpecifiedByCourseSettings(54.821354, { accuracyOfScores: 3 })).toBe(54.821);
        expect(roundValueSpecifiedByCourseSettings(0.821354, { accuracyOfScores: 2 })).toBe(0.82);
        expect(roundValueSpecifiedByCourseSettings(1000.821354, { accuracyOfScores: 1 })).toBe(1000.8);
        expect(roundValueSpecifiedByCourseSettings(4.821354, { accuracyOfScores: 0 })).toBe(5);
    });

    it('RoundScorePercent', () => {
        expect(roundScorePercentSpecifiedByCourseSettings(0, { accuracyOfScores: 4 })).toBe(0);
        expect(roundScorePercentSpecifiedByCourseSettings(0.222222, { accuracyOfScores: 3 })).toBe(22.222);
        expect(roundScorePercentSpecifiedByCourseSettings(0.5, { accuracyOfScores: 2 })).toBe(50);
        expect(roundScorePercentSpecifiedByCourseSettings(0.7999999, { accuracyOfScores: 1 })).toBe(80);
        expect(roundScorePercentSpecifiedByCourseSettings(1, { accuracyOfScores: 0 })).toBe(100);
    });
});

describe('stringifyIgnoringFields', () => {
    it('should ignore nothing', () => {
        expect(stringifyIgnoringFields({})).toBe(JSON.stringify({}));
        expect(stringifyIgnoringFields({}, 'a', 'b')).toBe(JSON.stringify({}));
        expect(stringifyIgnoringFields({ a: 'a' }, 'b')).toBe(JSON.stringify({ a: 'a' }));
        expect(stringifyIgnoringFields({ a: 'a' })).toBe(JSON.stringify({ a: 'a' }));
    });

    it('should ignore fields', () => {
        expect(stringifyIgnoringFields({ a: 1 }, 'a')).toBe(JSON.stringify({}));
        expect(stringifyIgnoringFields({ a: 1, c: 2, b: 3 }, 'a', 'b')).toBe(JSON.stringify({ c: 2 }));
        expect(stringifyIgnoringFields({ b: 1, c: 3 }, 'c', 'b')).toBe(JSON.stringify({}));
    });
});

describe('average', () => {
    it('should return an average of 0 for an empty array', () => {
        expect(average([])).toBe(0);
    });

    it('should return the average for a non-empty array', () => {
        expect(average([10])).toBe(10);
        expect(average([1, 3])).toBe(2);
        expect(average([1, 5, 4, 8])).toBe(4.5);
    });
});

describe('Range', () => {
    it('should return the correct string representation', () => {
        const range = new Range(10, 50);
        expect(range.toString()).toBe('[10%, 50%)');

        const rangeInclusive = new Range(0, 100);
        expect(rangeInclusive.toString()).toBe('[0%, 100%]');
    });
});

describe('getAsMutableObject', () => {
    it('should return immutable object as mutable object', () => {
        const immutableObject = Object.freeze({
            name: 'Jane',
            familyName: 'Doe',
            age: 26,
        });

        const mutableObject = getAsMutableObject(immutableObject);
        mutableObject.name = 'Kelly'; // would throw an error if the object was not mutable
        mutableObject.familyName = 'Wilkins';
        mutableObject.age = 42;
    });
});

describe('isExamExercise', () => {
    it('should return true if course is not set', () => {
        const examExercise = { course: undefined } as Exercise;

        const isExamExerciseResult = isExamExercise(examExercise);
        expect(isExamExerciseResult).toBeTrue();
    });

    it('should return false if course is set', () => {
        const courseExercise = { course: new Course() } as Exercise;

        const isExamExerciseResult = isExamExercise(courseExercise);
        expect(isExamExerciseResult).toBeFalse();
    });
});

describe('roundUpToNextMultiple', () => {
    it('should round up to multiple of 5 if value is closer to lower multiple', () => {
        expect(roundToNextMultiple(21, 5, true)).toBe(25);
    });

    it('should round up to multiple of 5 if value is right underneath next multiple', () => {
        expect(roundToNextMultiple(24.8, 5, true)).toBe(25);
    });

    it('should round down to multiple of 5 if value is over next multiple', () => {
        expect(roundToNextMultiple(24.8, 5, false)).toBe(20);
    });

    it('should return value is it is a multiple', () => {
        expect(roundToNextMultiple(25, 5, true)).toBe(25);
    });

    it('should round up to multiple of 1', () => {
        expect(roundToNextMultiple(8.2, 1, true)).toBe(9);
    });
});

describe('removeSpecialCharacters', () => {
    it('should remove special characters', () => {
        expect(removeSpecialCharacters('Hello, World!')).toBe('HelloWorld');
        expect(removeSpecialCharacters('Hello, World! 123')).toBe('HelloWorld123');
        expect(removeSpecialCharacters('Hello, World! 123$%')).toBe('HelloWorld123');
    });
});
