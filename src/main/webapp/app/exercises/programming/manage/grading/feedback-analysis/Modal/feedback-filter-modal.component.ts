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
    @Output() filterApplied = new EventEmitter<any>();

    totalAmountOfTasks = signal<number>(0);
    testCaseNames = signal<string[]>([]);

    filterForm: FormGroup;

    private FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    private FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    private FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';

    constructor(
        private activeModal: NgbActiveModal,
        private fb: FormBuilder,
    ) {
        // Initialize form without any default values, values are set dynamically
        this.filterForm = this.fb.group({
            tasks: [[]], // Array to hold selected tasks
            testCases: [[]], // Array to hold selected test cases
            occurrence: [[0, 100]], // Default occurrence range
        });
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
            occurrence: [0, 100],
        });

        this.filterApplied.emit(this.filterForm.value);
        this.activeModal.close();
    }

    closeModal(): void {
        this.activeModal.dismiss();
    }

    get occurrence(): number[] {
        return this.filterForm.get('occurrence')?.value;
    }

    set occurrence(value: number[]) {
        this.filterForm.get('occurrence')?.setValue(value);
    }

    generateTaskArray(): number[] {
        return Array.from({ length: this.totalAmountOfTasks() }, (_, i) => i + 1);
    }
}
