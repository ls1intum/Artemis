import { Component, OnDestroy, OnInit, ViewChild, Input } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import { AlertService } from 'app/core/alert/alert.service';
import { Observable } from 'rxjs/Observable';
import * as moment from 'moment';
import { omit } from 'lodash';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { stringifyIgnoringFields } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-modeling-submission-exam',
    templateUrl: './modeling-submission-exam.component.html',
    styleUrls: ['app/exercise/modeling/participate/modeling-submission.component.scss'],
})
export class ModelingSubmissionExamComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;

    participation: StudentParticipation;
    modelingExercise: ModelingExercise;

    submission: ModelingSubmission;

    umlModel: UMLModel; // input model for Apollon
    hasElements = false; // indicates if the current model has at least one element
    isSaving: boolean;
    autoSaveInterval: number;
    autoSaveTimer: number;

    @Input()
    private participationId: number;

    constructor(private modelingSubmissionService: ModelingSubmissionService, private jhiAlertService: AlertService) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
    }

    ngOnInit(): void {
        // TODO: replace with exam-participation-service
        this.modelingSubmissionService.getLatestSubmissionForModelingEditor(this.participationId).subscribe(
            (modelingSubmission) => {
                this.updateModelingSubmission(modelingSubmission);
                this.setAutoSaveTimer();
            },
            (error) => {
                this.jhiAlertService.error(error.message, null, undefined);
            },
        );

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
     */
    private setAutoSaveTimer(): void {
        this.autoSaveTimer = 0;
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= 60 && !this.canDeactivate()) {
                this.saveDiagram();
            }
        }, 1000);
    }

    saveDiagram(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionModel();
        this.isSaving = true;
        this.autoSaveTimer = 0;

        // TODO: relplace with exam-participation-service
        this.modelingSubmissionService.create(this.submission, this.modelingExercise.id).subscribe(
            (submission) => {
                this.submission = submission.body!;
                this.jhiAlertService.success('artemisApp.modelingEditor.saveSuccessful');
                this.onSaveSuccess();
            },
            () => this.onSaveError(),
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
    }

    private onSaveError() {
        this.jhiAlertService.error('artemisApp.modelingEditor.error');
        this.isSaving = false;
    }

    ngOnDestroy(): void {
        clearInterval(this.autoSaveInterval);
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

    canDeactivate(): Observable<boolean> | boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return true;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();
        return !this.modelHasUnsavedChanges(model);
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes, false otherwise.
     */
    private modelHasUnsavedChanges(model: UMLModel): boolean {
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
