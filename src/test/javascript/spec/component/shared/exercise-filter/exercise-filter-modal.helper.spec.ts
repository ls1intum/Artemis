import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SidebarCardElement } from 'app/types/sidebar';
import {
    satisfiesCategoryFilter,
    satisfiesDifficultyFilter,
    satisfiesFilters,
    satisfiesPointsFilter,
    satisfiesScoreFilter,
} from 'app/shared/exercise-filter/exercise-filter-modal.helper';
import { FilterDetails, RangeFilter } from 'app/types/exercise-filter';

const EXERCISE_1 = { categories: [new ExerciseCategory('category1', '#691b0b'), new ExerciseCategory('category2', '#1b97ca')], maxPoints: 10, type: ExerciseType.TEXT } as Exercise;
const EXERCISE_2 = { categories: [new ExerciseCategory('category3', '#0d3cc2'), new ExerciseCategory('category4', '#6ae8ac')], maxPoints: 5 } as Exercise;
const EXERCISE_4 = { categories: [new ExerciseCategory('category1', '#691b0b'), new ExerciseCategory('category8', '#1b97ca')], maxPoints: 10 } as Exercise;
const EXERCISE_5 = { maxPoints: 20 } as Exercise;

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
const SIDEBAR_CARD_ELEMENT_4 = { exercise: EXERCISE_4 } as SidebarCardElement;

/** contains duplicated type and difficulty with {@link SIDEBAR_CARD_ELEMENT_2}*/
const SIDEBAR_CARD_ELEMENT_5 = { exercise: EXERCISE_5, type: ExerciseType.PROGRAMMING, difficulty: DifficultyLevel.EASY } as SidebarCardElement;

describe('satisfiesDifficultyFilter', () => {
    it('should return true if difficulty filter is undefined', () => {
        const difficultyFilter = undefined;

        const resultItemWithDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_1, difficultyFilter);
        expect(resultItemWithDifficulty).toBeTrue();

        const resultItemWithoutDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_4, difficultyFilter);
        expect(resultItemWithoutDifficulty).toBeTrue();
    });

    it('should return true if difficulty filter is []', () => {
        const difficultyFilter: DifficultyLevel[] = [];

        const resultItemWithDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_1, difficultyFilter);
        expect(resultItemWithDifficulty).toBeTrue();

        const resultItemWithoutDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_4, difficultyFilter);
        expect(resultItemWithoutDifficulty).toBeTrue();
    });

    it('should return true if difficulty is in difficulty filter', () => {
        const difficultyFilter = [DifficultyLevel.HARD];

        const resultItemWithDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_1, difficultyFilter);
        expect(resultItemWithDifficulty).toBeTrue();
    });

    it('should return false if difficulty is NOT in difficulty filter', () => {
        const difficultyFilter = [DifficultyLevel.HARD];

        const resultItemWithDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_2, difficultyFilter);
        expect(resultItemWithDifficulty).toBeFalse();

        const resultItemWithoutDifficulty = satisfiesDifficultyFilter(SIDEBAR_CARD_ELEMENT_4, difficultyFilter);
        expect(resultItemWithoutDifficulty).toBeFalse();
    });
});

describe('satisfiesCategoryFilter', () => {
    it('should return true if difficulty filter is []', () => {
        const categoryFilter: ExerciseCategory[] = [];

        const resultItemWithCategory = satisfiesCategoryFilter(SIDEBAR_CARD_ELEMENT_1, categoryFilter);
        expect(resultItemWithCategory).toBeTrue();

        const resultItemWithoutCategory = satisfiesCategoryFilter(SIDEBAR_CARD_ELEMENT_5, categoryFilter);
        expect(resultItemWithoutCategory).toBeTrue();
    });

    it('should return true category is included in difficulty filter', () => {
        const categoryFilter = [new ExerciseCategory('category1', '#691b0b')];

        const resultItemWithMatchingCategory = satisfiesCategoryFilter(SIDEBAR_CARD_ELEMENT_1, categoryFilter);
        expect(resultItemWithMatchingCategory).toBeTrue();
    });

    it('should return false if difficulty is NOT in difficulty filter', () => {
        const categoryFilter = [new ExerciseCategory('notExistingCategory', '#691b0b')];

        const resultItemWithCategory = satisfiesCategoryFilter(SIDEBAR_CARD_ELEMENT_1, categoryFilter);
        expect(resultItemWithCategory).toBeFalse();

        const resultItemWithoutCategory = satisfiesCategoryFilter(SIDEBAR_CARD_ELEMENT_5, categoryFilter);
        expect(resultItemWithoutCategory).toBeFalse();
    });
});

describe('satisfiesScoreFilter', () => {
    it('should return true if score filter is undefined', () => {
        const scoreFilter = undefined;

        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_1, true, scoreFilter);
        expect(resultItemWithScore).toBeTrue();

        const resultItemWithoutScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_4, true, scoreFilter);
        expect(resultItemWithoutScore).toBeTrue();
    });

    it('should return true if score filter is not applied', () => {
        const scoreFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 0,
                selectedMax: 1,
                generalMin: 0,
                generalMax: 1,
                step: 1,
            },
        };

        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_1, false, scoreFilter);
        expect(resultItemWithScore).toBeTrue();

        const resultItemWithoutScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_4, false, scoreFilter);
        expect(resultItemWithoutScore).toBeTrue();
    });

    it('should return true if score is in score filter', () => {
        const scoreFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 5,
                selectedMax: 10,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };

        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_1, true, scoreFilter);
        expect(resultItemWithScore).toBeTrue();
    });

    it('should return false if score is NOT in score filter', () => {
        const scoreFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 20,
                selectedMax: 30,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };

        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_1, true, scoreFilter);
        expect(resultItemWithScore).toBeFalse();

        const resultItemWithoutScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_4, true, scoreFilter);
        expect(resultItemWithoutScore).toBeFalse();
    });

    it('should return true if score of participation is not defined (not participated) and lower bound is 0', () => {
        const scoreFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 0,
                selectedMax: 10,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };
        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_4, true, scoreFilter);
        expect(resultItemWithScore).toBeTrue();
    });

    it('should return false if score of participation is not defined (not participated) and lower bound is NOT 0', () => {
        const scoreFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 1,
                selectedMax: 10,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };
        const resultItemWithScore = satisfiesScoreFilter(SIDEBAR_CARD_ELEMENT_4, true, scoreFilter);
        expect(resultItemWithScore).toBeFalse();
    });
});

describe('satisfiesPointsFilter', () => {
    it('should return true if points filter is undefined', () => {
        const pointsFilter = undefined;

        const resultItemWithPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_1, true, pointsFilter);
        expect(resultItemWithPoints).toBeTrue();

        const resultItemWithoutPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_4, true, pointsFilter);
        expect(resultItemWithoutPoints).toBeTrue();
    });

    it('should return true if points filter is not applied', () => {
        const pointsFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 0,
                selectedMax: 1,
                generalMin: 0,
                generalMax: 1,
                step: 1,
            },
        };

        const resultItemWithPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_1, false, pointsFilter);
        expect(resultItemWithPoints).toBeTrue();

        const resultItemWithoutPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_4, false, pointsFilter);
        expect(resultItemWithoutPoints).toBeTrue();
    });

    it('should return true if points is in points filter', () => {
        const pointsFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 9,
                selectedMax: 10,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };

        const resultItemWithPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_1, true, pointsFilter);
        expect(resultItemWithPoints).toBeTrue();
    });

    it('should return false if points is NOT in points filter', () => {
        const pointsFilter: RangeFilter = {
            isDisplayed: true,
            filter: {
                selectedMin: 11,
                selectedMax: 12,
                generalMin: 0,
                generalMax: 80,
                step: 1,
            },
        };

        const resultItemWithPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_1, true, pointsFilter);
        expect(resultItemWithPoints).toBeFalse();

        const resultItemWithoutPoints = satisfiesPointsFilter(SIDEBAR_CARD_ELEMENT_4, true, pointsFilter);
        expect(resultItemWithoutPoints).toBeFalse();
    });
});

describe('satisfiesFilters', () => {
    it('should return true if item satisfies filters', () => {
        const filter: FilterDetails = {
            selectedCategories: [new ExerciseCategory('category1', '#691b0b')],
            searchedTypes: [ExerciseType.TEXT],
            searchedDifficulties: [DifficultyLevel.HARD],
            isScoreFilterApplied: false,
            isPointsFilterApplied: false,
            achievedScore: {
                isDisplayed: true,
                filter: {
                    selectedMin: 5,
                    selectedMax: 10,
                    generalMin: 0,
                    generalMax: 80,
                    step: 1,
                },
            },
            achievablePoints: {
                isDisplayed: true,
                filter: {
                    selectedMin: 9,
                    selectedMax: 10,
                    generalMin: 0,
                    generalMax: 80,
                    step: 1,
                },
            },
        };
        const resultItem = satisfiesFilters(SIDEBAR_CARD_ELEMENT_1, filter);

        expect(resultItem).toBeTrue();
    });

    it('should return false if item does not satisfy the score filter', () => {
        const filter: FilterDetails = {
            selectedCategories: [new ExerciseCategory('category1', '#691b0b')],
            searchedTypes: [ExerciseType.TEXT],
            searchedDifficulties: [DifficultyLevel.HARD],
            isScoreFilterApplied: false,
            isPointsFilterApplied: false,
            achievedScore: {
                isDisplayed: true,
                filter: {
                    selectedMin: 69,
                    selectedMax: 70,
                    generalMin: 0,
                    generalMax: 80,
                    step: 1,
                },
            },
            achievablePoints: {
                isDisplayed: true,
                filter: {
                    selectedMin: 9,
                    selectedMax: 10,
                    generalMin: 0,
                    generalMax: 80,
                    step: 1,
                },
            },
        };
        const resultItem = satisfiesFilters(SIDEBAR_CARD_ELEMENT_1, filter);

        expect(resultItem).toBeTrue();
    });
});
