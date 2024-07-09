import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseFilterModalComponent } from 'app/shared/exercise-filter/exercise-filter-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge.component';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { RangeFilter } from 'app/types/exercise-filter';
import { DifficultyLevel, ExerciseType, getIcon } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

const SCORE_FILTER: RangeFilter = {
    isDisplayed: true,
    filter: {
        generalMin: 0,
        generalMax: 75,
        selectedMin: 0,
        selectedMax: 75,
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
    });

    beforeEach(() => {
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
});
