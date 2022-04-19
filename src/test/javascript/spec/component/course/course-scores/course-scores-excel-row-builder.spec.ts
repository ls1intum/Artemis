import { POINTS_KEY, SCORE_KEY } from 'app/course/course-scores/course-scores.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { CourseScoresRowBuilder } from 'app/course/course-scores/course-scores-row-builder';
import { CourseScoresExcelRowBuilder } from 'app/course/course-scores/course-scores-excel-row-builder';

describe('The CourseScoresExcelRowBuilder', () => {
    let excelRowBuilder: CourseScoresRowBuilder;

    beforeEach(() => {
        excelRowBuilder = new CourseScoresExcelRowBuilder();
    });

    it('should convert numbers to their common spreadsheet format', () => {
        excelRowBuilder.setLocalized('n', 100);
        const rowObject = excelRowBuilder.build()['n'];
        expect(rowObject['t']).toBe('n');
        expect(rowObject['v']).toBe(100);
    });

    it('should convert percentage numbers to their common spreadsheet format', () => {
        excelRowBuilder.setLocalizedPercent('n', 5);
        const rowObject = excelRowBuilder.build()['n'];
        expect(rowObject['t']).toBe('n');
        expect(rowObject['v']).toBe(0.05);
        expect(rowObject['z']).toBe('0%');
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
