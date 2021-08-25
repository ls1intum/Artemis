import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Selection, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ComplaintType } from 'app/entities/complaint.model';
import { Feedback } from 'app/entities/feedback.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { participationStatus } from 'app/exercises/shared/exercise/exercise-utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ButtonType } from 'app/shared/components/button.component';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { stringifyIgnoringFields } from 'app/shared/util/utils';
import { omit } from 'lodash';
import * as moment from 'moment';
import { JhiAlertService } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { addParticipationToResult, getUnreferencedFeedback } from 'app/exercises/shared/result/result-utils';
import { TransformationModelingExercise } from 'app/entities/transformation-modeling-exercise.model';
import { TransformationModelingSubmission } from 'app/entities/transformation-modeling-submission.model';

@Component({
    selector: 'jhi-transformation-modeling-submission',
    templateUrl: './transformation-modeling-submission.component.html',
    styleUrls: ['./transformation-modeling-submission.component.scss'],
})
export class TransformationModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly addParticipationToResult = addParticipationToResult;
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;
    ButtonType = ButtonType;

    private subscription: Subscription;

    participation: StudentParticipation;
    transformationModelingExercise: TransformationModelingExercise;
    result?: Result;
    resultWithComplaint?: Result;

    selectedEntities: string[];
    selectedRelationships: string[];

    submission: TransformationModelingSubmission;

    assessmentResult?: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    problemModel: UMLModel; // input model for Apollon
    umlModel: UMLModel; // input model for Apollon
    hasElements = false; // indicates if the current model has at least one element
    isSaving: boolean;

    explanation: string; // current explanation on text editor

    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isLoading: boolean;
    isLate: boolean; // indicates if the submission is late
    ComplaintType = ComplaintType;
    private examMode = false;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private apollonDiagramService: ApollonDiagramService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private router: Router,
        private participationWebsocketService: ParticipationWebsocketService,
    ) {
        this.isSaving = false;
        this.isLoading = true;
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            if (params['participationId']) {
                this.modelingSubmissionService.getLatestSubmissionForModelingEditor(params['participationId']).subscribe(
                    (modelingSubmission) => {
                        this.updateModelingSubmission(modelingSubmission);
                    },
                    (error) => {
                        if (error.status === 403) {
                            this.router.navigate(['accessdenied']);
                        }
                    },
                );
            }
        });
        window.scroll(0, 0);
    }

    // Updates component with the given modeling submission
    private updateModelingSubmission(modelingSubmission: TransformationModelingSubmission) {
        if (!modelingSubmission) {
            this.jhiAlertService.error('artemisApp.apollonDiagram.submission.noSubmission');
        }

        this.submission = modelingSubmission;

        // reconnect participation <--> result
        if (getLatestSubmissionResult(modelingSubmission)) {
            modelingSubmission.participation!.results = [getLatestSubmissionResult(modelingSubmission)!];
        }
        this.participation = modelingSubmission.participation as StudentParticipation;

        // reconnect participation <--> submission
        this.participation.submissions = [<TransformationModelingSubmission>omit(modelingSubmission, 'participation')];

        this.transformationModelingExercise = this.participation.exercise as ModelingExercise;
        this.transformationModelingExercise.studentParticipations = [this.participation];
        this.examMode = !!this.transformationModelingExercise.exerciseGroup;
        this.transformationModelingExercise.participationStatus = participationStatus(this.transformationModelingExercise);
        if (this.transformationModelingExercise.diagramType == undefined) {
            this.transformationModelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        // checks if the student started the exercise after the due date
        this.isLate =
            this.transformationModelingExercise &&
            !!this.transformationModelingExercise.dueDate &&
            !!this.participation.initializationDate &&
            moment(this.participation.initializationDate).isAfter(this.transformationModelingExercise.dueDate);
        this.isAfterAssessmentDueDate = !this.transformationModelingExercise.assessmentDueDate || moment().isAfter(this.transformationModelingExercise.assessmentDueDate);
        if (this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
        }
        this.explanation = this.submission.explanationText ?? '';
        this.problemModel = JSON.parse(this.transformationModelingExercise.problemModel!);
        if (getLatestSubmissionResult(this.submission) && this.isAfterAssessmentDueDate) {
            this.result = getLatestSubmissionResult(this.submission);
        }
        this.resultWithComplaint = getFirstResultWithComplaint(this.submission);
        if (this.submission.submitted && this.result && this.result.completionDate) {
            this.modelingAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                this.assessmentResult = assessmentResult;
                this.prepareAssessmentData();
            });
        }
        this.isLoading = false;
    }

    submit(): void {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }
        this.updateSubmissionWithCurrentValues();
        if (TransformationModelingSubmissionComponent.isModelEmpty(this.submission.model)) {
            this.jhiAlertService.warning('artemisApp.modelingEditor.empty');
            return;
        }
        this.isSaving = true;
        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.transformationModelingExercise.id!).subscribe(
                (response) => {
                    this.submission = response.body!;
                    if (this.submission.model) {
                        this.umlModel = JSON.parse(this.submission.model);
                        this.hasElements = this.umlModel.elements && this.umlModel.elements.length !== 0;
                    }
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.transformationModelingExercise;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.participation, this.transformationModelingExercise);
                    this.transformationModelingExercise.studentParticipations = [this.participation];
                    this.transformationModelingExercise.participationStatus = participationStatus(this.transformationModelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);

                    if (this.isLate) {
                        this.jhiAlertService.warning('entity.action.submitDeadlineMissedAlert');
                    } else {
                        this.jhiAlertService.success('entity.action.submitSuccessfulAlert');
                    }

                    this.onSaveSuccess();
                },
                (error: HttpErrorResponse) => this.onSaveError(error),
            );
        } else {
            this.modelingSubmissionService.create(this.submission, this.transformationModelingExercise.id!).subscribe(
                (response) => {
                    this.submission = response.body!;
                    this.participation = this.submission.participation as StudentParticipation;
                    this.participation.exercise = this.transformationModelingExercise;
                    this.transformationModelingExercise.studentParticipations = [this.participation];
                    this.transformationModelingExercise.participationStatus = participationStatus(this.transformationModelingExercise);
                    this.result = getLatestSubmissionResult(this.submission);
                    if (this.isLate) {
                        this.jhiAlertService.warning('artemisApp.modelingEditor.submitDeadlineMissed');
                    } else {
                        this.jhiAlertService.success('artemisApp.modelingEditor.submitSuccessful');
                    }
                    this.onSaveSuccess();
                },
                (error: HttpErrorResponse) => this.onSaveError(error),
            );
        }
    }

    private onSaveSuccess() {
        this.isSaving = false;
    }

    private onSaveError(error?: HttpErrorResponse) {
        if (error) {
            console.error(error.message);
        }
        this.jhiAlertService.error('artemisApp.modelingEditor.error');
        this.isSaving = false;
    }

    private static isModelEmpty(model?: string): boolean {
        const umlModel: UMLModel = model ? JSON.parse(model) : undefined;
        return !umlModel || !umlModel.elements || umlModel.elements.length === 0;
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
    }

    /**
     * Check whether or not a assessmentResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        return this.assessmentResult ? getUnreferencedFeedback(this.assessmentResult.feedbacks) : undefined;
    }

    /**
     * Find "Referenced Feedback" item for Result, if it exists.
     */
    get referencedFeedback(): Feedback[] | undefined {
        return this.assessmentResult?.feedbacks?.filter((feedbackElement) => feedbackElement.reference != undefined);
    }

    /**
     * Updates the model of the submission with the current Apollon model state
     * and the explanation text of submission with current explanation if explanation is defined
     */
    updateSubmissionWithCurrentValues(): void {
        if (!this.submission) {
            this.submission = new TransformationModelingSubmission();
        }
        this.submission.explanationText = this.explanation;
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
     * Prepare assessment data for displaying the assessment information to the student.
     */
    private prepareAssessmentData(): void {
        this.initializeAssessmentInfo();
    }

    /**
     * Retrieves names for displaying the assessment and calculates the total score
     */
    private initializeAssessmentInfo(): void {
        if (this.assessmentResult && this.assessmentResult.feedbacks && this.umlModel) {
            this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.assessmentResult, this.umlModel);
            let totalScore = 0;
            for (const feedback of this.assessmentResult.feedbacks) {
                totalScore += feedback.credits!;
            }
            this.totalScore = totalScore;
        }
    }

    /**
     * Handles changes of the model element selection in Apollon. This is used for displaying
     * only the feedback of the selected model elements.
     * @param selection the new selection
     */
    onSelectionChanged(selection: Selection) {
        this.selectedEntities = selection.elements;
        for (const selectedEntity of this.selectedEntities) {
            this.selectedEntities.push(...this.getSelectedChildren(selectedEntity));
        }
        this.selectedRelationships = selection.relationships;
    }

    /**
     * Returns the elementIds of all the children of the element with the given elementId
     * or an empty list, if no children exist for this element.
     */
    private getSelectedChildren(elementId: string): string[] {
        if (!this.umlModel || !this.umlModel.elements) {
            return [];
        }
        return this.umlModel.elements.filter((element) => element.owner === elementId).map((element) => element.id);
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    shouldBeDisplayed(feedback: Feedback): boolean {
        if ((!this.selectedEntities || this.selectedEntities.length === 0) && (!this.selectedRelationships || this.selectedRelationships.length === 0)) {
            return true;
        }
        const referencedModelType = feedback.referenceType! as UMLElementType;
        if (referencedModelType in UMLRelationshipType) {
            return this.selectedRelationships.indexOf(feedback.referenceId!) > -1;
        } else {
            return this.selectedEntities.indexOf(feedback.referenceId!) > -1;
        }
    }

    canDeactivate(): boolean {
        if (!this.modelingEditor || !this.modelingEditor.isApollonEditorMounted) {
            return true;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();
        const explanationIsUpToDate = this.explanation === (this.submission.explanationText ?? '');
        return !this.modelHasUnsavedChanges(model) && explanationIsUpToDate;
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

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any): void {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * counts the number of model elements
     * is used in the submit() function
     */
    calculateNumberOfModelElements(): number {
        if (this.submission && this.submission.model) {
            const umlModel = JSON.parse(this.submission.model);
            return umlModel.elements.length + umlModel.relationships.length;
        }
        return 0;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return (
            this.transformationModelingExercise &&
            !this.examMode &&
            (!this.transformationModelingExercise.dueDate || moment(this.transformationModelingExercise.dueDate).isSameOrAfter(moment()))
        );
    }

    get submitButtonTooltip(): string {
        if (!this.isLate) {
            if (this.isActive && !this.transformationModelingExercise.dueDate) {
                return 'entity.action.submitNoDeadlineTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.deadlineMissedTooltip';
            }
        }

        return 'entity.action.submitDeadlineMissedTooltip';
    }
}
