import { Component, inject, output, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { RangeSliderComponent } from 'app/shared-ui/range-slider/range-slider.component';
import { FeedbackAnalysisService } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';

export interface FilterData {
    tasks: string[];
    testCases: string[];
    occurrence: number[];
    errorCategories: string[];
}

@Component({
    selector: 'jhi-feedback-filter-modal',
    templateUrl: './feedback-filter-modal.component.html',
    imports: [RangeSliderComponent, TranslateDirective],
    providers: [FeedbackAnalysisService],
})
export class FeedbackFilterModalComponent {
    private localStorageService = inject(LocalStorageService);
    private activeModal = inject(NgbActiveModal);

    filterApplied = output<FilterData>();

    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.filterModal';
    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';
    readonly FILTER_ERROR_CATEGORIES_KEY = 'feedbackAnalysis.errorCategories';

    readonly testCaseNames = signal<string[]>([]);
    readonly minCount = signal<number>(0);
    readonly maxCount = signal<number>(0);
    readonly taskArray = signal<string[]>([]);
    readonly errorCategories = signal<string[]>([]);

    // filters is a deep two-way binding target (jhi-range-slider writes back to filters.occurrence[0]/[1]) and is
    // mutated in place by onCheckboxChange, so it is backed by a signal via a getter/setter facade. Reads stay
    // reactive; commitFilters() rebuilds the reference after in-place mutations the template depends on.
    private readonly _filters = signal<FilterData>({
        tasks: [],
        testCases: [],
        occurrence: [this.minCount(), this.maxCount() || 1],
        errorCategories: [],
    });
    get filters(): FilterData {
        return this._filters();
    }
    set filters(value: FilterData) {
        this._filters.set(value);
    }
    private commitFilters(): void {
        this._filters.update((filters) => ({ ...filters }));
    }

    applyFilter(): void {
        this.localStorageService.store(this.FILTER_TASKS_KEY, this.filters.tasks);
        this.localStorageService.store(this.FILTER_TEST_CASES_KEY, this.filters.testCases);
        this.localStorageService.store(this.FILTER_OCCURRENCE_KEY, this.filters.occurrence);
        this.localStorageService.store(this.FILTER_ERROR_CATEGORIES_KEY, this.filters.errorCategories);
        this.filterApplied.emit(this.filters);
        this.activeModal.close();
    }

    clearFilter(): void {
        this.localStorageService.remove(this.FILTER_TASKS_KEY);
        this.localStorageService.remove(this.FILTER_TEST_CASES_KEY);
        this.localStorageService.remove(this.FILTER_OCCURRENCE_KEY);
        this.localStorageService.remove(this.FILTER_ERROR_CATEGORIES_KEY);
        this.filters = {
            tasks: [],
            testCases: [],
            occurrence: [this.minCount(), this.maxCount()],
            errorCategories: [],
        };
        this.filterApplied.emit(this.filters);
        this.activeModal.close();
    }

    onCheckboxChange(event: Event, controlName: keyof FilterData): void {
        const checkbox = event.target as HTMLInputElement;
        const values = this.filters[controlName];

        if (controlName === 'occurrence') {
            const numericValue = Number(checkbox.value);
            this.pushValue(checkbox, values as number[], numericValue);
        } else {
            this.pushValue(checkbox, values as string[], checkbox.value);
        }
        // The checkbox [checked] bindings read filters.*; rebuild the reference so the signal fires after the in-place mutation.
        this.commitFilters();
    }

    private pushValue<T>(checkbox: HTMLInputElement, values: T[], valueToAddOrRemove: T): void {
        if (checkbox.checked) {
            values.push(valueToAddOrRemove);
        } else {
            const index = values.indexOf(valueToAddOrRemove);
            if (index >= 0) {
                values.splice(index, 1);
            }
        }
    }

    closeModal(): void {
        this.activeModal.dismiss();
    }
}
