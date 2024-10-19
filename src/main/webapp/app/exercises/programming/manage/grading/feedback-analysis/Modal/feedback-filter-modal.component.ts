import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import { FeedbackAnalysisService } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LocalStorageService } from 'ngx-webstorage';

export interface FilterData {
    tasks: string[];
    testCases: string[];
    occurrence: number[];
}
@Component({
    selector: 'jhi-feedback-filter-modal',
    templateUrl: './feedback-filter-modal.component.html',
    imports: [RangeSliderComponent, ArtemisSharedCommonModule, ReactiveFormsModule],
    providers: [FeedbackAnalysisService],
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FeedbackFilterModalComponent {
    private localStorage = inject(LocalStorageService);
    private activeModal = inject(NgbActiveModal);
    private fb = inject(FormBuilder);
    filterApplied = output<FilterData>();
    readonly filterForm: FormGroup;

    readonly FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    readonly FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    readonly FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';

    readonly totalAmountOfTasks = signal<number>(0);
    readonly testCaseNames = signal<string[]>([]);
    readonly minCount = signal<number>(0);
    readonly maxCount = signal<number>(0);
    readonly taskArray = computed(() => Array.from({ length: this.totalAmountOfTasks() }, (_, i) => i + 1));

    filters: FilterData = {
        tasks: [],
        testCases: [],
        occurrence: [this.minCount(), this.maxCount() || 1],
    };

    constructor() {
        this.filterForm = this.fb.group({
            tasks: [[]],
            testCases: [[]],
            occurrence: [[this.minCount(), this.maxCount() || 1]],
        });
    }

    applyFilter(): void {
        this.localStorage.store(this.FILTER_TASKS_KEY, this.filters.tasks);
        this.localStorage.store(this.FILTER_TEST_CASES_KEY, this.filters.testCases);
        this.localStorage.store(this.FILTER_OCCURRENCE_KEY, this.filters.occurrence);
        this.filterApplied.emit(this.filters);
        this.activeModal.close();
    }

    clearFilter(): void {
        this.localStorage.clear(this.FILTER_TASKS_KEY);
        this.localStorage.clear(this.FILTER_TEST_CASES_KEY);
        this.localStorage.clear(this.FILTER_OCCURRENCE_KEY);
        this.filters = {
            tasks: [],
            testCases: [],
            occurrence: [this.minCount(), this.maxCount()],
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
