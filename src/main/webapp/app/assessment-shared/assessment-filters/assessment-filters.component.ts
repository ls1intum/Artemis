import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Submission } from 'app/entities/submission';

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
        return submission && submission.result && !submission.result.completionDate;
    }
}
