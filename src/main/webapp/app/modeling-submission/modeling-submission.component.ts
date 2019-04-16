import { Component, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { ModelingExercise } from '../entities/modeling-exercise';
import { Participation } from '../entities/participation';
import { ApollonDiagramService } from '../entities/apollon-diagram';
import { DiagramType, ElementType, Selection, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { Result } from '../entities/result';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ComponentCanDeactivate } from '../shared';
import { JhiWebsocketService } from '../core';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ModelingEditorComponent } from 'app/modeling-editor';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';

@Component({
    selector: 'jhi-modeling-submission',
    templateUrl: './modeling-submission.component.html',
    styleUrls: ['./modeling-submission.component.scss'],
})
// TODO CZ: move assessment stuff to separate assessment result view?
export class ModelingSubmissionComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild(ModelingEditorComponent)
    modelingEditor: ModelingEditorComponent;

    private subscription: Subscription;
    participation: Participation;
    modelingExercise: ModelingExercise;
    result: Result;

    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;

    assessmentResult: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    umlModel: UMLModel;
    isActive: boolean;
    isSaving: boolean;
    retryStarted = false;
    autoSaveInterval: number;
    autoSaveTimer: number;

    websocketChannel: string;

    problemStatement: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private apollonDiagramService: ApollonDiagramService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private router: Router,
        private artemisMarkdown: ArtemisMarkdown,
    ) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.modelingSubmissionService.getDataForModelingEditor(params['participationId']).subscribe(
                    modelingSubmission => {
                        if (!modelingSubmission) {
                            this.jhiAlertService.error('arTeMiSApp.apollonDiagram.submission.noSubmission');
                        }
                        // reconnect participation <--> result
                        if (modelingSubmission.result) {
                            modelingSubmission.participation.results = [modelingSubmission.result];
                        }
                        this.participation = modelingSubmission.participation;
                        this.modelingExercise = this.participation.exercise as ModelingExercise;
                        this.problemStatement = this.artemisMarkdown.htmlForMarkdown(this.modelingExercise.problemStatement);
                        /**
                         * set diagramType to class diagram if exercise is null
                         */
                        if (this.modelingExercise.diagramType == null) {
                            this.modelingExercise.diagramType = DiagramType.ClassDiagram;
                        }
                        this.isActive = this.modelingExercise.dueDate == null || new Date() <= moment(this.modelingExercise.dueDate).toDate();
                        this.submission = modelingSubmission;
                        if (this.submission.model) {
                            this.umlModel = JSON.parse(this.submission.model);
                        }
                        if (this.submission.id && !this.submission.submitted) {
                            this.subscribeToWebsocket();
                        }
                        if (this.submission.result) {
                            this.result = this.submission.result;
                        }
                        if (this.submission.submitted && this.result && this.result.completionDate) {
                            this.modelingAssessmentService.getAssessment(this.submission.id).subscribe((assessmentResult: Result) => {
                                this.assessmentResult = assessmentResult;
                                this.initializeAssessmentInfo();
                            });
                        }
                        this.setAutoSaveTimer();
                    },
                    error => {
                        if (error.status === 403) {
                            this.router.navigate(['accessdenied']);
                        }
                    },
                );
            }
        });
        window.scroll(0, 0);
    }

    subscribeToWebsocket(): void {
        if (!this.submission && !this.submission.id) {
            return;
        }
        this.websocketChannel = '/user/topic/modelingSubmission/' + this.submission.id;
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(submission => {
            if (submission.submitted) {
                this.submission = submission;
                if (this.submission.model) {
                    this.umlModel = JSON.parse(this.submission.model);
                }
                if (this.submission.result && this.submission.result.rated) {
                    this.modelingAssessmentService.getAssessment(this.submission.id).subscribe((assessmentResult: Result) => {
                        this.assessmentResult = assessmentResult;
                        this.initializeAssessmentInfo();
                    });
                }
                this.jhiAlertService.info('arTeMiSApp.modelingEditor.autoSubmit');
                this.isActive = false;
            }
        });
    }

    /**
     * This function initialized the Apollon editor depending on the submission status.
     * If it was already submitted, the Apollon editor is loaded in Assessment read-only mode.
     * Otherwise, it is loaded in the modeling mode and an auto save timer is started.
     */
    setAutoSaveTimer(): void {
        if (this.submission.submitted) {
            return;
        }
        this.autoSaveTimer = 0;
        // auto save of submission if there are changes
        this.autoSaveInterval = window.setInterval(() => {
            this.autoSaveTimer++;
            if (this.submission && this.submission.submitted) {
                clearInterval(this.autoSaveInterval);
                this.autoSaveTimer = 0;
            }
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
        if (!this.submission) {
            this.submission = new ModelingSubmission();
        }
        this.submission.submitted = false;
        this.updateSubmissionModel();
        this.isSaving = true;
        this.autoSaveTimer = 0;

        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.result = this.submission.result;
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                },
                error => {
                    this.isSaving = false;
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                },
            );
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.id).subscribe(
                submission => {
                    this.submission = submission.body;
                    this.result = this.submission.result;
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                    this.isActive = this.modelingExercise.dueDate == null || new Date() <= moment(this.modelingExercise.dueDate).toDate();
                    this.subscribeToWebsocket();
                },
                error => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.isSaving = false;
                },
            );
        }
    }

    submit(): void {
        if (!this.submission) {
            return;
        }
        this.updateSubmissionModel();
        if (!this.umlModel || this.umlModel.elements.length === 0) {
            this.jhiAlertService.warning('arTeMiSApp.modelingEditor.empty');
            return;
        }

        let confirmSubmit = true;
        if (this.calculateNumberOfModelElements() < 10) {
            confirmSubmit = window.confirm('Are you sure you want to submit? You cannot edit your model anymore until you get an assessment!');
        }

        if (confirmSubmit) {
            this.submission.submitted = true;
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.umlModel = JSON.parse(this.submission.model);
                    this.result = this.submission.result;
                    // Compass has already calculated a result
                    if (this.result && this.result.assessmentType) {
                        const participation = this.participation;
                        participation.results = [this.result];
                        this.participation = Object.assign({}, participation);
                        this.modelingAssessmentService.getAssessment(this.submission.id).subscribe((assessmentResult: Result) => {
                            this.assessmentResult = assessmentResult;
                            this.initializeAssessmentInfo();
                        });
                        this.jhiAlertService.success('arTeMiSApp.modelingEditor.submitSuccessfulWithAssessment');
                    } else {
                        if (this.isActive) {
                            this.jhiAlertService.success('arTeMiSApp.modelingEditor.submitSuccessful');
                        } else {
                            this.jhiAlertService.warning('arTeMiSApp.modelingEditor.submitDeadlineMissed');
                        }
                    }
                    this.retryStarted = false;
                    if (this.websocketChannel) {
                        this.jhiWebsocketService.unsubscribe(this.websocketChannel);
                    }
                },
                err => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.submission.submitted = false;
                },
            );
        }
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
        clearInterval(this.autoSaveInterval);
        if (this.websocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.websocketChannel);
        }
    }

    /**
     * Updates the model of the submission with the current Apollon model state
     */
    updateSubmissionModel(): void {
        this.umlModel = this.modelingEditor.getCurrentModel();
        const diagramJson = JSON.stringify(this.umlModel);
        if (this.submission && diagramJson != null) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Retrieves names for displaying the assessment and calculates the total score
     */
    initializeAssessmentInfo(): void {
        if (this.assessmentResult && this.submission && this.submission.model) {
            this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.assessmentResult, this.umlModel);
            let totalScore = 0;
            for (const feedback of this.assessmentResult.feedbacks) {
                totalScore += feedback.credits;
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
        this.selectedRelationships = selection.relationships;
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    isSelected(modelElementId: string, type: ElementType): boolean {
        if ((!this.selectedEntities || this.selectedEntities.length === 0) && (!this.selectedRelationships || this.selectedRelationships.length === 0)) {
            return true;
        }
        if (type in UMLRelationshipType) {
            return this.selectedRelationships.indexOf(modelElementId) > -1;
        } else {
            return this.selectedEntities.indexOf(modelElementId) > -1;
        }
    }

    // function to check whether there are pending changes
    canDeactivate(): Observable<boolean> | boolean {
        if (this.submission && this.submission.submitted) {
            return true;
        }
        const model: UMLModel = this.modelingEditor.getCurrentModel();
        const jsonModel = JSON.stringify(model);
        if (
            ((!this.submission || !this.submission.model) && model.elements.length > 0 && jsonModel !== '') ||
            (this.submission && this.submission.model && JSON.parse(this.submission.model).version === model.version && this.submission.model !== jsonModel)
        ) {
            return false;
        }
        return true;
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any): void {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * starts a retry and resets necessary attributes
     * the retry is only persisted after saving or submitting the model
     */
    retry(): void {
        this.retryStarted = true;
        this.umlModel.assessments = [];
        this.submission = new ModelingSubmission();
        this.assessmentResult = null;
        this.result = null; // TODO: think about how we could visualize old results and assessments after retry
        clearInterval(this.autoSaveInterval);
        this.setAutoSaveTimer();
    }

    /**
     * counts the number of model elements
     * is used in the submit() function
     */
    calculateNumberOfModelElements(): number {
        if (this.umlModel) {
            return this.umlModel.elements.length + this.umlModel.relationships.length;
        }
        return 0;
    }
}
