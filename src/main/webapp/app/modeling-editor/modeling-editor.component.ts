import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { DiagramType, ModelingExercise } from '../entities/modeling-exercise';
import { Participation } from '../entities/participation';
import { ApollonDiagramService } from '../entities/apollon-diagram/apollon-diagram.service';
import ApollonEditor, { ApollonOptions, Point, State } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { Result } from '../entities/result';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelElementType, ModelingAssessment, ModelingAssessmentService } from '../entities/modeling-assessment';
import * as $ from 'jquery';
import { ModelingEditorService } from './modeling-editor.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ComponentCanDeactivate } from '../shared';
import { JhiWebsocketService } from '../core';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    providers: [ModelingAssessmentService, ApollonDiagramService]
})
export class ModelingEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild('editorContainer')
    editorContainer: ElementRef;

    private subscription: Subscription;
    participation: Participation;
    modelingExercise: ModelingExercise;
    result: Result;

    apollonEditor: ApollonEditor | null = null;
    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;

    /**
     * JSON with the following keys: editor, entities, interactiveElements, relationships
     * format is given by Apollon
     */
    submissionState: State;

    //TODO: rename
    assessments: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    /**
     * an Array of model element IDs as keys with {x: <xOffset>, y: <yOffset>} as values
     * is used for positioning the assessment symbols
     */
    positions: Map<string, Point>;

    diagramState: State = null;
    isActive: boolean;
    isSaving: boolean;
    retryStarted = false;
    autoSaveInterval: number;
    autoSaveTimer: number;

    websocketChannel: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private apollonDiagramService: ApollonDiagramService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private modelingEditorService: ModelingEditorService,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private router: Router
    ) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.modelingEditorService.get(params['participationId']).subscribe(
                    modelingSubmission => {
                        // reconnect participation <--> result
                        if (modelingSubmission.result) {
                            modelingSubmission.participation.results = [modelingSubmission.result];
                        }
                        this.participation = modelingSubmission.participation;
                        this.modelingExercise = this.participation.exercise as ModelingExercise;
                        /**
                         * set diagramType to class diagram if exercise is null, use case or communication
                         * apollon does not support use case and communication yet
                         */
                        if (
                            this.modelingExercise.diagramType === null ||
                            this.modelingExercise.diagramType === DiagramType.USE_CASE ||
                            this.modelingExercise.diagramType === DiagramType.COMMUNICATION
                        ) {
                            this.modelingExercise.diagramType = DiagramType.CLASS;
                        }
                        this.isActive =
                            this.modelingExercise.dueDate == null || new Date() <= moment(this.modelingExercise.dueDate).toDate();
                        this.submission = modelingSubmission;
                        if (this.submission && this.submission.id && !this.submission.submitted) {
                            this.subscribeToWebsocket();
                        }
                        if (this.submission && this.submission.result) {
                            this.result = this.submission.result;
                        }
                        if (this.submission && this.submission.model) {
                            this.initializeApollonEditor(JSON.parse(this.submission.model));
                        } else {
                            this.initializeApollonEditor(null);
                        }
                        if (this.submission && this.submission.submitted && this.result && this.result.completionDate) {
                            if (this.result.assessments) {
                                this.assessments = JSON.parse(this.result.assessments);
                                this.initializeAssessmentInfo();
                            } else {
                                this.modelingAssessmentService
                                    .find(params['participationId'], this.submission.id)
                                    .subscribe(assessments => {
                                        this.assessments = assessments.body;
                                        this.initializeAssessmentInfo();
                                    });
                            }
                        }
                    },
                    error => {
                        if (error.status === 403) {
                            this.router.navigate(['accessdenied']);
                        }
                    }
                );
            }
        });
        window.scroll(0, 0);
    }

    subscribeToWebsocket() {
        if (!this.submission && !this.submission.id) {
            return;
        }
        this.websocketChannel = '/user/topic/modelingSubmission/' + this.submission.id;
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(submission => {
            if (submission.submitted) {
                this.submission = submission;
                if (this.submission.result && this.submission.result.rated) {
                    this.modelingAssessmentService.find(this.submission.participation.id, this.submission.id).subscribe(assessments => {
                        this.assessments = assessments.body;
                        this.initializeAssessmentInfo();
                    });
                }
                this.jhiAlertService.info('arTeMiSApp.modelingEditor.autoSubmit');
                if (this.submission.model) {
                    this.initializeApollonEditor(JSON.parse(this.submission.model));
                }
                this.isActive = false;
            }
        });
    }

    /**
     * This function initialized the Apollon editor depending on the submission status.
     * If it was already submitted, the Apollon editor is loaded in read-only mode.
     * Otherwise, it is loaded in the modeling mode and an auto save timer is started.
     */
    initializeApollonEditor(initialState: State) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        if (this.submission && this.submission.submitted) {
            clearInterval(this.autoSaveInterval);
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                initialState,
                mode: 'READ_ONLY',
                diagramType: <ApollonOptions['diagramType']>this.modelingExercise.diagramType
            });

            const state = this.apollonEditor.getState();
            this.apollonEditor.subscribeToSelectionChange(selection => {
                const selectedEntities = [];
                for (const entity of selection.entityIds) {
                    selectedEntities.push(entity);
                    for (const attribute of state.entities.byId[entity].attributes) {
                        selectedEntities.push(attribute.id);
                    }
                    for (const method of state.entities.byId[entity].methods) {
                        selectedEntities.push(method.id);
                    }
                }
                this.selectedEntities = selectedEntities;
                const selectedRelationships = [];
                for (const rel of selection.relationshipIds) {
                    selectedRelationships.push(rel);
                }
                this.selectedRelationships = selectedRelationships;
            });

            const apollonDiv = $('.apollon-editor > div');
            const assessmentsDiv = $('.assessments__container');
            assessmentsDiv.scrollTop(apollonDiv.scrollTop());
            assessmentsDiv.scrollLeft(apollonDiv.scrollLeft());

            apollonDiv.on('scroll', function() {
                assessmentsDiv.scrollTop(apollonDiv.scrollTop());
                assessmentsDiv.scrollLeft(apollonDiv.scrollLeft());
            });
        } else {
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                initialState,
                mode: 'MODELING_ONLY',
                diagramType: <ApollonOptions['diagramType']>this.modelingExercise.diagramType
            });
            this.updateSubmissionModel();
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
    }

    saveDiagram() {
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
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.result = this.submission.result;
                    if (!this.submission.model) {
                        this.updateSubmissionModel();
                    }
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                },
                error => {
                    this.isSaving = false;
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                }
            );
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(
                submission => {
                    this.submission = submission.body;
                    this.result = this.submission.result;
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                    this.isActive = this.modelingExercise.dueDate == null || new Date() <= this.modelingExercise.dueDate.toDate();
                    this.subscribeToWebsocket();
                },
                error => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.isSaving = false;
                }
            );
        }
    }

    submit() {
        if (!this.submission) {
            return;
        }
        this.updateSubmissionModel();
        if (!this.diagramState || this.diagramState.entities.allIds.length === 0) {
            this.jhiAlertService.warning('arTeMiSApp.modelingEditor.empty');
            return;
        }

        let confirmSubmit = true;
        if (this.calculateNumberOfModelElements() < 10) {
            confirmSubmit = window.confirm(
                'Are you sure you want to submit? You cannot edit your model anymore until you get an assessment!'
            );
        }

        if (confirmSubmit) {
            this.submission.submitted = true;
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(
                response => {
                    this.submission = response.body;
                    this.result = this.submission.result;
                    // Compass has already calculated a result
                    if (this.result && this.result.assessmentType) {
                        const participation = this.participation;
                        participation.results = [this.result];
                        this.participation = Object.assign({}, participation);
                        this.modelingAssessmentService.find(this.participation.id, this.submission.id).subscribe(assessments => {
                            this.assessments = assessments.body;
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
                    clearInterval(this.autoSaveInterval);
                    this.initializeApollonEditor(JSON.parse(this.submission.model));
                    if (this.websocketChannel) {
                        this.jhiWebsocketService.unsubscribe(this.websocketChannel);
                    }
                },
                err => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this.submission.submitted = false;
                }
            );
        }
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
        clearInterval(this.autoSaveInterval);

        if (this.websocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.websocketChannel);
        }
    }

    /**
     * Saves the current model state in the attribute diagramState
     */
    updateSubmissionModel() {
        this.diagramState = this.apollonEditor.getState();
        const diagramJson = JSON.stringify(this.diagramState);
        if (this.submission && diagramJson != null) {
            this.submission.model = diagramJson;
        }
    }

    /**
     * Retrieves names and positions for displaying the assessment and calculates the total score
     */
    initializeAssessmentInfo() {
        if (this.assessments && this.submission && this.submission.model) {
            this.submissionState = JSON.parse(this.submission.model);
            //TODO: use Result instead of ModelingAssessment
            this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.assessments, this.submissionState);
            this.positions = this.modelingAssessmentService.getElementPositions(this.assessments, this.submissionState);
            let totalScore = 0;
            for (const assessment of this.assessments) {
                totalScore += assessment.credits;
            }
            this.totalScore = totalScore;
        }
    }

    getElementPositions() {
        //TODO: use Result instead of ModelingAssessment
        this.positions = this.modelingAssessmentService.getElementPositions(this.assessments, this.apollonEditor.getState());
    }

    /**
     * This function is used for limiting the number of symbols for the assessment to 5.
     * For each point an <i> element is created
     */
    numberToArray(n: number, startFrom: number): number[] {
        n = n > 5 ? 5 : n;
        n = n < -5 ? -5 : n;
        return this.modelingAssessmentService.numberToArray(n, startFrom);
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    isSelected(id: string, type: ModelElementType) {
        if (
            (!this.selectedEntities || this.selectedEntities.length === 0) &&
            (!this.selectedRelationships || this.selectedRelationships.length === 0)
        ) {
            return true;
        }
        if (type !== ModelElementType.RELATIONSHIP) {
            return this.selectedEntities.indexOf(id) > -1;
        } else {
            return this.selectedRelationships.indexOf(id) > -1;
        }
    }

    /**
     * This function opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    // function to check whether there are pending changes
    canDeactivate(): Observable<boolean> | boolean {
        if (
            (!this.submission &&
                this.apollonEditor &&
                this.apollonEditor.getState().entities.allIds.length > 0 &&
                JSON.stringify(this.apollonEditor.getState()) !== '') ||
            (this.submission &&
                this.submission.model &&
                JSON.parse(this.submission.model).version === this.apollonEditor.getState().version &&
                this.submission.model !== JSON.stringify(this.apollonEditor.getState()))
        ) {
            return false;
        }
        return true;
    }

    // displays the alert for confirming leaving the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * starts a retry and resets necessary attributes
     * the retry is only persisted after saving or submitting the model
     */
    retry() {
        this.retryStarted = true;
        const currentModel = this.submission.model;
        this.submission = new ModelingSubmission();
        this.submission.model = currentModel;
        this.assessments = [];
        this.result = null; // TODO: think about how we could visualize old results and assessments after retry

        clearInterval(this.autoSaveInterval);
        if (this.submission.model) {
            this.initializeApollonEditor(JSON.parse(this.submission.model));
        } else {
            this.initializeApollonEditor(null);
        }
    }

    /**
     * counts the number of model elements
     * is used in the submit() function
     */
    calculateNumberOfModelElements(): number {
        if (this.diagramState) {
            let total = this.diagramState.entities.allIds.length + this.diagramState.relationships.allIds.length;
            for (const elem of this.diagramState.entities.allIds) {
                total += this.diagramState.entities.byId[elem].attributes.length;
                total += this.diagramState.entities.byId[elem].methods.length;
            }
            return total;
        }
        return 0;
    }
}
