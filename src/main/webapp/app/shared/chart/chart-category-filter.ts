import { ChartFilter } from 'app/shared/chart/chart-filter';
import { Injectable } from '@angular/core';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Injectable({ providedIn: 'root' })
export class ChartCategoryFilter extends ChartFilter {
    exerciseCategories: Set<string>;
    allCategoriesSelected = true;
    includeExercisesWithNoCategory = true;
    exercisesWithoutCategoriesPresent: boolean;

    /**
     * Collects all categories from the currently visible exercises (included or excluded the optional exercises depending on the prior state)
     * @private
     */
    determineDisplayableCategories(courseExercises: any[]): Set<any> {
        const exerciseCategories = courseExercises
            .filter((exercise) => exercise.categories)
            .flatMap((exercise) => exercise.categories!)
            .map((category) => category.category!);
        return new Set(exerciseCategories);
    }

    setupCategoryFilter(courseExercises: any[]): void {
        this.exerciseCategories = this.determineDisplayableCategories(courseExercises);
        this.exercisesWithoutCategoriesPresent = courseExercises.some((exercises) => !exercises.categories);
        this.performFilterSetup();
    }

    updateCategoryFilterForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        const updatedSet = this.determineDisplayableCategories(avgStatistics);
        this.filterMap.forEach((value, key) => {
            if (!updatedSet.has(key)) {
                this.filterMap.set(key, false);
            }
        });
        if (!avgStatistics.length || avgStatistics.every((exercise) => !!exercise.categories)) {
            this.includeExercisesWithNoCategory = false;
        }
        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        this.numberOfActiveFilters = this.includeExercisesWithNoCategory ? 1 : 0;
        this.filterMap.forEach((value) => (this.numberOfActiveFilters += value ? 1 : 0));
        return this.applyCategoryFilter(avgStatistics);
    }

    calculateNumberOfAppliedFilters(): void {
        this.numberOfActiveFilters = this.exerciseCategories.size + (this.includeExercisesWithNoCategory ? 1 : 0);
    }

    /**
     * Handles the selection or deselection of a specific category and configures the filter accordingly
     * @param category the category that is selected or deselected
     */
    toggleCategory(courseExercises: any[], category: string): any[] {
        const isIncluded = this.filterMap.get(category)!;
        this.filterMap.set(category, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        this.areAllCategoriesSelected(!isIncluded);
        return this.applyCategoryFilter(courseExercises);
    }
    /**
     * handles the selection and deselection of "exercises with no categories" filter option
     */
    toggleExercisesWithNoCategory(courseExercises: any[]): any[] {
        this.numberOfActiveFilters += this.includeExercisesWithNoCategory ? -1 : 1;
        this.includeExercisesWithNoCategory = !this.includeExercisesWithNoCategory;

        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        return this.applyCategoryFilter(courseExercises);
    }

    /**
     * Handles the use case when the user selects or deselects the option "select all categories"
     */
    toggleAllCategories(courseExercises: any[]): any[] {
        if (!this.allCategoriesSelected) {
            this.setupCategoryFilter(courseExercises);
            this.includeExercisesWithNoCategory = true;
            this.calculateNumberOfAppliedFilters();
        } else {
            this.exerciseCategories.forEach((category) => this.filterMap.set(category, false));
            this.numberOfActiveFilters -= this.exerciseCategories.size + 1;
            this.allCategoriesSelected = !this.allCategoriesSelected;
            this.includeExercisesWithNoCategory = false;
        }
        return this.applyCategoryFilter(courseExercises);
    }

    /**
     * Auxiliary method in order to reduce code duplication
     * Takes the currently configured exerciseCategoryFilters and applies it to the course exercises
     *
     * Important note: As exercises can have no or multiple categories, the filter is designed to be non-exclusive. This means
     * as long as an exercise has at least one of the selected categories, it is displayed.
     */
    applyCategoryFilter(courseExercises: any[]): any[] {
        return courseExercises.filter((exercise) => {
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

    private performFilterSetup(): void {
        this.exerciseCategories.forEach((category) => this.filterMap.set(category, true));
        this.allCategoriesSelected = true;
        this.includeExercisesWithNoCategory = this.exercisesWithoutCategoriesPresent;
        this.calculateNumberOfAppliedFilters();
    }
}
