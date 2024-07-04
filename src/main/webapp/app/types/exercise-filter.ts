import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { SidebarData } from 'app/types/sidebar';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export type FilterOption<T> = { isDisplayed: boolean; options: T[] };
export type ExerciseCategoryFilterOption = { category: ExerciseCategory; searched: boolean };
export type ExerciseTypeFilterOption = { name: string; value: ExerciseType; checked: boolean; icon: IconProp };
export type DifficultyFilterOption = { name: string; value: DifficultyLevel; checked: boolean };

export type RangeFilter = {
    isDisplayed: boolean;
    filter: {
        generalMin: number;
        generalMax: number;
        selectedMin: number;
        selectedMax: number;
        step: number;
    };
};

export type ExerciseFilterOptions = {
    categoryFilter?: FilterOption<ExerciseCategoryFilterOption>;
    exerciseTypesFilter?: FilterOption<ExerciseTypeFilterOption>;
    difficultyFilter?: FilterOption<DifficultyFilterOption>;
    achievedScore?: RangeFilter;
    achievablePoints?: RangeFilter;
};

export type ExerciseFilterResults = { filteredSidebarData?: SidebarData; appliedExerciseFilters?: ExerciseFilterOptions; isFilterActive: boolean };
