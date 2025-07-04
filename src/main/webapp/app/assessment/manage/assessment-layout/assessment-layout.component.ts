import { Component, EventEmitter, HostBinding, Input, Output } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { AssessmentHeaderComponent } from '../assessment-header/assessment-header.component';
import { AssessmentComplaintAlertComponent } from '../assessment-complaint-alert/assessment-complaint-alert.component';
import { AssessmentNoteComponent } from '../assessment-note/assessment-note.component';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';

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
    imports: [AssessmentHeaderComponent, AssessmentComplaintAlertComponent, AssessmentNoteComponent, ComplaintsForTutorComponent],
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
    @Output() onSubmit = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() updateAssessmentAfterComplaint = new EventEmitter<AssessmentAfterComplaint>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();
    @Output() useAsExampleSubmission = new EventEmitter<void>();
}
