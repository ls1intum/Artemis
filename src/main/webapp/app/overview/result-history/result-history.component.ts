import { Component, Input, OnChanges } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';
import { getSelfLearningFeedbackTextColorClass, getSelfLearningIconClass } from 'app/exercises/shared/self-learning-feedback-request/self-learning-feedback-request.utils';

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

        // finished self-learning feedback requests are filtered out
        // thus they are not counted
        const filteredEntities = this.entries.filter(
            (entry) => Result.isResult(entry) || (SelfLearningFeedbackRequest.isSelfLearningFeedbackRequest(entry) && !SelfLearningFeedbackRequest.isCompletedAndSuccessful(entry)),
        );

        if (filteredEntities.length <= MAX_RESULT_HISTORY_LENGTH) {
            this.displayedEntries = filteredEntities;
        } else {
            this.displayedEntries = filteredEntities.slice(filteredEntities.length - MAX_RESULT_HISTORY_LENGTH);

            const lastRatedShownResult = this.displayedEntries.filter((entry) => Result.isResult(entry) && entry.rated).last() as Result | undefined;
            const lastRatedResult = this.entries.filter((entry) => Result.isResult(entry) && entry.rated).last() as Result | undefined;
            if (!lastRatedShownResult && lastRatedResult) {
                this.displayedEntries[0] = lastRatedResult;
                this.movedLastRatedResult = true;
            }
        }
    }

    protected readonly Result = Result;
    protected readonly SelfLearningFeedbackRequest = SelfLearningFeedbackRequest;
    protected readonly getSelfLearningIconClass = getSelfLearningIconClass;
    protected readonly getSelfLearningFeedbackTextColorClass = getSelfLearningFeedbackTextColorClass;
}
