import { Component, EventEmitter, HostBinding, Input, Output } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Exercise } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { AssessmentAfterComplaint } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentNote } from 'app/entities/assessment-note.model';

/**
 * The <jhi-assessment-layout> component provides the basic layout for an assessment page.
 * It shows the header, alerts for complaints on top and the complaint form at the bottom of the page.
 * The actual assessment needs to be inserted using content projection.
 * Components using this component need to provide Inputs and handle Outputs. This component does not perform assessment logic.
 */
@Component({
    selector: 'jhi-assessment-layout',
    templateUrl: './assessment-layout.component.html',
    styleUrls: ['./assessment-layout.component.scss'],
})
export class AssessmentLayoutComponent {
    @HostBinding('class.assessment-container') readonly assessmentContainerClass = true;

    @Output() navigateBack = new EventEmitter<void>();
    MORE_FEEDBACK = ComplaintType.MORE_FEEDBACK;
    @Input() isLoading: boolean;
    @Input() saveBusy: boolean;
    @Input() submitBusy: boolean;
    @Input() cancelBusy: boolean;
    @Input() nextSubmissionBusy: boolean;
    @Input() correctionRound: number;

    @Input() isTeamMode: boolean;
    @Input() isAssessor: boolean;
    @Input() canOverride: boolean;
    @Input() isTestRun = false;
    @Input() isIllegalSubmission: boolean;
    @Input() exerciseDashboardLink: string[];

    @Input() result?: Result;
    @Input() assessmentsAreValid: boolean;
    @Input() complaint?: Complaint;
    @Input() exercise?: Exercise;
    @Input() submission?: Submission;
    @Input() hasAssessmentDueDatePassed: boolean;
    @Input() isProgrammingExercise: boolean; // remove once diff view activated for programming exercises

    private _highlightDifferences: boolean;

    @Input() set highlightDifferences(highlightDifferences: boolean) {
        this._highlightDifferences = highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    get highlightDifferences() {
        return this._highlightDifferences;
    }

    setAssessmentNoteForResult(assessmentNote: AssessmentNote) {
        if (this.result) {
            this.result.assessmentNote = assessmentNote;
        }
    }

    @Output() save = new EventEmitter<void>();
    // eslint-disable-next-line @angular-eslint/no-output-native
    @Output() submit = new EventEmitter<void>();
    // eslint-disable-next-line @angular-eslint/no-output-native
    @Output() cancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() updateAssessmentAfterComplaint = new EventEmitter<AssessmentAfterComplaint>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();
    @Output() useAsExampleSubmission = new EventEmitter<void>();
}
