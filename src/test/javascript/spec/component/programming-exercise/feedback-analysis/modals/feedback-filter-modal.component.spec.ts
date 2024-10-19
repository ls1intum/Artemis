import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackFilterModalComponent } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';

describe('FeedbackFilterModalComponent', () => {
    let fixture: ComponentFixture<FeedbackFilterModalComponent>;
    let component: FeedbackFilterModalComponent;
    let localStorageService: LocalStorageService;
    let activeModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisSharedCommonModule, RangeSliderComponent, FeedbackFilterModalComponent],
            providers: [{ provide: LocalStorageService, useValue: { store: jest.fn(), clear: jest.fn(), retrieve: jest.fn() } }, NgbActiveModal],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackFilterModalComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);
        activeModal = TestBed.inject(NgbActiveModal);
        component.minCount.set(0);
        component.maxCount.set(10);
        fixture.detectChanges();
    });

    it('should initialize filters correctly', () => {
        component.filters = {
            tasks: [],
            testCases: [],
            occurrence: [component.minCount(), component.maxCount()],
        };

        expect(component.filters).toEqual({
            tasks: [],
            testCases: [],
            occurrence: [0, 10],
        });
    });

    it('should call localStorage store when applying filters', () => {
        const storeSpy = jest.spyOn(localStorageService, 'store');
        const emitSpy = jest.spyOn(component.filterApplied, 'emit');
        const closeSpy = jest.spyOn(activeModal, 'close');

        component.filters.occurrence = [component.minCount(), component.maxCount()];
        component.applyFilter();

        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_TASKS_KEY, []);
        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_TEST_CASES_KEY, []);
        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_OCCURRENCE_KEY, [0, 10]);
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should clear filters and reset them correctly', () => {
        const clearSpy = jest.spyOn(localStorageService, 'clear');
        const emitSpy = jest.spyOn(component.filterApplied, 'emit');
        const closeSpy = jest.spyOn(activeModal, 'close');

        component.clearFilter();

        expect(clearSpy).toHaveBeenCalledWith(component.FILTER_TASKS_KEY);
        expect(clearSpy).toHaveBeenCalledWith(component.FILTER_TEST_CASES_KEY);
        expect(clearSpy).toHaveBeenCalledWith(component.FILTER_OCCURRENCE_KEY);

        expect(component.filters).toEqual({
            tasks: [],
            testCases: [],
            occurrence: [0, 10],
        });
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should update filters when checkboxes change', () => {
        const event = { target: { checked: true, value: 'test-task' } } as unknown as Event;
        component.onCheckboxChange(event, 'tasks');
        expect(component.filters.tasks).toEqual(['test-task']);
    });

    it('should remove the value from filters when checkbox is unchecked', () => {
        component.filters.tasks = ['test-task', 'task-2'];
        const event = { target: { checked: false, value: 'test-task' } } as unknown as Event;
        component.onCheckboxChange(event, 'tasks');
        expect(component.filters.tasks).toEqual(['task-2']);
    });

    it('should dismiss modal when closeModal is called', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        component.closeModal();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
