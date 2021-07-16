import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';

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
    @Input() isAtLeastInstructor: boolean;
    @Input() isTestRun = false;
    @Input() exerciseDashboardLink: string[];
    @Input() canOverride: boolean;

    @Input() exercise?: Exercise;
    @Input() result?: Result;
    @Input() isIllegalSubmission: boolean;
    @Input() hasComplaint = false;
    @Input() hasMoreFeedbackRequest = false;
    @Input() complaintHandled = false;
    @Input() assessmentsAreValid: boolean;
    @Input() hasAssessmentDueDatePassed: boolean;
    @Input() isProgrammingExercise = false; // remove once diff view activated for programming exercises

    @Output() save = new EventEmitter<void>();
    @Output() submit = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();

    private _highlightDifferences: boolean;

    @Input() set highlightDifferences(highlightDifferences: boolean) {
        this._highlightDifferences = highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    get highlightDifferences() {
        return this._highlightDifferences;
    }

    /**
     * In ExamMode:
     * Highlight the difference between the first and second correction round
     */
    public toggleHighlightDifferences() {
        this.highlightDifferences = !this.highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }
}
