import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ModelingPlagiarismResult } from 'app/exercises/shared/plagiarism/types/modeling/ModelingPlagiarismResult';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { faChevronRight, faExclamationTriangle, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ReplaySubject } from 'rxjs';

@Component({
    selector: 'jhi-messages',
    styleUrls: ['./course-messages.component.scss'],
    templateUrl: './course-messages.component.html',
})
export class CourseMessagesComponent implements OnInit {
    /**
     * True, if an automated plagiarism detection is running; false otherwise.
     */
    detectionInProgress = false;

    detectionInProgressMessage = '';

    /**
     * Index of the currently selected comparison.
     */
    selectedChatSessionId: ReplaySubject<number>;
    visibleComparisons?: PlagiarismComparison<any>[];

    readonly FeatureToggle = FeatureToggle;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faExclamationTriangle = faExclamationTriangle;
    faChevronRight = faChevronRight;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {}

    selectChatSessionWithID(id: number) {
        this.selectedChatSessionId.next(id);
    }

    handlePlagiarismResult(result: ModelingPlagiarismResult | TextPlagiarismResult) {
        this.detectionInProgress = false;

        if (result?.comparisons) {
            this.sortComparisonsForResult(result);
        }

        // this.plagiarismResult = result;
        // this.selectedComparisonId = this.plagiarismResult.comparisons[0].id;
        this.visibleComparisons = result.comparisons;
    }

    sortComparisonsForResult(result: PlagiarismResult<any>) {
        result.comparisons = result.comparisons.sort((a, b) => {
            // if the cases share the same similarity, we sort by the id
            if (b.similarity - a.similarity === 0) {
                return b.id - a.id;
            } else {
                return b.similarity - a.similarity;
            }
        });
    }

    /**
     * Resets the filter applied by chart interaction
     */
    resetFilter(): void {}
}
