import { ExerciseCategoryFilterOption, ExerciseFilterOptions, ExerciseTypeFilterOptions } from 'app/types/exercise-filter';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ExerciseType, getIcon } from 'app/entities/exercise.model';

const DEFAULT_EXERCISE_TYPES_FILTER: ExerciseTypeFilterOptions = [
    { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
    { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: getIcon(ExerciseType.QUIZ) },
    { name: 'artemisApp.courseStatistics.modeling', value: ExerciseType.MODELING, checked: false, icon: getIcon(ExerciseType.MODELING) },
    { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
    { name: 'artemisApp.courseStatistics.file-upload', value: ExerciseType.FILE_UPLOAD, checked: false, icon: getIcon(ExerciseType.FILE_UPLOAD) },
];

export function getExerciseCategoryFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData): ExerciseCategoryFilterOption[] {
    if (exerciseFilters?.categoryFilters) {
        return exerciseFilters?.categoryFilters;
    }

    return (
        sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined)
            .flatMap((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || [])
            .map((category: ExerciseCategory) => ({ category: category, searched: false }))
            .reduce((unique: ExerciseCategoryFilterOption[], item: ExerciseCategoryFilterOption) => {
                return unique.some((uniqueItem) => uniqueItem.category.equals(item.category)) ? unique : [...unique, item];
            }, [])
            .sort((categoryFilterOptionsA, categoryFilterOptionB) => categoryFilterOptionsA.category.compare(categoryFilterOptionB.category)) ?? []
    );
}

export function getExerciseTypeFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData) {
    if (exerciseFilters?.exerciseTypesFilter) {
        return exerciseFilters?.exerciseTypesFilter;
    }

    const existingExerciseTypes = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.type !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.type);

    return DEFAULT_EXERCISE_TYPES_FILTER?.filter((exerciseType) => existingExerciseTypes?.includes(exerciseType.value));
}
