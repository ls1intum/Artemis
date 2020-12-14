import { Component, EventEmitter, Input, Output } from '@angular/core';
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';

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
export class AssessmentFiltersComponent {
    FilterProp = FilterProp;

    filterProp: FilterProp = FilterProp.ALL;

    @Input() submissions: Submission[] = [];

    @Output() filterChange = new EventEmitter<Submission[]>();

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
