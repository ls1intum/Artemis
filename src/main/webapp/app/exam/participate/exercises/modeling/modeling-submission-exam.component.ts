import { Component, OnDestroy, OnInit, ViewChild, Input } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import { AlertService } from 'app/core/alert/alert.service';
import { Observable } from 'rxjs/Observable';
import * as moment from 'moment';
import { omit } from 'lodash';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { stringifyIgnoringFields } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-modeling-submission-exam',
    templateUrl: './modeling-submission-exam.component.html',
    styleUrls: ['./modeling-submission-exam.component.scss'],
})
export class ModelingSubmissionExamComponent implements OnInit, OnDestroy {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;

    @Input()
    participationId: number;

    modelingExercise: ModelingExercise;
    umlModel: UMLModel; // input model for Apollon

    participation: StudentParticipation;
    submission: ModelingSubmission;

    hasElements = false; // indicates if the current model has at least one element
    isSaving: boolean;
    autoSaveInterval: number;

    constructor(private jhiAlertService: AlertService) {
        this.isSaving = false;
    }

    ngOnInit(): void {
        this.setAutoSaveTimer();
        window.scroll(0, 0);
    }

    private updateModelingSubmission(modelingSubmission: ModelingSubmission) {
        if (!modelingSubmission) {
            this.jhiAlertService.error('artemisApp.apollonDiagram.submission.noSubmission');
        }

        this.submission = modelingSubmission;
        this.participation = modelingSubmission.participation as StudentParticipation;

        // reconnect participation <--> submission
        this.participation.submissions = [<ModelingSubmission>omit(modelingSubmission, 'participation')];

        this.modelingExercise = this.participation.exercise as ModelingExercise;
        this.modelingExercise.studentParticipations = [this.participation];
        this.modelingExercise.participationStatus = participationStatus(this.modelingExercise);
        if (this.modelingExercise.diagramType == null) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
        }
    }

    /**
     * This function sets and starts an auto-save timer that automatically saves changes
     * to the model after at most 60 seconds.
     *
     * TODO: I don't really like the logic here, with a second 60s timer.
     * I think the parent component should handle this and whenever a save is needed (we have 3 cases), then it retrieves the latest UML model from Apollon as submission
     */
    private setAutoSaveTimer(): void {
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(
            () => {
                if (this.modelHasUnsavedChanges()) {
                    this.saveDiagram();
                }
            }, // 60seconds
            1000 * 60,
        );
    }

    saveDiagram(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionModel();
        this.isSaving = true;
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
        // TODO: sync the latest changes from Apollon to exam-participation.service
    }

    /**
     * Updates the model of the submission with the current Apollon model state
     */
    updateSubmissionModel(): void {
        if (!this.submission) {
            this.submission = new ModelingSubmission();
        }
        if (!this.modelingEditor || !this.modelingEditor.getCurrentModel()) {
            return;
        }
        const umlModel = this.modelingEditor.getCurrentModel();
        this.hasElements = umlModel.elements && umlModel.elements.length !== 0;
        const diagramJson = JSON.stringify(umlModel);
        if (this.submission && diagramJson) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes, false otherwise.
     */
    private modelHasUnsavedChanges(): boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return false;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();

        if (!this.submission || !this.submission.model) {
            return model.elements.length > 0 && JSON.stringify(model) !== '';
        } else if (this.submission && this.submission.model) {
            const currentModel = JSON.parse(this.submission.model);
            const versionMatch = currentModel.version === model.version;
            const modelMatch = stringifyIgnoringFields(currentModel, 'size') === stringifyIgnoringFields(model, 'size');
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
