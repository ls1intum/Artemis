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

function getAvailableCategoriesAsFilterOptions(sidebarData?: SidebarData): ExerciseCategoryFilterOption[] | undefined {
    const sidebarElementsWithExerciseCategory: SidebarCardElement[] | undefined = sidebarData?.ungroupedData?.filter(
        (sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined,
    );
    const availableCategories: ExerciseCategory[] | undefined = sidebarElementsWithExerciseCategory?.flatMap(
        (sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || [],
    );

    // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
    const availableCategoriesAsFilterOptions: ExerciseCategoryFilterOption[] | undefined = availableCategories?.map((category: ExerciseCategory) => ({
        category: category,
        searched: false,
    }));
    return availableCategoriesAsFilterOptions;
}

function getExerciseCategoryFilterOptionsWithoutDuplicates(exerciseCategoryFilterOptions?: ExerciseCategoryFilterOption[]): ExerciseCategoryFilterOption[] | undefined {
    return exerciseCategoryFilterOptions?.reduce((unique: ExerciseCategoryFilterOption[], item: ExerciseCategoryFilterOption) => {
        if (!unique.some((uniqueItem) => uniqueItem.category.equals(item.category))) {
            unique.push(item);
        }
        return unique;
    }, []);
}

function sortExerciseCategoryFilterOptionsSortedByName(exerciseCategoryFilterOptions?: ExerciseCategoryFilterOption[]): ExerciseCategoryFilterOption[] {
    return exerciseCategoryFilterOptions?.sort((categoryFilterOptionsA, categoryFilterOptionB) => categoryFilterOptionsA.category.compare(categoryFilterOptionB.category)) ?? [];
}

/**
 * @param exerciseFilters that might already be defined for the course sidebar
 * @param sidebarData that contains the exercises of a course and their information
 *
 * @returns already defined category filter options if they exist, otherwise the category filter options based on the sidebar data
 */
export function getExerciseCategoryFilterOptions(sidebarData?: SidebarData, exerciseFilters?: ExerciseFilterOptions): FilterOption<ExerciseCategoryFilterOption> {
    if (exerciseFilters?.categoryFilter) {
        return exerciseFilters?.categoryFilter;
    }

    const availableCategoriesAsFilterOptions = getAvailableCategoriesAsFilterOptions(sidebarData);
    const selectableCategoryFilterOptions = getExerciseCategoryFilterOptionsWithoutDuplicates(availableCategoriesAsFilterOptions);
    const sortedCategoryFilterOptions = sortExerciseCategoryFilterOptionsSortedByName(selectableCategoryFilterOptions);

    const isDisplayed = !!sortedCategoryFilterOptions.length;
    return { isDisplayed: isDisplayed, options: selectableCategoryFilterOptions ?? [] };
}

/**
 * @param exerciseFilters that might already be defined for the course sidebar
 * @param sidebarData that contains the exercises of a course and their information
 *
 * @returns already defined exercise type filter options if they exist, otherwise the exercise type filter options based on the sidebar data
 */
export function getExerciseTypeFilterOptions(sidebarData?: SidebarData, exerciseFilters?: ExerciseFilterOptions): FilterOption<ExerciseTypeFilterOption> {
    if (exerciseFilters?.exerciseTypesFilter) {
        return exerciseFilters?.exerciseTypesFilter;
    }

    const existingExerciseTypes = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.type !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.type);

    const availableTypeFilters = DEFAULT_EXERCISE_TYPES_FILTER?.filter((exerciseType) => existingExerciseTypes?.includes(exerciseType.value));

    return { isDisplayed: availableTypeFilters.length > 1, options: availableTypeFilters };
}

/**
 * @param exerciseFilters that might already be defined for the course sidebar
 * @param sidebarData that contains the exercises of a course and their information
 *
 * @returns already defined difficulty filter options if they exist, otherwise the difficulty filter options based on the sidebar data
 */
export function getExerciseDifficultyFilterOptions(sidebarData?: SidebarData, exerciseFilters?: ExerciseFilterOptions): FilterOption<DifficultyFilterOption> {
    if (exerciseFilters?.difficultyFilter) {
        return exerciseFilters.difficultyFilter;
    }

    const existingDifficulties = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);

    const availableDifficultyFilters = DEFAULT_DIFFICULTIES_FILTER?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));

    return { isDisplayed: !!availableDifficultyFilters.length, options: availableDifficultyFilters };
}

export function isRangeFilterApplied(rangeFilter?: RangeFilter): boolean {
    if (!rangeFilter?.filter) {
        return false;
    }

    const filter = rangeFilter.filter;
    const isExcludingMinValues = filter.selectedMin !== filter.generalMin;
    const isExcludingMaxValues = filter.selectedMax !== filter.generalMax;
    return isExcludingMinValues || isExcludingMaxValues;
}

function getUpdatedMinAndMaxValues(minValue: number, maxValue: number, currentMaxValue: number) {
    let updatedMinValue = minValue;
    let updatedMaxValue = maxValue;

    if (currentMaxValue < minValue) {
        updatedMinValue = currentMaxValue;
    }
    if (currentMaxValue > maxValue) {
        updatedMaxValue = currentMaxValue;
    }

    return { updatedMinValue, updatedMaxValue };
}

/**
 * The calculation for points and score are intentionally mixed into one method to reduce the number of iterations over the sidebar data.
 * @param sidebarData
 */
function calculateMinAndMaxForPointsAndScore(sidebarData: SidebarData) {
    let minAchievablePoints = Infinity;
    let maxAchievablePoints = -Infinity;

    let minAchievedScore = Infinity;
    let maxAchievedScore = -Infinity;

    sidebarData.ungroupedData?.forEach((sidebarElement: SidebarCardElement) => {
        if (sidebarElement.exercise?.maxPoints) {
            const currentExerciseMaxPoints = sidebarElement.exercise.maxPoints;

            const { updatedMinValue, updatedMaxValue } = getUpdatedMinAndMaxValues(minAchievablePoints, maxAchievablePoints, currentExerciseMaxPoints);
            minAchievablePoints = updatedMinValue;
            maxAchievablePoints = updatedMaxValue;

            if (sidebarElement.studentParticipation) {
                const currentExerciseAchievedScore = getLatestResultOfStudentParticipation(sidebarElement.studentParticipation, true)?.score;

                if (currentExerciseAchievedScore !== undefined) {
                    const { updatedMinValue, updatedMaxValue } = getUpdatedMinAndMaxValues(minAchievedScore, maxAchievedScore, currentExerciseAchievedScore);
                    minAchievedScore = updatedMinValue;
                    maxAchievedScore = updatedMaxValue;
                }
            }
        }
    });

    return { minAchievablePoints, maxAchievablePoints, minAchievedScore, maxAchievedScore };
}

/**
 * **Rounds the min and max values for achievable points and achieved score to the next multiple of the step.
 * The step {@link POINTS_STEP}, and {@link SCORE_STEP} or {@link SMALL_SCORE_STEP} are the selectable values for the range filter.**
 * <br>
 * <i>For the **score filter**, the step is increased if we have more than 20 values between the min and max value,
 * as up to 100 values are theoretically possible.<br>
 * For the **achievable points filter**, the step is always 1 as exercises usually have between 1 and 15 points,
 * so we do not need to increase the step and thereby limit accuracy of filter options.</i>
 *
 * @param minAchievablePoints
 * @param maxAchievablePoints
 * @param minAchievedScore
 * @param maxAchievedScore
 */
function roundRangeFilterMinAndMaxValues(minAchievablePoints: number, maxAchievablePoints: number, minAchievedScore: number, maxAchievedScore: number) {
    const roundUp = true;
    const roundDown = false;
    const minAchievablePointsRounded = roundToNextMultiple(minAchievablePoints, POINTS_STEP, roundDown);
    const maxAchievablePointsRounded = roundToNextMultiple(maxAchievablePoints, POINTS_STEP, roundUp);

    let minAchievedScoreRounded;
    let maxAchievedScoreRounded;

    if (maxAchievedScore > SCORE_THRESHOLD_TO_INCREASE_STEP) {
        minAchievedScoreRounded = roundToNextMultiple(minAchievedScore, SCORE_STEP, roundDown);
        maxAchievedScoreRounded = roundToNextMultiple(maxAchievedScore, SCORE_STEP, roundUp);
    } else {
        minAchievedScoreRounded = roundToNextMultiple(minAchievedScore, SMALL_SCORE_STEP, roundDown);
        maxAchievedScoreRounded = roundToNextMultiple(maxAchievedScore, SMALL_SCORE_STEP, roundUp);
    }

    return { minAchievablePointsRounded, maxAchievablePointsRounded, minAchievedScoreRounded, maxAchievedScoreRounded };
}

function calculateAchievablePointsFilterOptions(sidebarData: SidebarData): { achievablePoints?: RangeFilter; achievedScore?: RangeFilter } {
    const { minAchievablePoints, maxAchievablePoints, minAchievedScore, maxAchievedScore } = calculateMinAndMaxForPointsAndScore(sidebarData);

    const { minAchievablePointsRounded, maxAchievablePointsRounded, minAchievedScoreRounded, maxAchievedScoreRounded } = roundRangeFilterMinAndMaxValues(
        minAchievablePoints,
        maxAchievablePoints,
        minAchievedScore,
        maxAchievedScore,
    );

    return {
        achievablePoints: {
            isDisplayed: minAchievablePointsRounded < maxAchievablePointsRounded,
            filter: {
                generalMin: minAchievablePointsRounded,
                generalMax: maxAchievablePointsRounded,
                selectedMin: minAchievablePointsRounded,
                selectedMax: maxAchievablePointsRounded,
                step: POINTS_STEP,
            },
        },
        achievedScore: {
            isDisplayed: minAchievedScoreRounded < maxAchievedScoreRounded && minAchievedScoreRounded !== Infinity,
            filter: {
                generalMin: minAchievedScoreRounded,
                generalMax: maxAchievedScoreRounded,
                selectedMin: minAchievedScoreRounded,
                selectedMax: maxAchievedScoreRounded,
                step: maxAchievedScoreRounded <= SCORE_THRESHOLD_TO_INCREASE_STEP ? SMALL_SCORE_STEP : SCORE_STEP,
            },
        },
    };
}

/**
 * @param exerciseFilters that might already be defined for the course sidebar
 * @param sidebarData that contains the exercises of a course and their information
 *
 * @returns already defined achievable points and achieved score filter options if they exist, otherwise the achievable points and achieved score filter options based on the sidebar data
 */
export function getAchievablePointsAndAchievedScoreFilterOptions(
    sidebarData?: SidebarData,
    exerciseFilters?: ExerciseFilterOptions,
): {
    achievablePoints?: RangeFilter;
    achievedScore?: RangeFilter;
} {
    if (!sidebarData?.ungroupedData) {
        return { achievablePoints: undefined, achievedScore: undefined };
    }

    const isPointsFilterApplied = isRangeFilterApplied(exerciseFilters?.achievablePoints);
    const isScoreFilterApplied = isRangeFilterApplied(exerciseFilters?.achievedScore);

    const isRecalculatingFilterOptionsRequired = isPointsFilterApplied || isScoreFilterApplied || !exerciseFilters?.achievablePoints || !exerciseFilters?.achievedScore;
    if (!isRecalculatingFilterOptionsRequired) {
        // the scores might change when we work on exercises, so we re-calculate the filter options (but only if the filter is actually applied)
        return { achievablePoints: exerciseFilters?.achievablePoints, achievedScore: exerciseFilters?.achievedScore };
    }

    return calculateAchievablePointsFilterOptions(sidebarData);
}
