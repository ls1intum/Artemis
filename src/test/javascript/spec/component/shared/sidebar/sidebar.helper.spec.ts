import {
    getAchievablePointsAndAchievedScoreFilterOptions,
    getExerciseCategoryFilterOptions,
    getExerciseDifficultyFilterOptions,
    getExerciseTypeFilterOptions,
} from 'app/shared/sidebar/sidebar.helper';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';

const EXERCISE_1 = { categories: [new ExerciseCategory('category1', '#691b0b'), new ExerciseCategory('category2', '#1b97ca')], maxPoints: 10 } as Exercise;
const EXERCISE_2 = { categories: [new ExerciseCategory('category3', '#0d3cc2'), new ExerciseCategory('category4', '#6ae8ac')], maxPoints: 5 } as Exercise;
const EXERCISE_3 = { categories: [new ExerciseCategory('category5', '#691b0b')], maxPoints: 2 } as Exercise;

/** contains 1 duplicate categories and maxPoints with {@link EXERCISE_1} */
const EXERCISE_4 = { categories: [new ExerciseCategory('category1', '#691b0b'), new ExerciseCategory('category8', '#1b97ca')], maxPoints: 10 } as Exercise;
const EXERCISE_5 = { categories: [] as ExerciseCategory[], maxPoints: 20 } as Exercise;

const SIDEBAR_CARD_ELEMENT_1 = {
    exercise: EXERCISE_1,
    type: ExerciseType.TEXT,
    difficulty: DifficultyLevel.HARD,
    studentParticipation: { results: [{ score: 7.7 } as Result] } as StudentParticipation,
} as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_2 = {
    exercise: EXERCISE_2,
    type: ExerciseType.PROGRAMMING,
    difficulty: DifficultyLevel.EASY,
    studentParticipation: { results: [{ score: 82.3 } as Result] } as StudentParticipation,
} as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_3 = {
    exercise: EXERCISE_3,
    type: ExerciseType.QUIZ,
    difficulty: DifficultyLevel.MEDIUM,
    studentParticipation: { results: [{ score: 44.5 } as Result] } as StudentParticipation,
} as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_4 = { exercise: EXERCISE_4 } as SidebarCardElement;

/** contains duplicated type and difficulty with {@link SIDEBAR_CARD_ELEMENT_2}*/
const SIDEBAR_CARD_ELEMENT_5 = { exercise: EXERCISE_5, type: ExerciseType.PROGRAMMING, difficulty: DifficultyLevel.EASY } as SidebarCardElement;

describe('getExerciseCategoryFilterOptions', () => {
    it('should return all exercise categories', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            groupedData: {
                dueSoon: {
                    entityData: [],
                },
                noDate: {
                    entityData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
                },
                past: {
                    entityData: [SIDEBAR_CARD_ELEMENT_3],
                },
            },
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(sidebarData, undefined);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('category1', '#691b0b'), searched: false },
                { category: new ExerciseCategory('category2', '#1b97ca'), searched: false },
                { category: new ExerciseCategory('category3', '#0d3cc2'), searched: false },
                { category: new ExerciseCategory('category4', '#6ae8ac'), searched: false },
                { category: new ExerciseCategory('category5', '#691b0b'), searched: false },
            ],
        });
    });

    it('should filter duplicates', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_4],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(sidebarData, undefined);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('category1', '#691b0b'), searched: false },
                { category: new ExerciseCategory('category2', '#1b97ca'), searched: false },
                { category: new ExerciseCategory('category8', '#1b97ca'), searched: false },
            ],
        });
    });

    it('should sort categories alphanumerical', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_1],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(sidebarData, undefined);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('category1', '#691b0b'), searched: false },
                { category: new ExerciseCategory('category2', '#1b97ca'), searched: false },
                { category: new ExerciseCategory('category3', '#0d3cc2'), searched: false },
                { category: new ExerciseCategory('category4', '#6ae8ac'), searched: false },
            ],
        });
    });

    it('should not be displayed if no categories are available', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_5],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(sidebarData, undefined);
        expect(exerciseCategories).toEqual({
            isDisplayed: false,
            options: [],
        });
    });

    it('should directly return if already initialized', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(sidebarData, {
            categoryFilter: {
                isDisplayed: false,
                options: [],
            },
        });
        expect(exerciseCategories).toEqual({
            isDisplayed: false,
            options: [],
        });
    });
});

describe('getExerciseTypeFilterOptions', () => {
    it('should return present exercise types and sort them properly (same order as instructor creation)', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3, SIDEBAR_CARD_ELEMENT_4, SIDEBAR_CARD_ELEMENT_5],
        };

        const exerciseTypesFilter = getExerciseTypeFilterOptions(sidebarData, undefined);
        expect(exerciseTypesFilter).toEqual({
            isDisplayed: true,
            options: [
                { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
                { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: getIcon(ExerciseType.QUIZ) },
                { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
            ],
        });
    });

    it('should not contain duplicates', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_5],
        };

        const exerciseTypesFilter = getExerciseTypeFilterOptions(sidebarData, undefined);
        expect(exerciseTypesFilter).toEqual({
            isDisplayed: true,
            options: [
                { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
                { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
            ],
        });
    });

    it('should directly return if already initialized', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
        };

        const exerciseTypesFilter = getExerciseTypeFilterOptions(sidebarData, {
            exerciseTypesFilter: {
                isDisplayed: false,
                options: [],
            },
        });
        expect(exerciseTypesFilter).toEqual({
            isDisplayed: false,
            options: [],
        });
    });
});

describe('getExerciseDifficultyFilterOptions', () => {
    it('should return present exercise difficulties and sort them ascending', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3, SIDEBAR_CARD_ELEMENT_4, SIDEBAR_CARD_ELEMENT_5],
        };

        const difficultyFilter = getExerciseDifficultyFilterOptions(sidebarData, undefined);
        expect(difficultyFilter).toEqual({
            isDisplayed: true,
            options: [
                { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
                { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
                { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
            ],
        });
    });

    it('should not contain duplicates', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_5],
        };

        const difficultyFilter = getExerciseDifficultyFilterOptions(sidebarData, undefined);
        expect(difficultyFilter).toEqual({
            isDisplayed: true,
            options: [
                { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
                { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
            ],
        });
    });

    it('should directly return if already initialized', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
        };

        const difficultyFilter = getExerciseDifficultyFilterOptions(sidebarData, {
            difficultyFilter: {
                isDisplayed: false,
                options: [],
            },
        });
        expect(difficultyFilter).toEqual({
            isDisplayed: false,
            options: [],
        });
    });
});

describe('getAchievablePointsAndAchievedScoreFilterOptions', () => {
    const expectedFilterForFirstThreePresentExercises = {
        achievablePoints: {
            isDisplayed: true,
            filter: {
                generalMin: 2,
                generalMax: 10,
                selectedMin: 2,
                selectedMax: 10,
                step: 1,
            },
        },
        achievedScore: {
            isDisplayed: true,
            filter: {
                generalMax: 85,
                generalMin: 5,
                selectedMax: 85,
                selectedMin: 5,
                step: 5,
            },
        },
    };

    it('should return present exercise point and score range', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3],
        };

        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(sidebarData, undefined);
        expect(scoreAndPointsFilterOptions).toEqual(expectedFilterForFirstThreePresentExercises);
    });

    it('should set scores filter to not displayed if no scores are present', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_4, SIDEBAR_CARD_ELEMENT_5],
        };

        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(sidebarData, undefined);

        expect(scoreAndPointsFilterOptions).toEqual({
            achievablePoints: {
                isDisplayed: true,
                filter: {
                    generalMax: 20,
                    generalMin: 10,
                    selectedMax: 20,
                    selectedMin: 10,
                    step: 1,
                },
            },
            achievedScore: {
                isDisplayed: false,
                filter: {
                    generalMax: -Infinity,
                    generalMin: Infinity,
                    selectedMax: -Infinity,
                    selectedMin: Infinity,
                    step: 1,
                },
            },
        });
    });

    it('should directly return if already initialized and filters are not applied', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1],
        };

        const scoreAndPointsFilterOptions = getAchievablePointsAndAchievedScoreFilterOptions(sidebarData, {
            achievablePoints: expectedFilterForFirstThreePresentExercises.achievablePoints,
            achievedScore: expectedFilterForFirstThreePresentExercises.achievedScore,
        });
        expect(scoreAndPointsFilterOptions).toEqual({
            achievablePoints: expectedFilterForFirstThreePresentExercises.achievablePoints,
            achievedScore: expectedFilterForFirstThreePresentExercises.achievedScore,
        });
    });
});
