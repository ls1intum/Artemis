import { ChartFilter } from 'app/shared/chart/chart-filter';
import { Injectable } from '@angular/core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Injectable({ providedIn: 'root' })
export class ChartCategoryFilter extends ChartFilter {
    exerciseCategories: Set<string>;
    allCategoriesSelected = true;
    includeExercisesWithNoCategory = true;
    exercisesWithoutCategoriesPresent: boolean;

    /**
     * Collects all categories from the provided exercises
     * @private
     */
    determineDisplayableCategories(courseExercises: any[]): Set<any> {
        const exerciseCategories = courseExercises
            .filter((exercise) => exercise.categories)
            .flatMap((exercise) => exercise.categories!)
            .map((category) => category.category!);
        return new Set(exerciseCategories);
    }

    /**
     * Creates an initial filter setting by including all categories
     * @param exercisesScores the score objects the categories are derived from
     */
    setupCategoryFilter(exercisesScores: any[]): void {
        this.exerciseCategories = this.determineDisplayableCategories(exercisesScores);
        this.exercisesWithoutCategoriesPresent = exercisesScores.some((exercises) => !exercises.categories);
        this.exerciseCategories.forEach((category) => this.filterMap.set(category, true));
        this.allCategoriesSelected = true;
        this.includeExercisesWithNoCategory = this.exercisesWithoutCategoriesPresent;
        this.calculateNumberOfAppliedFilters();
    }

    /**
     * Calculates the current amount of active category filter options
     * @private
     */
    private calculateNumberOfAppliedFilters(): void {
        this.numberOfActiveFilters = this.exerciseCategories.size + (this.includeExercisesWithNoCategory ? 1 : 0);
    }

    /**
     * Handles the selection or deselection of a specific category and configures the filter accordingly
     * @param exercisesScores the scores the updated filter should apply to
     * @param category the category that is selected or deselected
     * @returns scores filtered against the updated filter setting
     */
    toggleCategory(exercisesScores: any[], category: string): any[] {
        const isIncluded = this.filterMap.get(category)!;
        this.filterMap.set(category, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        this.areAllCategoriesSelected(!isIncluded);
        return this.applyCurrentFilter(exercisesScores);
    }
    /**
     * Handles the selection and deselection of "exercises with no categories" filter option
     * @param exerciseScores the scores the updated filter should apply to
     * @returns scores filtered against the updated filter setting
     */
    toggleExercisesWithNoCategory(exerciseScores: any[]): any[] {
        this.numberOfActiveFilters += this.includeExercisesWithNoCategory ? -1 : 1;
        this.includeExercisesWithNoCategory = !this.includeExercisesWithNoCategory;
        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        return this.applyCurrentFilter(exerciseScores);
    }

    /**
     * Handles the use case when the user selects or deselects the option "select all categories"
     * @param exerciseScores the scores the updated filter should apply to
     * @returns scores filtered against the updated filter setting
     */
    toggleAllCategories(exerciseScores: any[]): any[] {
        if (!this.allCategoriesSelected) {
            this.setupCategoryFilter(exerciseScores);
            this.includeExercisesWithNoCategory = true;
            this.calculateNumberOfAppliedFilters();
        } else {
            this.exerciseCategories.forEach((category) => this.filterMap.set(category, false));
            this.numberOfActiveFilters -= this.exerciseCategories.size + 1;
            this.allCategoriesSelected = !this.allCategoriesSelected;
            this.includeExercisesWithNoCategory = false;
        }
        return this.applyCurrentFilter(exerciseScores);
    }

    /**
     * Auxiliary method in order to reduce code duplication
     * Takes the currently configured exerciseCategoryFilters and applies it to the exerciseScores
     *
     * Important note: As exercises can have no or multiple categories, the filter is designed to be non-exclusive. This means
     * as long as an exercise has at least one of the selected categories, it is displayed.
     * @param exerciseScores the exercise scores the current filter setting should be applied to
     * @returns scores filtered against the updated filter setting
     */
    applyCurrentFilter(exerciseScores: any[]): any[] {
        return exerciseScores.filter((exercise) => {
            if (!exercise.categories) {
                return this.includeExercisesWithNoCategory;
            }
            return exercise
                .categories!.flatMap((category: ExerciseCategory) => this.filterMap.get(category.category!)!)
                .reduce((value1: boolean, value2: boolean) => value1 || value2);
        });
    }

    /**
     * Auxiliary method that checks whether all possible categories are selected and updates the allCategoriesSelected flag accordingly
     * @param newFilterStatement indicates whether the updated filter option got selected or deselected and updates the flag accordingly
     * @private
     */
    private areAllCategoriesSelected(newFilterStatement: boolean): void {
        if (newFilterStatement) {
            if (this.exercisesWithoutCategoriesPresent && !this.includeExercisesWithNoCategory) {
                this.allCategoriesSelected = false;
            } else {
                this.allCategoriesSelected = true;
                this.filterMap.forEach((value) => (this.allCategoriesSelected = value && this.allCategoriesSelected));
            }
        } else {
            this.allCategoriesSelected = false;
        }
    }
}
