import { Component, Input, OnChanges } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { AssessmentType } from 'app/entities/assessment-type.model';

export const MAX_RESULT_HISTORY_LENGTH = 5;

// Modal -> Result details view
@Component({
    selector: 'jhi-result-history',
    templateUrl: './result-history.component.html',
    styleUrls: ['./result-history.scss'],
})
export class ResultHistoryComponent implements OnChanges {
    readonly getTextColorClass = getTextColorClass;
    readonly getResultIconClass = getResultIconClass;
    readonly evaluateTemplateStatus = evaluateTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;

    @Input() results: Result[];
    @Input() exercise: Exercise;

    showPreviousDivider = false;
    displayedResults: Result[];
    movedLastRatedResult: boolean;

    ngOnChanges(): void {
        this.showPreviousDivider = this.results.length > MAX_RESULT_HISTORY_LENGTH;
        this.displayedResults = this.results.filter((result) => !(result.assessmentType === AssessmentType.AUTOMATIC_ATHENA && !result.successful));
        const successfulResultsLength = this.displayedResults.length;
        if (successfulResultsLength > MAX_RESULT_HISTORY_LENGTH) {
            this.displayedResults = this.displayedResults.slice(successfulResultsLength - MAX_RESULT_HISTORY_LENGTH);
            const lastRatedResult = this.results.filter((result) => result.rated).last();
            if (!this.displayedResults.first()?.rated && lastRatedResult) {
                this.displayedResults[0] = lastRatedResult;
                this.movedLastRatedResult = true;
            }
        }
    }
}
