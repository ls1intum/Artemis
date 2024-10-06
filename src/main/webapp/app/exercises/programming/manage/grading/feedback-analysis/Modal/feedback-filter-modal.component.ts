import { Component, EventEmitter, Output, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RangeSliderComponent } from 'app/shared/range-slider/range-slider.component';
import { FeedbackAnalysisService } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LocalStorageService } from 'ngx-webstorage';

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
    filterForm: FormGroup;
    @Output() filterApplied = new EventEmitter<any>();

    private FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    private FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    private FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';

    totalAmountOfTasks = signal<number>(0);
    testCaseNames = signal<string[]>([]);
    maxCount = signal<number>(0);

    constructor() {
        this.filterForm = this.fb.group({
            tasks: [],
            testCases: [],
            occurrence: [],
        });
    }

    generateTaskArray(): number[] {
        return Array.from({ length: this.totalAmountOfTasks() }, (_, i) => i + 1);
    }

    get occurrence(): number[] {
        return this.filterForm.get('occurrence')?.value;
    }

    set occurrence(value: number[]) {
        this.filterForm.get('occurrence')?.setValue(value);
    }

    applyFilter(): void {
        const filters = this.filterForm.value;

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
            occurrence: [1, this.maxCount()],
        });

        this.filterApplied.emit(this.filterForm.value);
        this.activeModal.close();
    }

    onCheckboxChange(event: Event, controlName: string): void {
        const checkbox = event.target as HTMLInputElement;
        const values = this.filterForm.value[controlName];
        if (checkbox.checked) {
            values.push(checkbox.value);
        } else {
            const index = values.indexOf(checkbox.value);
            if (index >= 0) {
                values.splice(index, 1);
            }
        }
        const patch: { [key: string]: any } = {};
        patch[controlName] = values;
        this.filterForm.patchValue(patch);
    }

    closeModal(): void {
        this.activeModal.dismiss();
    }
}
