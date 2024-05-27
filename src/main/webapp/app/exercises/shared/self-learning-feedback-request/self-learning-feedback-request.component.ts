import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { faQuestionCircle, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';
import dayjs from 'dayjs/esm';
import { getSelfLearningFeedbackTextColorClass, getSelfLearningIconClass } from 'app/exercises/shared/self-learning-feedback-request/self-learning-feedback-request.utils';

@Component({
    selector: 'jhi-self-learning-feedback',
    templateUrl: './self-learning-feedback-request.component.html',
    styleUrls: ['./self-learning-feedback-request.component.scss'],
})
/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class SelfLearningFeedbackRequestComponent implements OnInit, OnDestroy {
    @Input() selfLearningFeedbackRequest: SelfLearningFeedbackRequest;
    @Input() showIcon = true;

    timedOut: boolean = false;

    private resultUpdateSubscription?: ReturnType<typeof setTimeout>;

    // Icons
    readonly faTimesCircle = faTimesCircle;
    readonly faQuestionCircle = faQuestionCircle;

    protected readonly getSelfLearningFeedbackTextColorClass = getSelfLearningFeedbackTextColorClass;
    protected readonly getSelfLearningIconClass = getSelfLearningIconClass;

    constructor() {}

    ngOnInit() {
        if (!this.selfLearningFeedbackRequest.responseDateTime && this.selfLearningFeedbackRequest.successful === undefined) {
            const dueTime = dayjs(this.selfLearningFeedbackRequest.requestDateTime).add(5, 'minutes').diff(dayjs(), 'milliseconds');

            this.resultUpdateSubscription = setTimeout(() => {
                this.timedOut = true;

                if (this.resultUpdateSubscription) {
                    clearTimeout(this.resultUpdateSubscription);
                }
            }, dueTime);
        }
    }

    ngOnDestroy() {
        if (this.resultUpdateSubscription) {
            clearTimeout(this.resultUpdateSubscription);
        }
    }
}
