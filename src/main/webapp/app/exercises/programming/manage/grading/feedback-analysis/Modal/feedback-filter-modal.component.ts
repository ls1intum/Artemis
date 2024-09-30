import { Component, EventEmitter, Input, Output } from '@angular/core';
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
    @Input() localStorageService!: LocalStorageService;
    @Output() filterApplied = new EventEmitter<any>();

    filterForm: FormGroup;
    tasks: string[] = [];
    testCases: string[] = [];
    occurrenceRange = [0, 100]; // Default occurrence range if needed

    private FILTER_TASKS_KEY = 'feedbackAnalysis.tasks';
    private FILTER_TEST_CASES_KEY = 'feedbackAnalysis.testCases';
    private FILTER_OCCURRENCE_KEY = 'feedbackAnalysis.occurrence';

    constructor(
        private activeModal: NgbActiveModal,
        private fb: FormBuilder,
    ) {
        // Initialize form without any default values, will be set from the parent component
        this.filterForm = this.fb.group({
            tasks: [],
            testCases: [],
            occurrence: [],
        });
    }

    applyFilter(): void {
        const filters = this.filterForm.value;

        filters.occurrence = this.occurrenceRange; // Add occurrence range to filters

        // Store applied filters into LocalStorage
        this.localStorageService.store(this.FILTER_TASKS_KEY, filters.tasks);
        this.localStorageService.store(this.FILTER_TEST_CASES_KEY, filters.testCases);
        this.localStorageService.store(this.FILTER_OCCURRENCE_KEY, filters.occurrence);

        this.filterApplied.emit(filters); // Emit the filters to parent
        this.activeModal.close();
    }

    clearFilter(): void {
        // Clear local storage entries
        this.localStorageService.clear(this.FILTER_TASKS_KEY);
        this.localStorageService.clear(this.FILTER_TEST_CASES_KEY);
        this.localStorageService.clear(this.FILTER_OCCURRENCE_KEY);

        // Reset form values
        this.filterForm.reset({
            tasks: [],
            testCases: [],
            occurrence: [0, 100], // Reset to default occurrence range
        });

        this.filterApplied.emit(this.filterForm.value); // Emit the cleared filter state
        this.activeModal.close();
    }

    closeModal(): void {
        this.activeModal.dismiss();
    }
}
