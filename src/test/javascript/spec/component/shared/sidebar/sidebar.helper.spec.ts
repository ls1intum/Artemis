import { getExerciseCategoryFilterOptions, getExerciseTypeFilterOptions } from 'app/shared/sidebar/sidebar.helper';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';

const EXERCISE_1 = { categories: [new ExerciseCategory('#691b0b', 'category1'), new ExerciseCategory('#1b97ca', 'category2')] } as Exercise;
const EXERCISE_2 = { categories: [new ExerciseCategory('#0d3cc2', 'category3'), new ExerciseCategory('#6ae8ac', 'category4')] } as Exercise;
const EXERCISE_3 = { categories: [new ExerciseCategory('#691b0b', 'category5')] } as Exercise;

/** contains 1 duplicate exercise category with {@link EXERCISE_1} */
const EXERCISE_4 = { categories: [new ExerciseCategory('#691b0b', 'category1'), new ExerciseCategory('#1b97ca', 'category8')] } as Exercise;

const SIDEBAR_CARD_ELEMENT_1 = { exercise: EXERCISE_1, type: ExerciseType.TEXT } as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_2 = { exercise: EXERCISE_2, type: ExerciseType.PROGRAMMING } as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_3 = { exercise: EXERCISE_3, type: ExerciseType.QUIZ } as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_4 = { exercise: EXERCISE_4 } as SidebarCardElement;

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

        const exerciseCategories = getExerciseCategoryFilterOptions(undefined, sidebarData);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('#691b0b', 'category1'), searched: false },
                { category: new ExerciseCategory('#1b97ca', 'category2'), searched: false },
                { category: new ExerciseCategory('#0d3cc2', 'category3'), searched: false },
                { category: new ExerciseCategory('#6ae8ac', 'category4'), searched: false },
                { category: new ExerciseCategory('#691b0b', 'category5'), searched: false },
            ],
        });
    });

    it('should filter duplicates', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_4],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(undefined, sidebarData);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('#691b0b', 'category1'), searched: false },
                { category: new ExerciseCategory('#1b97ca', 'category2'), searched: false },
                { category: new ExerciseCategory('#1b97ca', 'category8'), searched: false },
            ],
        });
    });

    it('should sort categories alphanumerical', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_1],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(undefined, sidebarData);
        expect(exerciseCategories).toEqual({
            isDisplayed: true,
            options: [
                { category: new ExerciseCategory('#691b0b', 'category1'), searched: false },
                { category: new ExerciseCategory('#1b97ca', 'category2'), searched: false },
                { category: new ExerciseCategory('#0d3cc2', 'category3'), searched: false },
                { category: new ExerciseCategory('#6ae8ac', 'category4'), searched: false },
            ],
        });
    });

    it('should directly return if already initialized', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
        };

        const exerciseCategories = getExerciseCategoryFilterOptions(
            {
                categoryFilter: {
                    isDisplayed: false,
                    options: [],
                },
            },
            sidebarData,
        );
        expect(exerciseCategories).toEqual({
            isDisplayed: false,
            options: [],
        });
    });
});

describe('getExerciseTypeFilterOptions', () => {
    it('should return present difficulty levels and sort them properly (same order as instructor creation)', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3, SIDEBAR_CARD_ELEMENT_4],
        };

        const exerciseTypes = getExerciseTypeFilterOptions(undefined, sidebarData);
        expect(exerciseTypes).toEqual({
            isDisplayed: true,
            options: [
                { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
                { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: getIcon(ExerciseType.QUIZ) },
                { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
            ],
        });
    });

    it('should directly return if already initialized', () => {
        const sidebarData: SidebarData = {
            groupByCategory: true,
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2],
        };

        const exerciseCategories = getExerciseTypeFilterOptions(
            {
                exerciseTypesFilter: {
                    isDisplayed: false,
                    options: [],
                },
            },
            sidebarData,
        );
        expect(exerciseCategories).toEqual({
            isDisplayed: false,
            options: [],
        });
    });
});
