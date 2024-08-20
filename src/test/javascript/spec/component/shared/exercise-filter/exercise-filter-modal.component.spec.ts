import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { RangeFilter } from 'app/types/exercise-filter';
import { DifficultyLevel, Exercise, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SidebarCardElement } from 'app/types/sidebar';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

const EXERCISE_1 = { categories: [new ExerciseCategory('category1', undefined), new ExerciseCategory('category2', '#1b97ca')], maxPoints: 5, type: ExerciseType.TEXT } as Exercise;
const EXERCISE_2 = {
    categories: [new ExerciseCategory('category3', '#0d3cc2'), new ExerciseCategory('category4', '#6ae8ac')],
    maxPoints: 5,
    type: ExerciseType.PROGRAMMING,
} as Exercise;
const EXERCISE_3 = {
    categories: [new ExerciseCategory('category1', undefined), new ExerciseCategory('category4', '#6ae8ac')],
    maxPoints: 8,
    type: ExerciseType.PROGRAMMING,
} as Exercise;

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
    studentParticipation: { results: [{ score: 5.0 } as Result] } as StudentParticipation,
} as SidebarCardElement;
const SIDEBAR_CARD_ELEMENT_3 = {
    exercise: EXERCISE_3,
    type: ExerciseType.PROGRAMMING,
    difficulty: DifficultyLevel.EASY,
    studentParticipation: { results: [{ score: 82.3 } as Result] } as StudentParticipation,
} as SidebarCardElement;

const SCORE_FILTER: RangeFilter = {
    isDisplayed: true,
    filter: {
        generalMin: 0,
        generalMax: 100,
        selectedMin: 0,
        selectedMax: 100,
        step: 5,
    },
};

const POINTS_FILTER: RangeFilter = {
    isDisplayed: true,
    filter: {
        generalMin: 0,
        generalMax: 20,
        selectedMin: 0,
        selectedMax: 20,
        step: 1,
    },
};

describe('ExerciseFilterModalComponent', () => {
    let component: ExerciseFilterModalComponent;
    let fixture: ComponentFixture<ExerciseFilterModalComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                MockModule(FontAwesomeModule),
                MockModule(ArtemisSharedCommonModule),
                MockModule(ArtemisSharedComponentModule),
            ],
            declarations: [ExerciseFilterModalComponent, MockComponent(CustomExerciseCategoryBadgeComponent), MockComponent(RangeSliderComponent)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseFilterModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);

        component.exerciseFilters = {
            exerciseTypesFilter: {
                isDisplayed: true,
                options: [
                    { name: 'artemisApp.courseStatistics.programming', value: ExerciseType.PROGRAMMING, checked: false, icon: getIcon(ExerciseType.PROGRAMMING) },
                    { name: 'artemisApp.courseStatistics.quiz', value: ExerciseType.QUIZ, checked: false, icon: getIcon(ExerciseType.QUIZ) },
                    { name: 'artemisApp.courseStatistics.modeling', value: ExerciseType.MODELING, checked: false, icon: getIcon(ExerciseType.MODELING) },
                    { name: 'artemisApp.courseStatistics.text', value: ExerciseType.TEXT, checked: false, icon: getIcon(ExerciseType.TEXT) },
                    { name: 'artemisApp.courseStatistics.file-upload', value: ExerciseType.FILE_UPLOAD, checked: false, icon: getIcon(ExerciseType.FILE_UPLOAD) },
                ],
            },
            difficultyFilter: {
                isDisplayed: true,
                options: [
                    { name: 'artemisApp.exercise.easy', value: DifficultyLevel.EASY, checked: false },
                    { name: 'artemisApp.exercise.medium', value: DifficultyLevel.MEDIUM, checked: false },
                    { name: 'artemisApp.exercise.hard', value: DifficultyLevel.HARD, checked: false },
                ],
            },
            categoryFilter: {
                isDisplayed: true,
                options: [
                    { category: new ExerciseCategory('category1', undefined), searched: false },
                    { category: new ExerciseCategory('category2', undefined), searched: false },
                ],
            },
            achievedScore: SCORE_FILTER,
            achievablePoints: POINTS_FILTER,
        };

        fixture.detectChanges();
    });

    it('should initialize filters properly', () => {
        expect(component.categoryFilter).toEqual(component.exerciseFilters?.categoryFilter);
        expect(component.typeFilter).toEqual(component.exerciseFilters?.exerciseTypesFilter);
        expect(component.difficultyFilter).toEqual(component.exerciseFilters?.difficultyFilter);
        expect(component.achievedScore).toEqual(component.exerciseFilters?.achievedScore);
        expect(component.achievablePoints).toEqual(component.exerciseFilters?.achievablePoints);
    });

    describe('should close modal', () => {
        it('with button in upper right corner on click', () => {
            const closeSpy = jest.spyOn(activeModal, 'close');
            const closeModalSpy = jest.spyOn(component, 'closeModal');

            const closeButton = fixture.debugElement.query(By.css('.btn-close'));
            expect(closeButton).not.toBeNull();

            closeButton.nativeElement.click();
            expect(closeSpy).toHaveBeenCalledOnce();
            expect(closeModalSpy).toHaveBeenCalledOnce();
        });

        it('with button in lower right corner on click', () => {
            const closeSpy = jest.spyOn(activeModal, 'close');
            const closeModalSpy = jest.spyOn(component, 'closeModal');

            const cancelButton = fixture.debugElement.query(By.css('button[jhiTranslate="entity.action.cancel"]'));
            expect(cancelButton).not.toBeNull();

            cancelButton.nativeElement.click();
            expect(closeSpy).toHaveBeenCalledOnce();
            expect(closeModalSpy).toHaveBeenCalledOnce();
        });
    });

    describe('select category', () => {
        it('should mark a category as selected when category is found', () => {
            expect(component.categoryFilter?.options[0].searched).toBeFalse(); // if it is not false in the beginning we do not test anything here
            const onSelectItemSpy = jest.spyOn(component, 'onSelectItem');

            component.model = 'category1';
            // Simulate selecting an item
            const event = {
                item: component.selectableCategoryOptions[0],
                preventDefault: jest.fn(),
            };
            component.onSelectItem(event);
            fixture.detectChanges();

            expect(onSelectItemSpy).toHaveBeenCalledOnce();
            expect(component.categoryFilter?.options[0].searched).toBeTrue();
            expect(component.model).toBeUndefined(); // Clear the input field after selection
        });

        it('should not change category filter when no item is provided', () => {
            expect(component.categoryFilter?.options[0].searched).toBeFalse(); // if it is not false in the beginning we do not test anything here
            const onSelectItemSpy = jest.spyOn(component, 'onSelectItem');

            component.model = 'categoryThatIsNotDefinedAndSearchedViaEnter';
            const event = {
                item: undefined,
                preventDefault: jest.fn(),
                stopPropagation: jest.fn(),
            };
            component.onSelectItem(event);
            fixture.detectChanges();

            expect(onSelectItemSpy).toHaveBeenCalledOnce();
            expect(component.categoryFilter?.options[0].searched).toBeFalse();
            expect(component.model).toBe('categoryThatIsNotDefinedAndSearchedViaEnter');
        });
    });

    it('should reset all filters when button is clicked', () => {
        component.categoryFilter!.options[0].searched = true;
        component.categoryFilter!.options[1].searched = true;
        component.typeFilter!.options[0].checked = true;
        component.typeFilter!.options[1].checked = true;
        component.difficultyFilter!.options[0].checked = true;
        component.difficultyFilter!.options[1].checked = false;
        component.achievablePoints!.filter.selectedMax = 10;
        component.achievedScore!.filter.selectedMin = 10;
        const resetFilterSpy = jest.spyOn(component, 'clearFilter');

        const resetButton = fixture.debugElement.query(By.css('span[jhiTranslate="artemisApp.courseOverview.exerciseFilter.clearFilter"]'));
        expect(resetButton).not.toBeNull();
        resetButton.nativeElement.click();

        expect(resetFilterSpy).toHaveBeenCalledOnce();
        expect(component.categoryFilter!.options[0].searched).toBeFalse();
        expect(component.categoryFilter!.options[1].searched).toBeFalse();
        expect(component.typeFilter!.options[0].checked).toBeFalse();
        expect(component.typeFilter!.options[1].checked).toBeFalse();
        expect(component.difficultyFilter!.options[0].checked).toBeFalse();
        expect(component.difficultyFilter!.options[1].checked).toBeFalse();
        expect(component.achievablePoints!.filter.selectedMax).toBe(component.achievablePoints?.filter.generalMax);
        expect(component.achievedScore!.filter.selectedMin).toBe(component.achievedScore?.filter.generalMin);
    });

    it('should apply filters, emit the correct sidebar data and close the modal', () => {
        component.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exercise',
            groupedData: {
                past: { entityData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3] },
            },
            ungroupedData: [SIDEBAR_CARD_ELEMENT_1, SIDEBAR_CARD_ELEMENT_2, SIDEBAR_CARD_ELEMENT_3],
        };
        component.categoryFilter!.options[0].searched = true; // must have 'category1'
        component.typeFilter!.options[0].checked = true; // must be a programming exercise
        component.difficultyFilter!.options[0].checked = true; // must be easy
        component.achievablePoints!.filter.selectedMax = 10;
        component.achievedScore!.filter.selectedMin = 10;

        const filterAppliedEmitSpy = jest.spyOn(component.filterApplied, 'emit');
        const applyFilterSpy = jest.spyOn(component, 'applyFilter');
        const closeModalSpy = jest.spyOn(component, 'closeModal');
        const applyButton = fixture.debugElement.query(By.css('button[jhiTranslate="artemisApp.courseOverview.exerciseFilter.applyFilter"]'));
        expect(applyButton).not.toBeNull();
        applyButton.nativeElement.click();

        expect(applyFilterSpy).toHaveBeenCalledOnce();
        expect(filterAppliedEmitSpy).toHaveBeenCalledOnce();
        expect(filterAppliedEmitSpy).toHaveBeenCalledWith({
            filteredSidebarData: component.sidebarData,
            appliedExerciseFilters: component.exerciseFilters,
            isFilterActive: true,
        });
        /** only {@link EXERCISE_3} fullfills the filter options and should be emitted in the event */
        expect(component.sidebarData.ungroupedData?.length).toBe(1);

        expect(closeModalSpy).toHaveBeenCalledOnce();
    });
});
