import { Component, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { getLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

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

    @Input()
    submissions: Submission[] = [];
    filteredSubmissions: number;

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

    ngOnChanges() {
        this.filteredSubmissions = this.submissions.filter((submission) => {
            const result = getLatestSubmissionResult(submission);
            return result && !result.completionDate;
        }).length;
    }
}
