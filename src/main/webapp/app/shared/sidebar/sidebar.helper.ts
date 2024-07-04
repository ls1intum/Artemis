import { DifficultyFilterOption, ExerciseCategoryFilterOption, ExerciseFilterOptions, ExerciseTypeFilterOption, FilterOption, RangeFilter } from 'app/types/exercise-filter';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';
import { roundToNextMultiple } from 'app/shared/util/utils';

const POINTS_STEP = 1;
const SCORE_THRESHOLD_TO_INCREASE_STEP = 20;
const SMALL_SCORE_STEP = 1;
const SCORE_STEP = 5;

const DEFAULT_DIFFICULTIES_FILTER: DifficultyFilterOption[] = [
    { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
    { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
    { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
];

const DEFAULT_EXERCISE_TYPES_FILTER: ExerciseTypeFilterOption[] = [
    { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
    { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: getIcon(ExerciseType.QUIZ) },
    { name: 'artemisApp.courseStatistics.modeling', value: ExerciseType.MODELING, checked: false, icon: getIcon(ExerciseType.MODELING) },
    { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
    { name: 'artemisApp.courseStatistics.file-upload', value: ExerciseType.FILE_UPLOAD, checked: false, icon: getIcon(ExerciseType.FILE_UPLOAD) },
];

export function getExerciseCategoryFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData): FilterOption<ExerciseCategoryFilterOption> {
    if (exerciseFilters?.categoryFilter) {
        return exerciseFilters?.categoryFilter;
    }

    const categoryOptions =
        sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined)
            .flatMap((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || [])
            .map((category: ExerciseCategory) => ({ category: category, searched: false }))
            .reduce((unique: ExerciseCategoryFilterOption[], item: ExerciseCategoryFilterOption) => {
                return unique.some((uniqueItem) => uniqueItem.category.equals(item.category)) ? unique : [...unique, item];
            }, [])
            .sort((categoryFilterOptionsA, categoryFilterOptionB) => categoryFilterOptionsA.category.compare(categoryFilterOptionB.category)) ?? [];

    const isDisplayed = !!categoryOptions.length;
    return { isDisplayed: isDisplayed, options: categoryOptions };
}

export function getExerciseTypeFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData): FilterOption<ExerciseTypeFilterOption> {
    if (exerciseFilters?.exerciseTypesFilter) {
        return exerciseFilters?.exerciseTypesFilter;
    }

    const existingExerciseTypes = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.type !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.type);

    const availableTypeFilters = DEFAULT_EXERCISE_TYPES_FILTER?.filter((exerciseType) => existingExerciseTypes?.includes(exerciseType.value));

    return { isDisplayed: availableTypeFilters.length > 1, options: availableTypeFilters };
}

export function getExerciseDifficultyFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData): FilterOption<DifficultyFilterOption> {
    if (exerciseFilters?.difficultyFilter) {
        return exerciseFilters.difficultyFilter;
    }

    const existingDifficulties = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);

    const availableDifficultyFilters = DEFAULT_DIFFICULTIES_FILTER?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));

    return { isDisplayed: !!availableDifficultyFilters.length, options: availableDifficultyFilters };
}

export function getAchievablePointsAndAchievedScoreFilterOptions(
    exerciseFilters?: ExerciseFilterOptions,
    sidebarData?: SidebarData,
): {
    achievablePoints?: RangeFilter;
    achievedScore?: RangeFilter;
} {
    if (!sidebarData?.ungroupedData) {
        return { achievablePoints: undefined, achievedScore: undefined };
    }

    const isPointsFilterApplied =
        exerciseFilters?.achievablePoints?.filter.selectedMax !== exerciseFilters?.achievablePoints?.filter.generalMax ||
        exerciseFilters?.achievablePoints?.filter.selectedMin !== exerciseFilters?.achievablePoints?.filter.generalMin;
    const isScoreFilterApplied =
        exerciseFilters?.achievedScore?.filter.selectedMax !== exerciseFilters?.achievedScore?.filter.generalMax ||
        exerciseFilters?.achievedScore?.filter.selectedMin !== exerciseFilters?.achievedScore?.filter.generalMin;
    if (!isPointsFilterApplied && !isScoreFilterApplied && exerciseFilters?.achievablePoints && exerciseFilters?.achievedScore) {
        // the scores might change when we work on exercises, so we re-calculate the filter options (but only if the filter is actually applied)
        return { achievablePoints: exerciseFilters?.achievablePoints, achievedScore: exerciseFilters?.achievedScore };
    }

    let minAchievablePoints = Infinity;
    let maxAchievablePoints = -Infinity;

    let minAchievedScore = Infinity;
    let maxAchievedScore = -Infinity;

    sidebarData.ungroupedData.forEach((sidebarElement: SidebarCardElement) => {
        if (sidebarElement.exercise?.maxPoints) {
            const currentExerciseMaxPoints = sidebarElement.exercise.maxPoints;

            if (currentExerciseMaxPoints > maxAchievablePoints) {
                maxAchievablePoints = currentExerciseMaxPoints;
            }
            if (currentExerciseMaxPoints < minAchievablePoints) {
                minAchievablePoints = currentExerciseMaxPoints;
            }

            if (sidebarElement.studentParticipation) {
                const currentExerciseAchievedScore = getLatestResultOfStudentParticipation(sidebarElement.studentParticipation, true)?.score;

                if (currentExerciseAchievedScore !== undefined) {
                    if (currentExerciseAchievedScore > maxAchievedScore) {
                        maxAchievedScore = currentExerciseAchievedScore;
                    }
                    if (currentExerciseAchievedScore < minAchievedScore) {
                        minAchievedScore = currentExerciseAchievedScore;
                    }
                }
            }
        }
    });

    const roundUp = true;
    const roundDown = false;
    minAchievablePoints = roundToNextMultiple(minAchievablePoints, POINTS_STEP, roundDown);
    maxAchievablePoints = roundToNextMultiple(maxAchievablePoints, POINTS_STEP, roundUp);

    minAchievedScore = roundToNextMultiple(minAchievedScore, SMALL_SCORE_STEP, roundDown);
    maxAchievedScore = roundToNextMultiple(maxAchievedScore, SMALL_SCORE_STEP, roundUp);

    if (maxAchievedScore > SCORE_THRESHOLD_TO_INCREASE_STEP) {
        minAchievedScore = roundToNextMultiple(minAchievedScore, SCORE_STEP, roundDown);
        maxAchievedScore = roundToNextMultiple(maxAchievedScore, SCORE_STEP, roundUp);
    }

    return {
        achievablePoints: {
            isDisplayed: minAchievablePoints < maxAchievablePoints,
            filter: {
                generalMin: minAchievablePoints,
                generalMax: maxAchievablePoints,
                selectedMin: minAchievablePoints,
                selectedMax: maxAchievablePoints,
                step: POINTS_STEP,
            },
        },
        achievedScore: {
            isDisplayed: minAchievedScore < maxAchievedScore && minAchievedScore !== Infinity,
            filter: {
                generalMin: minAchievedScore,
                generalMax: maxAchievedScore,
                selectedMin: minAchievedScore,
                selectedMax: maxAchievedScore,
                step: maxAchievedScore <= SCORE_THRESHOLD_TO_INCREASE_STEP ? SMALL_SCORE_STEP : SCORE_STEP,
            },
        },
    };
}
