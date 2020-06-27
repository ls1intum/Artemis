import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

/**
 * Filter properties for a result
 */
enum FilterProp {
    ALL = 'all',
    SUCCESSFUL = 'successful',
    UNSUCCESSFUL = 'unsuccessful',
}

@Component({
    selector: 'jhi-exam-scores',
    templateUrl: './exam-scores.component.html',
    styles: [],
})
export class ExamScoresComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly FilterProp = FilterProp;

    isLoading: boolean;
    paramSub: Subscription;
    exam: Exam;
    results: Result[];
    filteredResultsSize: number;

    resultCriteria: {
        filterProp: FilterProp;
    };

    constructor(private route: ActivatedRoute, private examService: ExamManagementService) {
        this.resultCriteria = {
            filterProp: FilterProp.ALL,
        };
        this.results = [];
        this.filteredResultsSize = 0;
    }

    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.examService.find(params['courseId'], params['examId']).subscribe((examResponse) => {
                this.exam = examResponse.body!;
            });
        });
    }

    exportNames() {}

    exportResults() {}

    /**
     * Updates the criteria by which to filter results
     * @param newValue New filter prop value
     */
    updateResultFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.resultCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    /**
     * Predicate used to filter results by the current filter prop setting
     * @param result Result for which to evaluate the predicate
     */
    filterResultByProp = (result: Result) => {
        switch (this.resultCriteria.filterProp) {
            case FilterProp.SUCCESSFUL:
                return result.successful;
            case FilterProp.UNSUCCESSFUL:
                return !result.successful;
            default:
                return true;
        }
    };

    /**
     * Update the number of filtered results
     * @param filteredResultsSize Total number of results after filters have been applied
     */
    handleResultsSizeChange = (filteredResultsSize: number) => {
        this.filteredResultsSize = filteredResultsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     * @param result
     */
    searchResultFormatter = (result: Result) => {
        const participation = result.participation as StudentParticipation;
        if (participation.student) {
            const { login, name } = participation.student;
            return `${login} (${name})`;
        }
    };

    /**
     * Converts a result object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     * @param result
     */
    searchTextFromResult = (result: Result): string => {
        return (result.participation as StudentParticipation).participantIdentifier || '';
    };

    /**
     * Triggers a re-fetch of the results from the server
     */
    refresh() {
        this.isLoading = true;
        this.results = [];
        // this.getResults().subscribe(() => (this.isLoading = false));
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
    }
}
