import { Component, Input } from '@angular/core';
import { Participation } from 'app/entities/participation/participation.model';
import { faCheckCircle, faCircleNotch, faExclamationCircle, faExclamationTriangle, faFile, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';
import { MissingResultInformation, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';

@Component({
    selector: 'jhi-self-learning-feedback',
    templateUrl: './self-learning-feedback-request.component.html',
    styleUrls: ['./self-learning-feedback-request.component.scss'],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class SelfLearningFeedbackRequestComponent {
    @Input() participation: Participation;
    @Input() selfLearningFeedbackRequest: SelfLearningFeedbackRequest;

    timedOut: boolean = false;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly faFile = faFile;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faTimesCircle = faTimesCircle;
    readonly faCheckCircle = faCheckCircle;

    constructor() {}

    protected readonly getResultIconClass = getResultIconClass;
    protected readonly getTextColorClass = getTextColorClass;
    protected readonly MissingResultInfo = MissingResultInformation;
    protected readonly evaluateTemplateStatus = evaluateTemplateStatus;
}
