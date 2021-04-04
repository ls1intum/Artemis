import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Result } from 'app/entities/result.model';

/**
 * The <jhi-assessment-header> component is used in the shared assessment layout.
 * It displays a header bar above the assessment editor with information of locking, as well as offering save/submit/etc buttons.
 * This guarantees a unified look and feel for both interfaces.
 * Depending Components need to perform actions based on the save/submit/cancel/nextSubmission/navigateBack outputs.
 */
@Component({
    selector: 'jhi-assessment-header',
    templateUrl: './assessment-header.component.html',
    styleUrls: ['./assessment-header.component.scss'],
})
export class AssessmentHeaderComponent {
    @Input() isLoading: boolean;
    @Input() saveBusy: boolean;
    @Input() submitBusy: boolean;
    @Input() cancelBusy: boolean;
    @Input() nextSubmissionBusy: boolean;

    @Input() isTeamMode: boolean;
    @Input() isAssessor: boolean;
    @Input() isAtLeastInstructor: boolean;
    @Input() isTestRun = false;
    @Input() canOverride: boolean;

    @Input() result: Result | undefined;
    @Input() hasComplaint = false;
    @Input() hasMoreFeedbackRequest = false;
    @Input() complaintHandled = false;
    @Input() assessmentsAreValid: boolean;
    @Input() hasAssessmentDueDatePassed: boolean;

    @Output() save = new EventEmitter<void>();
    @Output() submit = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
}
