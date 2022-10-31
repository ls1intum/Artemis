import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Submission, getLatestSubmissionResult } from 'app/entities/submission.model';

/**
 * filters for all or only locked submissions
 */
enum FilterProp {
    ALL = 'all',
    LOCKED = 'locked',
}

@Component({
    selector: 'jhi-assessment-filters',
    templateUrl: './assessment-filters.component.html',
})
export class AssessmentFiltersComponent implements OnChanges {
    FilterProp = FilterProp;

    filterProp: FilterProp = FilterProp.ALL;

    @Input() submissions: Submission[] = [];
    @Input() amountFilteredSubmissions: number;

    @Input() resetFilter = false;

    @Output() filterChange = new EventEmitter<Submission[]>();

    ngOnChanges(changes: SimpleChanges): void {
        if (!changes.amountFilteredSubmissions) {
            this.amountFilteredSubmissions = this.submissions.filter((submission) => {
                return AssessmentFiltersComponent.isSubmissionLocked(submission);
            }).length;
        }
        if (this.resetFilter) {
            this.filterProp = FilterProp.ALL;
        }
    }

    /**
     * Updates filter to either filtering for locked or all assessments
     * @param {FilterProp} filterProp - filter type
     */
    public updateFilter(filterProp: FilterProp) {
        this.filterProp = filterProp;
        this.updateFilteredSubmissions();
    }

    private updateFilteredSubmissions() {
        this.filterChange.emit(this.submissions.filter(this.filterSubmissionByProp));
    }

    private filterSubmissionByProp = (submission: Submission) => {
        switch (this.filterProp) {
            case FilterProp.LOCKED:
                return AssessmentFiltersComponent.isSubmissionLocked(submission);
            case FilterProp.ALL:
            default:
                return true;
        }
    };

    private static isSubmissionLocked(submission: Submission) {
        return submission && getLatestSubmissionResult(submission) && !getLatestSubmissionResult(submission)!.completionDate;
    }
}
