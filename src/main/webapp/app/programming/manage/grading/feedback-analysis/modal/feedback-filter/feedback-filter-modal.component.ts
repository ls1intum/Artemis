import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiCheckboxComponent, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { RangeSliderComponent } from 'app/shared-ui/range-slider/range-slider.component';
import { FeedbackAnalysisService } from 'app/programming/manage/grading/feedback-analysis/service/feedback-analysis.service';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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
    imports: [RangeSliderComponent, TranslateDirective, ArtemisTranslatePipe, TumAetUiDialogComponent, TumAetUiButtonDirective, TumAetUiCheckboxComponent],
    providers: [FeedbackAnalysisService],
})
export class FeedbackFilterModalComponent implements OnInit {
    private localStorageService = inject(LocalStorageService);

    filterApplied = output<FilterData>();
    /** Emits once the dialog was closed, whether filters were applied or not, so the host can remove it. */
    closed = output<void>();

    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.filterModal';
    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';
    readonly FILTER_ERROR_CATEGORIES_KEY = 'feedbackAnalysis.errorCategories';

    readonly testCaseNames = input<string[]>([]);
    readonly minCount = input<number>(0);
    readonly maxCount = input<number>(0);
    readonly taskArray = input<string[]>([]);
    readonly errorCategories = input<string[]>([]);
    /** The filters to start from; the dialog edits this object in place and reports it through `filterApplied`. */
    readonly initialFilters = input<FilterData>();

    // filters is a deep two-way binding target (jhi-range-slider writes back to filters.occurrence[0]/[1]) and is
    // mutated in place by onFilterToggle, so it is backed by a signal via a getter/setter facade. Reads stay
    // reactive; commitFilters() rebuilds the reference after in-place mutations the template depends on.
    // `equal: () => false` so re-setting the same reference emits after the template mutates the filter arrays
    // in place; copying them would detach what the two-way bindings write into.
    private readonly _filters = signal<FilterData>(
        {
            tasks: [],
            testCases: [],
            occurrence: [this.minCount(), this.maxCount() || 1],
            errorCategories: [],
        },
        { equal: () => false },
    );
    get filters(): FilterData {
        return this._filters();
    }
    set filters(value: FilterData) {
        this._filters.set(value);
    }
    private commitFilters(): void {
        // No copy: the signal is declared with `equal: () => false`, so re-setting the same reference emits. The
        // template two-way binds into the filter arrays, which must stay the same instances.
        this._filters.set(this._filters());
    }

    ngOnInit(): void {
        const initialFilters = this.initialFilters();
        if (initialFilters) {
            this.filters = initialFilters;
        }
    }

    applyFilter(): void {
        this.localStorageService.store(this.FILTER_TASKS_KEY, this.filters.tasks);
        this.localStorageService.store(this.FILTER_TEST_CASES_KEY, this.filters.testCases);
        this.localStorageService.store(this.FILTER_OCCURRENCE_KEY, this.filters.occurrence);
        this.localStorageService.store(this.FILTER_ERROR_CATEGORIES_KEY, this.filters.errorCategories);
        this.filterApplied.emit(this.filters);
        this.closed.emit();
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
        this.closed.emit();
    }

    /**
     * Adds the value to, or removes it from, one of the text filters.
     */
    onFilterToggle(checked: boolean, controlName: 'tasks' | 'testCases' | 'errorCategories', value: string): void {
        this.pushValue(checked, this.filters[controlName], value);
        // The checkbox [checked] bindings read filters.*; re-set the reference so the signal fires after the in-place mutation.
        this.commitFilters();
    }

    private pushValue<T>(checked: boolean, values: T[], valueToAddOrRemove: T): void {
        if (checked) {
            values.push(valueToAddOrRemove);
        } else {
            const index = values.indexOf(valueToAddOrRemove);
            if (index >= 0) {
                values.splice(index, 1);
            }
        }
    }

    closeModal(): void {
        this.closed.emit();
    }

    protected onVisibleChange(visible: boolean): void {
        if (!visible) {
            this.closeModal();
        }
    }
}
