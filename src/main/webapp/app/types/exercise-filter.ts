import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { SidebarData } from 'app/types/sidebar';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export type ExerciseCategoryFilterOption = { category: ExerciseCategory; searched: boolean };
export type ExerciseTypeFilterOptions = { name: string; value: ExerciseType; checked: boolean; icon: IconProp }[];
export type DifficultyFilterOptions = { name: string; value: DifficultyLevel; checked: boolean }[];
export type RangeFilter = { generalMin: number; generalMax: number; selectedMin: number; selectedMax: number; step: number };

export type ExerciseFilterOptions = {
    categoryFilters?: ExerciseCategoryFilterOption[];
    exerciseTypesFilter?: ExerciseTypeFilterOptions;
    difficultyFilters?: DifficultyFilterOptions;
    achievedScore?: RangeFilter;
    achievablePoints?: RangeFilter;
};

export type ExerciseFilterResults = { filteredSidebarData?: SidebarData; appliedExerciseFilters?: ExerciseFilterOptions; isFilterActive: boolean };
