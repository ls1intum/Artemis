import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, Input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { ModelingExercise } from '../entities/modeling-exercise';
import { Participation } from '../entities/participation';
import { ApollonDiagramService } from '../entities/apollon-diagram';
import { ApollonEditor, ApollonMode, DiagramType, ElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { Result } from '../entities/result';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import * as $ from 'jquery';
import { ModelingEditorService } from './modeling-editor.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ComponentCanDeactivate } from '../shared';
import { JhiWebsocketService } from '../core';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { ModelingAssessmentService } from 'app/modeling-assessment/modeling-assessment.service';

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
    submissionState: UMLModel;

    // TODO: rename
    assessmentResult: Result;
    assessmentsNames: Map<string, Map<string, string>>;
    totalScore: number;

    /**
     * an Array of model element IDs as keys with {x: <xOffset>, y: <yOffset>} as values
     * is used for positioning the assessment symbols
     */
    positions: Map<string, { x: number; y: number }>;

    umlModel: UMLModel = null;
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
        private modelingEditorService: ModelingEditorService,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private router: Router,
        private artemisMarkdown: ArtemisMarkdown
    ) {
        this.isSaving = false;
        this.autoSaveTimer = 0;
    }

    ngOnInit() {
        if (this.isExampleSubmission) {
            return; // different behavior for example submissions - exercise and submission set from parent component
        }

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
                        this.problemStatement = this.artemisMarkdown.htmlForMarkdown(this._modelingExercise.problemStatement);
                        this.checkDiagramType();
                        this.isActive = this._modelingExercise.dueDate == null || new Date() <= moment(this._modelingExercise.dueDate).toDate();
                        this._submission = modelingSubmission;
                        if (this._submission && this._submission.id && !this._submission.submitted) {
                            this.subscribeToWebsocket();
                        }
                        if (this._submission && this._submission.result) {
                            this.result = this._submission.result;
                        }
                        if (this._submission && this._submission.submitted && this.result && this.result.completionDate) {
                            this.modelingAssessmentService.getAssessment(this._submission.id).subscribe((assessmentResult: Result) => {
                                this.assessmentResult = assessmentResult;
                                this.initializeAssessmentInfo();
                                this.initializeApollonEditor(JSON.parse(this._submission.model));
                            });
                        }
                        this.initializeEditor();
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

    /**
     * Set diagramType to class diagram if it is null
     */
    private checkDiagramType(): void {
        if (this._modelingExercise) {
            if (this._modelingExercise.diagramType == null) {
                this._modelingExercise.diagramType = DiagramType.ClassDiagram;
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
                    this.modelingAssessmentService.getAssessment(this._submission.id).subscribe((assessmentResult: Result) => {
                        this.assessmentResult = assessmentResult;
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
     * If it was already submitted, the Apollon editor is loaded in Assessment read-only mode.
     * Otherwise, it is loaded in the modeling mode and an auto save timer is started.
     */
    initializeApollonEditor(initialModel: UMLModel) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        if (this._submission && this._submission.submitted) {
            clearInterval(this.autoSaveInterval);
            if (this.assessmentResult && this.assessmentResult.feedbacks && this.assessmentResult.feedbacks.length > 0) {
                initialModel.assessments = this.assessmentResult.feedbacks.map(feedback => {
                    return {
                        modelElementId: feedback.referenceId,
                        elementType: feedback.referenceType,
                        score: feedback.credits,
                        feedback: feedback.text,
                    };
                });
            }
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                model: initialModel,
                mode: ApollonMode.Assessment,
                readonly: true,
                type: this._modelingExercise.diagramType
            });

            this.apollonEditor.subscribeToSelectionChange(selection => {
                this.selectedEntities = selection.elements;
                this.selectedRelationships = selection.relationships;
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
                model: initialModel,
                mode: ApollonMode.Modelling,
                readonly: false,
                type: this._modelingExercise.diagramType
            });

            this.updateSubmissionModel();
            if (this.isExampleSubmission) {
                return; // disable auto save for example submissions
            }
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
            this.modelingSubmissionService.update(this._submission, this._modelingExercise.id).subscribe(
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
            this.modelingSubmissionService.create(this._submission, this._modelingExercise.id).subscribe(
                submission => {
                    this._submission = submission.body;
                    this.result = this._submission.result;
                    this.isSaving = false;
                    this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
                    this.isActive = this._modelingExercise.dueDate == null || new Date() <= moment(this._modelingExercise.dueDate).toDate();
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
        if (!this.umlModel || this.umlModel.elements.length === 0) {
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
            this.modelingSubmissionService.update(this._submission, this._modelingExercise.id).subscribe(
                response => {
                    this._submission = response.body;
                    this.result = this._submission.result;
                    // Compass has already calculated a result
                    if (this.result && this.result.assessmentType) {
                        const participation = this.participation;
                        participation.results = [this.result];
                        this.participation = Object.assign({}, participation);
                        this.modelingAssessmentService.getAssessment(this._submission.id).subscribe((assessmentResult: Result) => {
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
     * Updates the model of the submission with the current Apollon model state
     */
    updateSubmissionModel() {
        this.umlModel = this.apollonEditor.model;
        const diagramJson = JSON.stringify(this.umlModel);
        if (this._submission && diagramJson != null) {
            this._submission.model = diagramJson;
        }
    }

    /**
     * Retrieves names for displaying the assessment and calculates the total score
     */
    initializeAssessmentInfo() {
        if (this.assessmentResult && this._submission && this._submission.model) {
            this.submissionState = JSON.parse(this._submission.model);
            this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.assessmentResult, this.submissionState);
            let totalScore = 0;
            for (const feedback of this.assessmentResult.feedbacks) {
                totalScore += feedback.credits;
            }
            this.totalScore = totalScore;
        }
    }

    /**
     * Checks whether a model element in the modeling editor is selected.
     */
    isSelected(modelElementId: string, type: ElementType) {
        if (
            (!this.selectedEntities || this.selectedEntities.length === 0) &&
            (!this.selectedRelationships || this.selectedRelationships.length === 0)
        ) {
            return true;
        }
        // TODO does this work?
        if (type in UMLRelationshipType) {
            return this.selectedRelationships.indexOf(modelElementId) > -1;
        } else {
            return this.selectedEntities.indexOf(modelElementId) > -1;
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
        if (this._submission && this._submission.submitted) {
            return true;
        }
        const jsonModel = JSON.stringify(this.apollonEditor.model);
        if ((!this._submission && this.apollonEditor && this.apollonEditor.model.elements.length > 0 && jsonModel !== '') ||
            (this._submission && this._submission.model && JSON.parse(this._submission.model).version === this.apollonEditor.model.version && this._submission.model !== jsonModel)) {
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
        this.assessmentResult = null;
        this.result = null; // TODO: think about how we could visualize old results and assessments after retry

        clearInterval(this.autoSaveInterval);
        this.initializeEditor();
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
