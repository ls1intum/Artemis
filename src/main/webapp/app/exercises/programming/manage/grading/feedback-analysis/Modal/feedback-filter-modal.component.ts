import { Component, computed, inject, output, signal } from '@angular/core';
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

    constructor() {
        this.filterForm = this.fb.group({
            tasks: [[]],
            testCases: [[]],
            occurrence: [[this.minCount(), this.maxCount() || 1]],
        });
    }

    get occurrence(): number[] {
        return this.filterForm.get('occurrence')?.value;
    }

    set occurrence(value: number[]) {
        this.filterForm.get('occurrence')?.setValue(value);
    }

    applyFilter(): void {
        const filters: FilterData = this.filterForm.value;
        this.localStorage.store(this.FILTER_TASKS_KEY, filters.tasks);
        this.localStorage.store(this.FILTER_TEST_CASES_KEY, filters.testCases);
        this.localStorage.store(this.FILTER_OCCURRENCE_KEY, filters.occurrence);
        this.filterApplied.emit(filters);
        this.activeModal.close();
    }

    clearFilter(): void {
        this.localStorage.clear(this.FILTER_TASKS_KEY);
        this.localStorage.clear(this.FILTER_TEST_CASES_KEY);
        this.localStorage.clear(this.FILTER_OCCURRENCE_KEY);
        this.filterForm.reset({
            tasks: [],
            testCases: [],
            occurrence: [this.minCount(), this.maxCount()],
        });
        this.filterApplied.emit(this.filterForm.value as FilterData);
        this.activeModal.close();
    }

    onCheckboxChange(event: Event, controlName: keyof FilterData): void {
        const checkbox = event.target as HTMLInputElement;

        if (controlName === 'occurrence') {
            const values = this.filterForm.value[controlName] as number[];
            const numericValue = Number(checkbox.value);
            if (checkbox.checked) {
                values.push(numericValue);
            } else {
                const index = values.indexOf(numericValue);
                if (index >= 0) {
                    values.splice(index, 1);
                }
            }
            this.filterForm.patchValue({ occurrence: values });
        } else {
            const values = this.filterForm.value[controlName] as string[];
            if (checkbox.checked) {
                values.push(checkbox.value);
            } else {
                const index = values.indexOf(checkbox.value);
                if (index >= 0) {
                    values.splice(index, 1);
                }
            }
            const patch: Partial<FilterData> = {};
            patch[controlName] = values;
            this.filterForm.patchValue(patch);
        }
    }

    closeModal(): void {
        this.activeModal.dismiss();
    }
}
