import { ChartFilter } from 'app/shared/chart/chart-filter';
import { Exercise } from 'app/entities/exercise.model';
import { Injectable } from '@angular/core';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Injectable({ providedIn: 'root' })
export class ChartCategoryFilter extends ChartFilter {
    exerciseCategories: Set<string>;
    allCategoriesSelected = true;
    includeExercisesWithNoCategory = true;
    // TODO: Include optional filtering as well?
    // currentlyHidingNotIncludedInScoreExercises: boolean;

    /**
     * Collects all categories from the currently visible exercises (included or excluded the optional exercises depending on the prior state)
     * @private
     */
    determineDisplayableCategories(courseExercises: Exercise[]): void {
        const exerciseCategories = courseExercises
            .filter((exercise) => exercise.categories)
            .flatMap((exercise) => exercise.categories!)
            .map((category) => category.category!);
        this.exerciseCategories = new Set(exerciseCategories);
    }

    determineDisplayableCategoriesForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): void {
        console.log(avgStatistics);
        const exerciseCategories = avgStatistics
            .filter((exercise) => exercise.categories)
            .flatMap((exercise: CourseManagementStatisticsModel) => exercise.categories!)
            .map((category: ExerciseCategory) => category.category!);
        this.exerciseCategories = new Set(exerciseCategories);
    }

    setupCategoryFilter(courseExercises: Exercise[]): void {
        this.determineDisplayableCategories(courseExercises);
        this.performFilterSetup();
    }

    setupCategoryFilterForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): void {
        this.determineDisplayableCategoriesForCourseStatistics(avgStatistics);
        this.performFilterSetup();
    }

    updateCategoryFilterForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        this.determineDisplayableCategoriesForCourseStatistics(avgStatistics);
        this.filterMap.forEach((value, key) => {
            if (!this.exerciseCategories.has(key)) {
                this.filterMap.delete(key);
            }
        });
        this.exerciseCategories.forEach((category) => {
            if (this.filterMap.get(category) === undefined) {
                this.filterMap.set(category, true);
            }
        });
        // TODO: Keine Kategorien da?
        this.includeExercisesWithNoCategory = avgStatistics.some((exercise) => !exercise.categories);
        // TODO: Alle ausgewählt?
        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        this.numberOfActiveFilters = this.includeExercisesWithNoCategory ? 1 : 0;
        this.filterMap.forEach((value) => (this.numberOfActiveFilters += value ? 1 : 0));
        return this.applyCategoryFilterForCourseStatistics(avgStatistics);
    }

    calculateNumberOfAppliedFilters(): void {
        this.numberOfActiveFilters = this.exerciseCategories.size + (this.includeExercisesWithNoCategory ? 1 : 0);
    }

    /**
     * Handles the selection or deselection of a specific category and configures the filter accordingly
     * @param category the category that is selected or deselected
     */
    toggleCategory(courseExercises: Exercise[], category: string): Exercise[] {
        const isIncluded = this.filterMap.get(category)!;
        this.filterMap.set(category, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        this.areAllCategoriesSelected(!isIncluded);
        return this.applyCategoryFilter(courseExercises);
    }

    toggleCategoryForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[], category: string): CourseManagementStatisticsModel[] {
        const isIncluded = this.filterMap.get(category)!;
        this.filterMap.set(category, !isIncluded);
        this.numberOfActiveFilters += !isIncluded ? 1 : -1;
        this.areAllCategoriesSelected(!isIncluded);
        return this.applyCategoryFilterForCourseStatistics(avgStatistics);
    }

    /**
     * handles the selection and deselection of "exercises with no categories" filter option
     */
    toggleExercisesWithNoCategory(courseExercises: Exercise[]): Exercise[] {
        this.numberOfActiveFilters += this.includeExercisesWithNoCategory ? -1 : 1;
        this.includeExercisesWithNoCategory = !this.includeExercisesWithNoCategory;

        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        return this.applyCategoryFilter(courseExercises);
    }

    toggleExercisesWithNoCategoryForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        this.numberOfActiveFilters += this.includeExercisesWithNoCategory ? -1 : 1;
        this.includeExercisesWithNoCategory = !this.includeExercisesWithNoCategory;

        this.areAllCategoriesSelected(this.includeExercisesWithNoCategory);
        return this.applyCategoryFilterForCourseStatistics(avgStatistics);
    }

    /**
     * Handles the use case when the user selects or deselects the option "select all categories"
     */
    toggleAllCategories(courseExercises: Exercise[]): Exercise[] {
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

    toggleAllCategoriesForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        if (!this.allCategoriesSelected) {
            this.setupCategoryFilterForCourseStatistics(avgStatistics);
            this.includeExercisesWithNoCategory = true;
            this.calculateNumberOfAppliedFilters();
        } else {
            this.exerciseCategories.forEach((category) => this.filterMap.set(category, false));
            this.numberOfActiveFilters -= this.exerciseCategories.size + 1;
            this.allCategoriesSelected = !this.allCategoriesSelected;
            this.includeExercisesWithNoCategory = false;
        }
        return this.applyCategoryFilterForCourseStatistics(avgStatistics);
    }

    /**
     * Auxiliary method in order to reduce code duplication
     * Takes the currently configured exerciseCategoryFilters and applies it to the course exercises
     *
     * Important note: As exercises can have no or multiple categories, the filter is designed to be non-exclusive. This means
     * as long as an exercise has at least one of the selected categories, it is displayed.
     */
    applyCategoryFilter(courseExercises: Exercise[]): Exercise[] {
        return courseExercises.filter((exercise) => {
            if (!exercise.categories) {
                return this.includeExercisesWithNoCategory;
            }
            return exercise.categories!.flatMap((category) => this.filterMap.get(category.category!)!).reduce((value1, value2) => value1 || value2);
        });
    }

    applyCategoryFilterForCourseStatistics(avgStatistics: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        return avgStatistics.filter((exercise) => {
            if (!exercise.categories) {
                return this.includeExercisesWithNoCategory;
            }
            return exercise.categories!.flatMap((category) => this.filterMap.get(category.category!)!).reduce((value1, value2) => value1 || value2);
        });
    }

    /**
     * Auxiliary method that checks whether all possible categories are selected and updates the allCategoriesSelected flag accordingly
     * @param newFilterStatement indicates whether the updated filter option got selected or deselected and updates the flag accordingly
     * @private
     */
    private areAllCategoriesSelected(newFilterStatement: boolean): void {
        if (newFilterStatement) {
            if (!this.includeExercisesWithNoCategory) {
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
        this.includeExercisesWithNoCategory = true;
        this.calculateNumberOfAppliedFilters();
    }
}
