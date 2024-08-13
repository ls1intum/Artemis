import { SidebarCardElement } from 'app/types/sidebar';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { FilterDetails, RangeFilter } from 'app/types/exercise-filter';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';

export function satisfiesDifficultyFilter(sidebarElement: SidebarCardElement, searchedDifficulties?: DifficultyLevel[]): boolean {
    if (!searchedDifficulties?.length) {
        return true;
    }
    if (!sidebarElement.difficulty) {
        return false;
    }

    return searchedDifficulties.includes(sidebarElement.difficulty);
}

export function satisfiesTypeFilter(sidebarElement: SidebarCardElement, searchedTypes?: ExerciseType[]): boolean {
    if (!searchedTypes?.length) {
        return true;
    }
    if (!sidebarElement.exercise?.type) {
        return false;
    }

    return searchedTypes.includes(sidebarElement.exercise.type);
}

export function satisfiesCategoryFilter(sidebarElement: SidebarCardElement, selectedCategories: ExerciseCategory[]): boolean {
    if (!selectedCategories.length) {
        return true;
    }
    if (!sidebarElement?.exercise?.categories) {
        return false;
    }

    // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
    const isAnyExerciseCategoryMatchingASelectedCategory = sidebarElement.exercise.categories.some((category) =>
        selectedCategories.some((selectedCategory) => selectedCategory.equals(category)),
    );
    return isAnyExerciseCategoryMatchingASelectedCategory;
}

export function satisfiesScoreFilter(sidebarElement: SidebarCardElement, isFilterApplied: boolean, achievedScoreFilter?: RangeFilter): boolean {
    if (!isFilterApplied || !achievedScoreFilter) {
        return true;
    }

    const latestResult = getLatestResultOfStudentParticipation(sidebarElement.studentParticipation, true);
    if (!latestResult?.score) {
        return achievedScoreFilter.filter.selectedMin === 0;
    }

    const isScoreInSelectedMinRange = latestResult.score >= achievedScoreFilter.filter.selectedMin;
    const isScoreInSelectedMaxRange = latestResult.score <= achievedScoreFilter.filter.selectedMax;

    return isScoreInSelectedMinRange && isScoreInSelectedMaxRange;
}

export function satisfiesPointsFilter(sidebarElement: SidebarCardElement, isPointsFilterApplied: boolean, achievablePointsFilter?: RangeFilter): boolean {
    if (!isPointsFilterApplied || !achievablePointsFilter) {
        return true;
    }

    /** {@link Exercise.maxPoints} must be in the range 1 - 9999 */
    if (!sidebarElement.exercise?.maxPoints) {
        return false;
    }

    const isAchievablePointsInSelectedMinRange = sidebarElement.exercise.maxPoints >= achievablePointsFilter.filter.selectedMin;
    const isAchievablePointsInSelectedMaxRange = sidebarElement.exercise.maxPoints <= achievablePointsFilter.filter.selectedMax;

    return isAchievablePointsInSelectedMinRange && isAchievablePointsInSelectedMaxRange;
}

export function satisfiesFilters(sidebarElement: SidebarCardElement, filterDetails: FilterDetails) {
    return (
        satisfiesCategoryFilter(sidebarElement, filterDetails.selectedCategories) &&
        satisfiesDifficultyFilter(sidebarElement, filterDetails.searchedDifficulties) &&
        satisfiesTypeFilter(sidebarElement, filterDetails.searchedTypes) &&
        satisfiesScoreFilter(sidebarElement, filterDetails.isScoreFilterApplied, filterDetails.achievedScore) &&
        satisfiesPointsFilter(sidebarElement, filterDetails.isPointsFilterApplied, filterDetails.achievablePoints)
    );
}
