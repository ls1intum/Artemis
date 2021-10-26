import { Exercise } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

export class ExerciseFilter {
    exerciseNameSearch: string;
    exerciseCategorySearch: string;
    exerciseTypeSearch: string;

    constructor(exerciseNameSearch = '', exerciseCategorySearch = '', exerciseTypeSearch = 'all') {
        this.exerciseNameSearch = exerciseNameSearch.toLowerCase();
        this.exerciseCategorySearch = exerciseCategorySearch.toLowerCase();
        this.exerciseTypeSearch = exerciseTypeSearch;
    }

    /**
     * Check if the exercise filter is in its default state (no exercise will be filtered by it)
     */
    isEmpty(): boolean {
        return this.exerciseNameSearch === '' && this.exerciseCategorySearch === '' && this.exerciseTypeSearch === 'all';
    }

    /**
     * Checks if an exercise should be included by this filter
     * @param exercise The exercise to check
     */
    includeExercise(exercise: Exercise): boolean {
        const nameMatches: boolean = this.exerciseNameSearch === '' || exercise.title!.toLowerCase().includes(this.exerciseNameSearch);
        const categoryMatches: boolean = this.exerciseCategorySearch === '' || (exercise.categories?.some((category) => this.matchesTag(category)) ?? false);
        const typeMatches: boolean = this.exerciseTypeSearch === 'all' || exercise.type === this.exerciseTypeSearch;
        return nameMatches && categoryMatches && typeMatches;
    }

    private matchesTag(category: ExerciseCategory): boolean {
        return category.category!.toLowerCase().includes(this.exerciseCategorySearch);
    }
}
