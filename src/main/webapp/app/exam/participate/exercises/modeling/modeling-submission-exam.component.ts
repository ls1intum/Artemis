import { Component, OnInit, ViewChild, Input } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import * as moment from 'moment';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { stringifyIgnoringFields } from 'app/shared/util/utils';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-editor-exam.component';

@Component({
    selector: 'jhi-modeling-submission-exam',
    templateUrl: './modeling-submission-exam.component.html',
    styleUrls: ['./modeling-submission-exam.component.scss'],
})
export class ModelingSubmissionExamComponent extends ExamSubmissionComponent implements OnInit {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;

    @Input()
    studentParticipation: StudentParticipation;

    @Input()
    modelingExercise: ModelingExercise;
    umlModel: UMLModel; // input model for Apollon

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    submission: ModelingSubmission;

    ngOnInit(): void {
        if (this.studentParticipation.submissions && this.studentParticipation.submissions.length === 1) {
            this.submission = this.studentParticipation.submissions[0] as ModelingSubmission;

            // show submission answers in UI
            this.updateViewFromSubmission();
        }
        window.scroll(0, 0);
    }

    updateViewFromSubmission(): void {
        if (this.submission.model) {
            // Updates the Apollon editor model state (view) with the latest modeling submission
            this.umlModel = JSON.parse(this.submission.model);
        }
    }

    /**
     * Updates the model of the submission with the current Apollon editor model state (view)
     */
    public updateSubmissionFromView(): void {
        if (!this.modelingEditor || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        const currentApollonModel = this.modelingEditor.getCurrentModel();
        const diagramJson = JSON.stringify(currentApollonModel);
        if (this.submission && diagramJson) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes, false otherwise.
     */
    public hasUnsavedChanges(): boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return false;
        }
        const currentApollonModel = this.modelingEditor.getCurrentModel();

        if (!this.submission || !this.submission.model) {
            return currentApollonModel.elements.length > 0 && JSON.stringify(currentApollonModel) !== '';
        } else if (this.submission && this.submission.model) {
            const currentSubmissionModel = JSON.parse(this.submission.model);
            const versionMatch = currentSubmissionModel.version === currentApollonModel.version;
            const modelMatch = stringifyIgnoringFields(currentSubmissionModel, 'size') === stringifyIgnoringFields(currentApollonModel, 'size');
            return versionMatch && !modelMatch;
        }
        return false;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.modelingExercise && (!this.modelingExercise.dueDate || moment(this.modelingExercise.dueDate).isSameOrAfter(moment()));
    }
}
