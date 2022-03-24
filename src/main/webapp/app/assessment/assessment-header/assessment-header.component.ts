import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { TextAssessmentEventType } from 'app/entities/text-assesment-event.model';
import { ActivatedRoute } from '@angular/router';
import { ComplaintType } from 'app/entities/complaint.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TranslateService } from '@ngx-translate/core';
import { faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { faSquareCaretRight } from '@fortawesome/free-regular-svg-icons';

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
    @Input() correctionRound = 0; // correctionRound defaults to 0

    @Input() isTeamMode: boolean;
    @Input() isAssessor: boolean;
    @Input() isTestRun = false;
    @Input() exerciseDashboardLink: string[];
    @Input() canOverride: boolean;

    @Input() exercise?: Exercise;
    @Input() result?: Result;
    @Input() isIllegalSubmission: boolean;
    @Input() hasComplaint = false;
    @Input() hasMoreFeedbackRequest = false;
    @Input() complaintHandled = false;
    @Input() complaintType?: ComplaintType;
    @Input() assessmentsAreValid: boolean;
    @Input() hasAssessmentDueDatePassed: boolean;
    @Input() isProgrammingExercise = false; // remove once diff view activated for programming exercises

    @Output() save = new EventEmitter<void>();
    @Output() submit = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();
    @Output() useAsExampleSubmission = new EventEmitter<void>();

    private _highlightDifferences: boolean;
    readonly ExerciseType = ExerciseType;
    readonly ComplaintType = ComplaintType;
    readonly AssessmentType = AssessmentType;

    // Icons
    faSpinner = faSpinner;
    faSave = faSave;
    faArrowRight = faSquareCaretRight;

    @Input() set highlightDifferences(highlightDifferences: boolean) {
        this._highlightDifferences = highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    constructor(public textAssessmentAnalytics: TextAssessmentAnalytics, protected route: ActivatedRoute, private translateService: TranslateService) {
        textAssessmentAnalytics.setComponentRoute(route);
    }

    get highlightDifferences() {
        return this._highlightDifferences;
    }

    @HostListener('document:keydown.control.s', ['$event'])
    saveOnControlAndS(event: KeyboardEvent) {
        event.preventDefault();
        const saveButton = document.getElementById('save') as HTMLButtonElement;
        if (saveButton && !saveButton.disabled) {
            saveButton.click();
        }
    }

    @HostListener('document:keydown.control.enter', ['$event'])
    submitOnControlAndEnter(event: KeyboardEvent) {
        event.preventDefault();
        const submitButton = document.getElementById('submit') as HTMLButtonElement;
        const overrideButton = document.getElementById('override') as HTMLButtonElement;
        if (submitButton && !submitButton.disabled) {
            submitButton.click();
        } else if (overrideButton && !overrideButton.disabled) {
            overrideButton.click();
        }
    }

    @HostListener('document:keydown.control.shift.arrowRight', ['$event'])
    assessNextOnControlShiftAndArrowRight(event: KeyboardEvent) {
        event.preventDefault();
        const assessNextButton = document.getElementById('assessNextButton') as HTMLButtonElement;
        if (assessNextButton && !assessNextButton.disabled) {
            assessNextButton.click();
        }
    }

    /**
     * In ExamMode:
     * Highlight the difference between the first and second correction round
     */
    public toggleHighlightDifferences() {
        this.highlightDifferences = !this.highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    /**
     * Sends and assessment event for the submit button using the analytics service in case the exercise type is TEXT
     */
    sendSubmitAssessmentEventToAnalytics() {
        if (this.exercise?.type === ExerciseType.TEXT) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.SUBMIT_ASSESSMENT);
        }
    }

    /**
     * Sends and assessment event for the assess next button using the analytics service in case the exercise type is TEXT
     */
    sendAssessNextEventToAnalytics() {
        if (this.exercise?.type === ExerciseType.TEXT) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION);
        }
    }

    /**
     * Opens dialog to verify that instructor wants to use current submission as example submission
     */
    onUseAsExampleSolutionClicked() {
        const verificationMessage = this.translateService.instant('artemisApp.assessment.useAsExampleSubmissionVerificationQuestion');
        if (confirm(verificationMessage)) {
            this.useAsExampleSubmission.emit();
        }
    }
}
