import { ChartExerciseTypeFilter } from 'app/exercise/chart/chart-exercise-type-filter';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ChartExerciseTypeFilter', () => {
    setupTestBed({ zoneless: true });
    let exerciseTypeFilter: ChartExerciseTypeFilter;
    let results: any[];

    beforeEach(() => {
        exerciseTypeFilter = TestBed.inject(ChartExerciseTypeFilter);
    });

    it('should setup and execute exercise type filter correctly', () => {
        const exerciseTypes = [ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD];
        const exercises: any[] = [];
        exerciseTypes.forEach((exerciseType, index) => {
            exercises.push({ name: exerciseType + index, score: index, exerciseType });
        });

        exerciseTypeFilter.initializeFilterOptions(exercises);

        expect(exerciseTypeFilter.numberOfActiveFilters).toBe(5);

        exerciseTypes.forEach((exerciseType) => {
            expect(exerciseTypeFilter.typeSet.has(exerciseType)).toBe(true);
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBe(true);

            results = exerciseTypeFilter.toggleExerciseType(exerciseType, exercises);

            expect(exerciseTypeFilter.numberOfActiveFilters).toBe(4);
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBe(false);

            expect(results.filter((exercise) => exercise.exerciseType === exerciseType)).toHaveLength(0);

            results = exerciseTypeFilter.toggleExerciseType(exerciseType, exercises);
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBe(true);
        });
    });
});
