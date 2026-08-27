import { Component, input, model, output } from '@angular/core';
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
    host: { class: 'assessment-container' },
})
export class AssessmentLayoutComponent {
    MORE_FEEDBACK = ComplaintType.MORE_FEEDBACK;
    readonly isLoading = input.required<boolean>();
    readonly saveBusy = input<boolean>(false);
    readonly submitBusy = input<boolean>(false);
    readonly cancelBusy = input<boolean>(false);
    readonly nextSubmissionBusy = input<boolean>(false);
    readonly correctionRound = input<number>(0);

    readonly isTeamMode = input.required<boolean>();
    readonly isAssessor = input.required<boolean>();
    readonly canOverride = input.required<boolean>();
    readonly isTestRun = input(false);
    readonly exerciseDashboardLink = input.required<string[]>();

    readonly result = input<Result>();
    readonly assessmentsAreValid = input<boolean>(false);
    readonly complaint = input<Complaint>();
    readonly exercise = input<Exercise>();
    readonly submission = input<Submission>();
    /** Whether the layout renders the assessment note. A host that renders the note itself passes false. */
    readonly showAssessmentNote = input(true);

    /**
     * Whether the layout renders the complaint banner and the complaint form beneath the assessment, both of which are
     * reached by scrolling the page. A host whose page does not scroll renders the form itself and passes false.
     */
    readonly showComplaintSection = input(true);
    readonly hasAssessmentDueDatePassed = input.required<boolean>();
    readonly isProgrammingExercise = input<boolean>(false); // remove once diff view activated for programming exercises
    readonly highlightDifferences = model(false);

    readonly navigateBack = output();
    readonly save = output();
    readonly onSubmit = output();
    readonly onCancel = output();
    readonly nextSubmission = output();
    readonly updateAssessmentAfterComplaint = output<AssessmentAfterComplaint>();
    readonly useAsExampleSubmission = output();

    setAssessmentNoteForResult(assessmentNote: AssessmentNote) {
        const result = this.result();
        if (result) {
            result.assessmentNote = assessmentNote;
        }
    }
}
