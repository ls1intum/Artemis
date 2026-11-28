import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FeedbackFilterModalComponent } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-filter/feedback-filter-modal.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { RangeSliderComponent } from 'app/shared-ui/range-slider/range-slider.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('FeedbackFilterModalComponent', () => {
    let fixture: ComponentFixture<FeedbackFilterModalComponent>;
    let component: FeedbackFilterModalComponent;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RangeSliderComponent, FeedbackFilterModalComponent],
            providers: [
                { provide: LocalStorageService, useValue: { store: vi.fn(), remove: vi.fn(), retrieve: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackFilterModalComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);
        fixture.componentRef.setInput('minCount', 0);
        fixture.componentRef.setInput('maxCount', 10);
        fixture.detectChanges();
    });

    it('should initialize filters correctly', () => {
        component.filters = {
            tasks: [],
            testCases: [],
            occurrence: [component.minCount(), component.maxCount()],
            errorCategories: [],
        };

        expect(component.filters).toEqual({
            tasks: [],
            testCases: [],
            occurrence: [0, 10],
            errorCategories: [],
        });
    });

    it('should call localStorage store when applying filters', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');
        const emitSpy = vi.spyOn(component.filterApplied, 'emit');
        const closeSpy = vi.fn();
        component.closed.subscribe(closeSpy);

        component.filters.occurrence = [component.minCount(), component.maxCount()];
        component.filters.errorCategories = ['Student Error', 'Ares Error'];
        component.applyFilter();

        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_TASKS_KEY, []);
        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_TEST_CASES_KEY, []);
        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_OCCURRENCE_KEY, [0, 10]);
        expect(storeSpy).toHaveBeenCalledWith(component.FILTER_ERROR_CATEGORIES_KEY, ['Student Error', 'Ares Error']);
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should clear filters and reset them correctly', () => {
        const removeSpy = vi.spyOn(localStorageService, 'remove');
        const emitSpy = vi.spyOn(component.filterApplied, 'emit');
        const closeSpy = vi.fn();
        component.closed.subscribe(closeSpy);

        component.clearFilter();

        expect(removeSpy).toHaveBeenCalledWith(component.FILTER_TASKS_KEY);
        expect(removeSpy).toHaveBeenCalledWith(component.FILTER_TEST_CASES_KEY);
        expect(removeSpy).toHaveBeenCalledWith(component.FILTER_OCCURRENCE_KEY);
        expect(removeSpy).toHaveBeenCalledWith(component.FILTER_ERROR_CATEGORIES_KEY);

        expect(component.filters).toEqual({
            tasks: [],
            testCases: [],
            occurrence: [0, 10],
            errorCategories: [],
        });
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledOnce();
    });

    it('should update filters when checkboxes change for tasks', () => {
        component.onFilterToggle(true, 'tasks', 'test-task');
        expect(component.filters.tasks).toEqual(['test-task']);
    });

    it('should update filters when checkboxes change for errorCategories', () => {
        component.onFilterToggle(true, 'errorCategories', 'Student Error');
        expect(component.filters.errorCategories).toEqual(['Student Error']);
    });

    it('should start from the provided filters and toggle them through the rendered checkboxes', () => {
        fixture = TestBed.createComponent(FeedbackFilterModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('taskArray', ['Task 1', 'Task 2']);
        fixture.componentRef.setInput('maxCount', 10);
        fixture.componentRef.setInput('initialFilters', { tasks: ['Task 2'], testCases: [], occurrence: [0, 10], errorCategories: [] });
        fixture.detectChanges();

        const checkboxes = Array.from(document.querySelectorAll<HTMLInputElement>('[role="dialog"] input[type="checkbox"]')).slice(-2);
        expect(checkboxes.map((checkbox) => checkbox.checked)).toEqual([false, true]);

        checkboxes[0].click();
        fixture.detectChanges();
        expect(component.filters.tasks).toEqual(['Task 2', 'Task 1']);
    });

    it('should remove the value from filters when checkbox is unchecked for tasks', () => {
        component.filters.tasks = ['test-task', 'task-2'];
        component.onFilterToggle(false, 'tasks', 'test-task');
        expect(component.filters.tasks).toEqual(['task-2']);
    });

    it('should remove the value from filters when checkbox is unchecked for errorCategories', () => {
        component.filters.errorCategories = ['Student Error', 'Ares Error'];
        component.onFilterToggle(false, 'errorCategories', 'Student Error');
        expect(component.filters.errorCategories).toEqual(['Ares Error']);
    });

    it('should dismiss modal when closeModal is called', () => {
        const dismissSpy = vi.fn();
        component.closed.subscribe(dismissSpy);
        component.closeModal();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });
});
