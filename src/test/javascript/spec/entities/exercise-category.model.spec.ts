import { ExerciseCategory } from 'app/entities/exercise-category.model';

describe('ExerciseCategory', () => {
    describe('equals', () => {
        it('should return true if the two exercise categories are equal', () => {
            const exerciseCategory1 = new ExerciseCategory('Category 1', 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 1', 'red');

            expect(exerciseCategory1.equals(exerciseCategory2)).toBeTruthy();
        });

        it('should return false if the two exercise categories are not equal', () => {
            const exerciseCategory1 = new ExerciseCategory('Category 1', 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 2', 'blue');

            expect(exerciseCategory1.equals(exerciseCategory2)).toBeFalsy();
        });
    });

    describe('compare', () => {
        it("should return 0 if the two exercise categories' display text is the same", () => {
            const exerciseCategory1 = new ExerciseCategory('Category 1', 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 1', 'blue');

            expect(exerciseCategory1.compare(exerciseCategory2)).toBe(0);
        });

        it("should return -1 if the first exercise category's display text is smaller than the second exercise category's display text", () => {
            const exerciseCategory1 = new ExerciseCategory('Category 1', 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 2', 'blue');

            expect(exerciseCategory1.compare(exerciseCategory2)).toBe(-1);
        });

        it("should return 1 if the first exercise category's display text is larger than the second exercise category's display text", () => {
            const exerciseCategory1 = new ExerciseCategory('Category 2', 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 1', 'blue');

            expect(exerciseCategory1.compare(exerciseCategory2)).toBe(1);
        });

        it('should return -1 if the first exercise category is undefined', () => {
            const exerciseCategory1 = new ExerciseCategory(undefined, 'red');
            const exerciseCategory2 = new ExerciseCategory('Category 1', 'blue');

            expect(exerciseCategory1.compare(exerciseCategory2)).toBe(-1);
        });
    });
});
