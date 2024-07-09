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
        fixture.detectChanges();
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
});
