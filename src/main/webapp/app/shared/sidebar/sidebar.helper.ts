import { DifficultyFilterOptions, ExerciseCategoryFilterOption, ExerciseFilterOptions, ExerciseTypeFilterOptions, RangeFilter } from 'app/types/exercise-filter';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { getLatestResultOfStudentParticipation } from 'app/exercises/shared/participation/participation.utils';
import { roundToMultiple } from 'app/shared/util/utils';

const POINTS_STEP = 1;
const SCORE_THRESHOLD_TO_INCREASE_STEP = 20;
const SMALL_SCORE_STEP = 1;
const SCORE_STEP = 5;

// TODO allow to filter for no difficulty?
const DEFAULT_DIFFICULTIES_FILTER: DifficultyFilterOptions = [
    { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
    { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
    { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
];

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

export function getExerciseDifficultyFilterOptions(exerciseFilters?: ExerciseFilterOptions, sidebarData?: SidebarData) {
    if (exerciseFilters?.difficultyFilters) {
        return exerciseFilters.difficultyFilters;
    }

    const existingDifficulties = sidebarData?.ungroupedData
        ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
        .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);

    return DEFAULT_DIFFICULTIES_FILTER?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));
}

export function getAchievablePointsAndAchievedScoreFilterOptions(
    exerciseFilters?: ExerciseFilterOptions,
    sidebarData?: SidebarData,
):
    | {
          achievablePoints: RangeFilter;
          achievedScore: RangeFilter;
      }
    | undefined {
    if (!sidebarData?.ungroupedData) {
        return;
    }

    const isPointsFilterApplied =
        exerciseFilters?.achievablePoints?.selectedMax === exerciseFilters?.achievablePoints?.generalMax ||
        exerciseFilters?.achievablePoints?.selectedMin === exerciseFilters?.achievablePoints?.generalMin;
    const isScoreFilterApplied =
        exerciseFilters?.achievedScore?.selectedMax === exerciseFilters?.achievedScore?.generalMax ||
        exerciseFilters?.achievedScore?.selectedMin === exerciseFilters?.achievedScore?.generalMin;
    if (!isPointsFilterApplied && !isScoreFilterApplied) {
        // the scores might change when we work on exercises, so we re-calculate the filter options (but only if the filter is actually applied)
        return;
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

    minAchievablePoints = roundToMultiple(minAchievablePoints, POINTS_STEP);
    maxAchievablePoints = roundToMultiple(maxAchievablePoints, POINTS_STEP);

    minAchievedScore = roundToMultiple(minAchievedScore, SMALL_SCORE_STEP);
    maxAchievedScore = roundToMultiple(maxAchievedScore, SMALL_SCORE_STEP);

    if (maxAchievedScore > SCORE_THRESHOLD_TO_INCREASE_STEP) {
        minAchievedScore = roundToMultiple(minAchievedScore, SCORE_STEP);
        maxAchievedScore = roundToMultiple(maxAchievedScore, SCORE_STEP);
    }

    return {
        achievablePoints: {
            generalMin: minAchievablePoints,
            generalMax: maxAchievablePoints,
            selectedMin: minAchievablePoints,
            selectedMax: maxAchievablePoints,
            step: POINTS_STEP,
        },
        achievedScore: {
            generalMin: minAchievedScore,
            generalMax: maxAchievedScore,
            selectedMin: minAchievedScore,
            selectedMax: maxAchievedScore,
            step: maxAchievedScore <= SCORE_THRESHOLD_TO_INCREASE_STEP ? SMALL_SCORE_STEP : SCORE_STEP,
        },
    };
}
