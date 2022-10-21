import { POINTS_KEY, SCORE_KEY } from 'app/shared/export/export-constants';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExportRowBuilder } from 'app/shared/export/export-row-builder';
import { ExcelExportRowBuilder } from 'app/shared/export/excel-export-row-builder';

describe('The ExcelExportRowBuilder', () => {
    let excelRowBuilder: ExportRowBuilder;

    beforeEach(() => {
        excelRowBuilder = new ExcelExportRowBuilder();
    });

    it('should convert numbers to their common spreadsheet format', () => {
        excelRowBuilder.setPoints('n', 100);
        const rowObject = excelRowBuilder.build()['n'];
        expect(rowObject['t']).toBe('n');
        expect(rowObject['v']).toBe(100);
    });

    it('should convert percentage numbers to their common spreadsheet format', () => {
        excelRowBuilder.setScore('n', 5);
        const rowObject = excelRowBuilder.build()['n'];
        expect(rowObject['t']).toBe('n');
        expect(rowObject['v']).toBe(0.05);
        expect(rowObject['z']).toBe('0%');
    });

    it('should return a hyphen for NaN values', () => {
        excelRowBuilder.setPoints('n', NaN);
        expect(excelRowBuilder.build()['n']).toBe('-');
        excelRowBuilder.setScore('p', NaN);
        expect(excelRowBuilder.build()['p']).toBe('-');
    });

    describe('Test the ExcelExportRowBuilder with a specific accuracyOfScores', () => {
        beforeEach(() => {
            excelRowBuilder = new ExcelExportRowBuilder(3);
        });

        it('should convert numbers to their localized format respecting the accuracyOfScores', () => {
            excelRowBuilder.setPoints('n', 100.12345);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(100.123);

            excelRowBuilder.setPoints('n', 99.9999);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(100);

            excelRowBuilder.setPoints('n', 25.5678);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(25.568);

            excelRowBuilder.setPoints('n', 1000.2345);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(1000.235);
        });

        it('should convert percentage numbers to their localized format', () => {
            excelRowBuilder.setScore('n', 5.12345);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(0.05123);
            expect(excelRowBuilder.build()['n']['z']).toBe('0.000%');

            excelRowBuilder.setScore('n', 99.9999);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(1);
            expect(excelRowBuilder.build()['n']['z']).toBe('0%');

            excelRowBuilder.setScore('n', 51.9999);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(0.52);
            expect(excelRowBuilder.build()['n']['z']).toBe('0%');

            excelRowBuilder.setScore('n', 25.5678);
            expect(excelRowBuilder.build()['n']['t']).toBe('n');
            expect(excelRowBuilder.build()['n']['v']).toBe(0.25568);
            expect(excelRowBuilder.build()['n']['z']).toBe('0.000%');
        });
    });

    it('should set the exercise type points', () => {
        const exerciseType = ExerciseType.PROGRAMMING;
        const key = `Programming ${POINTS_KEY}`;

        excelRowBuilder.setExerciseTypePoints(exerciseType, 100);
        expect(excelRowBuilder.build()[key]['t']).toBe('n');
        expect(excelRowBuilder.build()[key]['v']).toBe(100);

        // should take the value as is if it is a string
        excelRowBuilder.setExerciseTypePoints(exerciseType, '');
        expect(excelRowBuilder.build()[key]).toBe('');
    });

    it('should set the exercise type score', () => {
        const exerciseType = ExerciseType.PROGRAMMING;
        const key = `Programming ${SCORE_KEY}`;

        excelRowBuilder.setExerciseTypeScore(exerciseType, 100);
        expect(excelRowBuilder.build()[key]['t']).toBe('n');
        expect(excelRowBuilder.build()[key]['v']).toBe(1);
        expect(excelRowBuilder.build()[key]['z']).toBe('0%');

        // should take the value as is if it is a string
        excelRowBuilder.setExerciseTypeScore(exerciseType, '');
        expect(excelRowBuilder.build()[key]).toBe('');
    });
});
