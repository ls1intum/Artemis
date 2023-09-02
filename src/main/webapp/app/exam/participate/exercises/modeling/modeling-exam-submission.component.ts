import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, ViewChild } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import dayjs from 'dayjs/esm';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-modeling-submission-exam',
    templateUrl: './modeling-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ModelingExamSubmissionComponent }],
    styleUrls: ['./modeling-exam-submission.component.scss'],
    // change deactivation must be triggered manually
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModelingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: ModelingSubmission;

    @Input()
    exercise: ModelingExercise;
    umlModel: UMLModel; // input model for Apollon+

    explanationText: string; // current explanation text

    readonly IncludedInOverallScore = IncludedInOverallScore;

    // Icons
    farListAlt = faListAlt;

    constructor(changeDetectorReference: ChangeDetectorRef) {
        super(changeDetectorReference);
    }

    ngOnInit(): void {
        // show submission answers in UI
        this.updateViewFromSubmission();
    }

    /**
     * Updates the problem statement of the currently loaded modeling exercise which is part of the user's student exam.
     * @param newProblemStatement is the updated problem statement that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatement: string): void {
        this.exercise.problemStatement = newProblemStatement;
        this.changeDetectorReference.detectChanges();
    }

    getSubmission(): Submission {
        return this.studentSubmission;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    updateViewFromSubmission(): void {
        if (this.studentSubmission) {
            if (this.studentSubmission.model) {
                // Updates the Apollon editor model state (view) with the latest modeling submission
                this.umlModel = JSON.parse(this.studentSubmission.model);
            }
            // Updates explanation text with the latest submission
            this.explanationText = this.studentSubmission.explanationText ?? '';
        }
    }

    /**
     * Updates the model of the submission with the current Apollon editor model state (view)
     * Updates the explanation text of the submission with the current explanation
     */
    public updateSubmissionFromView(): void {
        if (!this.modelingEditor || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        const currentApollonModel = this.modelingEditor.getCurrentModel();
        const diagramJson = JSON.stringify(currentApollonModel);

        if (this.studentSubmission) {
            if (diagramJson) {
                this.studentSubmission.model = diagramJson;
            }
            this.studentSubmission.explanationText = this.explanationText;
        }
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes (i.e. the submission is NOT synced), false otherwise.
     */
    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission.isSynced!;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.exercise && (!this.exercise.dueDate || dayjs(this.exercise.dueDate).isSameOrAfter(dayjs()));
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    modelChanged(model: UMLModel) {
        this.studentSubmission.isSynced = false;
    }

    // Changes isSynced to false and updates explanation text
    explanationChanged(explanation: string) {
        this.studentSubmission.isSynced = false;
        this.explanationText = explanation;
    }
}
