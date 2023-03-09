import { TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import { ChartExerciseTypeFilter } from 'app/shared/chart/chart-exercise-type-filter';

describe('ChartExerciseTypeFilter', () => {
    let exerciseTypeFilter: ChartExerciseTypeFilter;
    let results: any[];

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                exerciseTypeFilter = TestBed.inject(ChartExerciseTypeFilter);
            });
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
            expect(exerciseTypeFilter.typeSet.has(exerciseType)).toBeTrue();
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBeTrue();

            results = exerciseTypeFilter.toggleExerciseType(exerciseType, exercises);

            expect(exerciseTypeFilter.numberOfActiveFilters).toBe(4);
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBeFalse();

            expect(results.filter((exercise) => exercise.exerciseType === exerciseType)).toBeEmpty();

            results = exerciseTypeFilter.toggleExerciseType(exerciseType, exercises);
            expect(exerciseTypeFilter.filterMap.get(exerciseType)).toBeTrue();
        });
    });
});
