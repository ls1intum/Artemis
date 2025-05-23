import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('Exercise Filter Test', () => {
    const category1 = new ExerciseCategory('Easy', undefined);
    const category2 = new ExerciseCategory('Hard', undefined);
    const course: Course = { id: 123 } as Course;
    const exercise1 = new ProgrammingExercise(course, undefined);
    exercise1.id = 1;
    exercise1.title = 'Test Exercise 1';
    exercise1.categories = [category2];
    const exercise2 = new TextExercise(course, undefined);
    exercise2.id = 2;
    exercise2.title = 'Test Exercise 2a';
    exercise2.categories = [category2];
    const exercise3 = new TextExercise(course, undefined);
    exercise3.id = 3;
    exercise3.title = 'Test Exercise 2b';
    exercise3.categories = [category1];
    let exercises: Exercise[] = [];

    beforeEach(() => {
        exercises = [exercise1, exercise2, exercise3];
    });

    it('should be empty on create', () => {
        const filter = new ExerciseFilter();
        expect(filter.isEmpty()).toBeTrue();
    });

    it('should filter by name', () => {
        const filter = new ExerciseFilter();
        filter.exerciseNameSearch = '2';
        const filteredExercises = exercises.filter((exercise) => filter.matchesExercise(exercise));
        expect(filteredExercises).toHaveLength(2);
    });

    it('should filter by category', () => {
        const filter = new ExerciseFilter();
        filter.exerciseCategorySearch = 'easy';
        const filteredExercises = exercises.filter((exercise) => filter.matchesExercise(exercise));
        expect(filteredExercises).toHaveLength(1);
    });

    it('should filter by type', () => {
        const filter = new ExerciseFilter();
        filter.exerciseTypeSearch = 'text';
        const filteredExercises = exercises.filter((exercise) => filter.matchesExercise(exercise));
        expect(filteredExercises).toHaveLength(2);
    });

    it('should filter by all', () => {
        const filter = new ExerciseFilter();
        filter.exerciseNameSearch = 'a';
        filter.exerciseCategorySearch = 'hard';
        filter.exerciseTypeSearch = 'text';
        const filteredExercises = exercises.filter((exercise) => filter.matchesExercise(exercise));
        expect(filteredExercises).toHaveLength(1);
    });
});
