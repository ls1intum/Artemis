import { Component, Input, OnChanges } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';

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

    @Input() entries: (Result | SelfLearningFeedbackRequest)[];
    @Input() exercise: Exercise;

    showPreviousDivider = false;
    displayedEntries: (Result | SelfLearningFeedbackRequest)[];
    movedLastRatedResult: boolean;

    ngOnChanges(): void {
        this.showPreviousDivider = this.entries.length > MAX_RESULT_HISTORY_LENGTH;

        if (this.entries.length <= MAX_RESULT_HISTORY_LENGTH) {
            this.displayedEntries = this.entries;
        } else {
            this.displayedEntries = this.entries.slice(this.entries.length - MAX_RESULT_HISTORY_LENGTH);

            const lastRatedResult = this.entries.filter((entry) => Result.isResult(entry) && entry.rated).last() as Result | undefined;
            const firstEntry = this.displayedEntries.first();
            if (Result.isResult(firstEntry)) {
                if (!firstEntry.rated && lastRatedResult) {
                    this.displayedEntries[0] = lastRatedResult;
                    this.movedLastRatedResult = true;
                }
            }
        }
    }

    protected readonly Result = Result;
    protected readonly SelfLearningFeedbackRequest = SelfLearningFeedbackRequest;
}
