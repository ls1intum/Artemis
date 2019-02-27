import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, Input } from '@angular/core';
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

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    providers: [ModelingAssessmentService, ApollonDiagramService]
})
export class ModelingEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild('editorContainer')
    editorContainer: ElementRef;

    @Input()
    isExampleSubmission = false;
    @Input()
    set submission(value: ModelingSubmission) {
        this._submission = value;
        this.initializeEditor();
    }
    @Input()
    set modelingExercise(value: ModelingExercise) {
        this._modelingExercise = value;
        this.checkDiagramType();
        this.initializeEditor();
    }

    private subscription: Subscription;
    participation: Participation;
    private _modelingExercise: ModelingExercise;
    result: Result;

    apollonEditor: ApollonEditor | null = null;
    selectedEntities: string[];
    selectedRelationships: string[];

    private _submission: ModelingSubmission;

    /**
     * JSON with the following keys: editor, entities, interactiveElements, relationships
     * format is given by Apollon
     */
    submissionState: State;

    assessments: ModelingAssessment[];
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
        // different behavior for example submissions
        if (!this.isExampleSubmission) {
            this.subscription = this.route.params.subscribe(params => {
                if (params['participationId']) {
                    this.modelingEditorService.get(params['participationId']).subscribe(
                        modelingSubmission => {
                            // reconnect participation <--> result
                            if (modelingSubmission.result) {
                                modelingSubmission.participation.results = [modelingSubmission.result];
                            }
                            this.participation = modelingSubmission.participation;
                            this._modelingExercise = this.participation.exercise as ModelingExercise;
                            this.checkDiagramType();
                            this.isActive =
                                this._modelingExercise.dueDate == null || new Date() <= moment(this._modelingExercise.dueDate).toDate();
                            this._submission = modelingSubmission;
                            if (this._submission && this._submission.id && !this._submission.submitted) {
                                this.subscribeToWebsocket();
                            }
                            if (this._submission && this._submission.result) {
                                this.result = this._submission.result;
                            }
                            this.initializeEditor();
                            if (this._submission && this._submission.submitted && this.result && this.result.completionDate) {
                                if (this.result.assessments) {
                                    this.assessments = JSON.parse(this.result.assessments);
                                    this.initializeAssessmentInfo();
                                } else {
                                    this.modelingAssessmentService
                                        .find(params['participationId'], this._submission.id)
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
        }
        window.scroll(0, 0);
    }

    /**
     * Set diagramType to class diagram if exercise is null, use case or communication diagram.
     * Apollon does not support use case and communication diagram types yet.
     */
    private checkDiagramType(): void {
        if (this._modelingExercise) {
            if (
                this._modelingExercise.diagramType === null ||
                this._modelingExercise.diagramType === DiagramType.USE_CASE ||
                this._modelingExercise.diagramType === DiagramType.COMMUNICATION
            ) {
                this._modelingExercise.diagramType = DiagramType.CLASS;
            }
        }
    }

    subscribeToWebsocket() {
        if (!this._submission && !this._submission.id) {
            return;
        }
        this.websocketChannel = '/user/topic/modelingSubmission/' + this._submission.id;
        this.jhiWebsocketService.subscribe(this.websocketChannel);
        this.jhiWebsocketService.receive(this.websocketChannel).subscribe(submission => {
            if (submission.submitted) {
                this._submission = submission;
                if (this._submission.result && this._submission.result.rated) {
                    this.modelingAssessmentService.find(this._submission.participation.id, this._submission.id).subscribe(assessments => {
                        this.assessments = assessments.body;
                        this.initializeAssessmentInfo();
                    });
                }
                this.jhiAlertService.info('arTeMiSApp.modelingEditor.autoSubmit');
                this.initializeEditor();
                this.isActive = false;
            }
        });
    }

    /**
     * Triggers the initialization of the Apollon editor.
     */
    private initializeEditor(): void {
        if (this._submission && this._submission.model) {
            this.initializeApollonEditor(JSON.parse(this._submission.model));
        } else {
            this.initializeApollonEditor(null);
        }
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

        if (this._submission && this._submission.submitted) {
            clearInterval(this.autoSaveInterval);
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                initialState,
                mode: 'READ_ONLY',
                diagramType: <ApollonOptions['diagramType']>this._modelingExercise.diagramType
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
                diagramType: <ApollonOptions['diagramType']>this._modelingExercise.diagramType
            });
            this.updateSubmissionModel();
            // auto save of submission if there are changes
            this.autoSaveInterval = window.setInterval(() => {
                this.autoSaveTimer++;
                if (this._submission && this._submission.submitted) {
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
        if (!this._submission) {
            this._submission = new ModelingSubmission();
        }
        this._submission.submitted = false;
        this.updateSubmissionModel();
        this.isSaving = true;
        this.autoSaveTimer = 0;

        if (this._submission.id) {
            this.modelingSubmissionService.update(this._submission, this._modelingExercise.course.id, this._modelingExercise.id).subscribe(
                response => {
                    this._submission = response.body;
                    this.result = this._submission.result;
                    if (!this._submission.model) {
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
            this.modelingSubmissionService.create(this._submission, this._modelingExercise.course.id, this._modelingExercise.id).subscribe(
                submission => {
                    this._submission = submission.body;
                    this.result = this._submission.result;
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                    this.isActive = this._modelingExercise.dueDate == null || new Date() <= this._modelingExercise.dueDate.toDate();
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
        if (!this._submission) {
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
            this._submission.submitted = true;
            this.modelingSubmissionService.update(this._submission, this._modelingExercise.course.id, this._modelingExercise.id).subscribe(
                response => {
                    this._submission = response.body;
                    this.result = this._submission.result;
                    // Compass has already calculated a result
                    if (this.result && this.result.assessmentType) {
                        const participation = this.participation;
                        participation.results = [this.result];
                        this.participation = Object.assign({}, participation);
                        this.modelingAssessmentService.find(this.participation.id, this._submission.id).subscribe(assessments => {
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
                    this.initializeApollonEditor(JSON.parse(this._submission.model));
                    if (this.websocketChannel) {
                        this.jhiWebsocketService.unsubscribe(this.websocketChannel);
                    }
                },
                err => {
                    this.jhiAlertService.error('arTeMiSApp.modelingEditor.error');
                    this._submission.submitted = false;
                }
            );
        }
    }

    ngOnDestroy() {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
        clearInterval(this.autoSaveInterval);

        if (this.websocketChannel) {
            this.jhiWebsocketService.unsubscribe(this.websocketChannel);
        }
    }

    /**
     * Saves the current model state in the attribute diagramState and updates the model of the submission object
     * with the current state of the Apollon editor.
     */
    updateSubmissionModel() {
        this.diagramState = this.apollonEditor.getState();
        const diagramJson = JSON.stringify(this.diagramState);
        if (this._submission && diagramJson != null) {
            this._submission.model = diagramJson;
        }
    }

    /**
     * Retrieves names and positions for displaying the assessment and calculates the total score
     */
    initializeAssessmentInfo() {
        if (this.assessments && this._submission && this._submission.model) {
            this.submissionState = JSON.parse(this._submission.model);
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
     * Opens the modal for the help dialog.
     */
    open(content: any) {
        this.modalService.open(content, { size: 'lg' });
    }

    /**
     * Checks whether there are pending changes of the model.
     */
    canDeactivate(): Observable<boolean> | boolean {
        if (
            (!this._submission &&
                this.apollonEditor &&
                this.apollonEditor.getState().entities.allIds.length > 0 &&
                JSON.stringify(this.apollonEditor.getState()) !== '') ||
            (this._submission &&
                this._submission.model &&
                JSON.parse(this._submission.model).version === this.apollonEditor.getState().version &&
                this._submission.model !== JSON.stringify(this.apollonEditor.getState()))
        ) {
            return false;
        }
        return true;
    }

    /**
     * Displays the alert for confirming leaving the page if there are unsaved changes.
     */
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
        const currentModel = this._submission.model;
        this._submission = new ModelingSubmission();
        this._submission.model = currentModel;
        this.assessments = [];
        this.result = null; // TODO: think about how we could visualize old results and assessments after retry

        clearInterval(this.autoSaveInterval);
        this.initializeEditor()
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

    /**
     * Triggers an update of the submission model, so that it represents the current state of the Apollon editor.
     * Returns the submission with the updated model afterwards. If no ModelingSubmission object exists, it initializes one first.
     * This function is used by the ExampleModelingSubmissionComponent to save the current state of the editor in a submission.
     */
    getCurrentState(): ModelingSubmission {
        if (!this._submission) {
            this._submission = new ModelingSubmission();
        }
        this.updateSubmissionModel();
        return this._submission;
    }
}
