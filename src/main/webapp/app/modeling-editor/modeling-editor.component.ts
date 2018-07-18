import { Component, ElementRef, OnDestroy, OnInit, ViewChild, HostListener } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Participation, ParticipationService } from '../entities/participation';
import { ActivatedRoute } from '@angular/router';
import { ApollonDiagramService } from '../entities/apollon-diagram/apollon-diagram.service';
import ApollonEditor from '@ls1intum/apollon';
import { JhiAlertService } from 'ng-jhipster';
import { Result, ResultService } from '../entities/result';
import { ParticipationResultService } from '../entities/result/result.service';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelingAssessment, ModelingAssessmentService } from '../entities/modeling-assessment';
import * as $ from 'jquery';
import { ModelingEditorService } from './modeling-editor.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ComponentCanDeactivate } from '../shared';
import { Observable } from 'rxjs/Observable';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-modeling-editor',
    templateUrl: './modeling-editor.component.html',
    providers: [ParticipationResultService, ModelingAssessmentService, ApollonDiagramService]
})
export class ModelingEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    @ViewChild('editorContainer') editorContainer: ElementRef;

    private subscription: Subscription;
    participation: Participation;
    modelingExercise: ModelingExercise;
    result: Result;

    apollonEditor: ApollonEditor | null = null;
    selectedEntities: any[];
    selectedRelationships: any[];

    submission: any = {};
    submissionState;
    assessments: ModelingAssessment[];
    assessmentsNames;
    totalScore: number;
    positions: {};
    diagramState = null;
    isActive: boolean;
    isSaving: boolean;

    constructor(
        private apollonDiagramService: ApollonDiagramService,
        private participationService: ParticipationService,
        private modelingExerciseService: ModelingExerciseService,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private participationResultService: ParticipationResultService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private modelingEditorService: ModelingEditorService,
        private modalService: NgbModal,
        private translateService: TranslateService
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            if (params['participationId']) {
                this.modelingEditorService.get(params['participationId']).subscribe(data => {
                    this.participation = data.participation;
                    if (data.participation.results) {
                        this.result = data.participation.results[0];
                    }
                    this.modelingExercise = this.participation.exercise;
                    this.isActive = this.modelingExercise.dueDate == null || Date.now() <= Date.parse(this.modelingExercise.dueDate);
                    this.submission = data.modelingSubmission;
                    if (this.submission && this.submission.model) {
                        this.initializeApollonEditor(JSON.parse(this.submission.model));
                    } else {
                        this.initializeApollonEditor({});
                    }
                    if (this.submission && this.submission.submitted && this.result && this.result.rated) {
                        if (data.assessments) {
                            this.assessments = data.assessments;
                            this.initializeAssessmentInfo();
                        } else {
                            this.modelingAssessmentService.find(params['participationId'], this.submission.id).subscribe(assessments => {
                                this.assessments = assessments.body;
                                this.initializeAssessmentInfo();
                            });
                        }
                    }
                });
            }
        });
        window.scroll(0, 0);
    }

    initializeApollonEditor(initialState) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        if (this.submission && this.submission.submitted) {
            this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
                initialState,
                mode: 'READ_ONLY'
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

            const apollonDiv = $('.apollon-editor > div > div');
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
                mode: 'MODELING_ONLY'
            });
            this.updateSubmissionModel();
            setInterval(() => {
                this.saveDiagram();
            }, 60000);
        }
    }

    saveDiagram() {
        if (!this.submission) {
            this.submission = new ModelingSubmission();
        }
        this.submission.submitted = false;
        this.updateSubmissionModel();
        this.isSaving = true;

        if (this.submission.id) {
            this.modelingSubmissionService.update(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(res => {
                this.result = res.body;
                this.submission = this.result.submission;
                this.isSaving = false;
                this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
            });
        } else {
            this.modelingSubmissionService.create(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(sub => {
                this.result = sub.body;
                this.submission = this.result.submission;
                this.isSaving = false;
                this.jhiAlertService.success('arTeMiSApp.modelingEditor.saveSuccessful');
            });
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
        this.submission.submitted = true;
        this.modelingSubmissionService.update(this.submission, this.modelingExercise.course.id, this.modelingExercise.id).subscribe(res => {
            this.result = res.body;
            this.submission = this.result.submission;
            // Compass has already calculated a result
            if (this.result.assessmentType) {
                const participation = this.participation;
                participation.results = [this.result];
                this.participation = Object.assign({}, participation);
                this.modelingAssessmentService.find(this.participation.id, this.submission.id).subscribe(assessments => {
                    this.assessments = assessments.body;
                    this.initializeAssessmentInfo();
                });
            }
            if (this.isActive) {
                this.jhiAlertService.success('arTeMiSApp.modelingEditor.submitSuccessful');
            } else {
                this.jhiAlertService.warning('arTeMiSApp.modelingEditor.submitDeadlineMissed');
            }
        });
        this.initializeApollonEditor(JSON.parse(this.submission.model));
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    updateSubmissionModel() {
        this.diagramState = this.apollonEditor.getState();
        const diagramJson = JSON.stringify(this.diagramState);
        if (this.submission) {
            this.submission.model = diagramJson;
        }
    }

    initializeAssessmentInfo() {
        if (this.assessments && this.submission && this.submission.model) {
            this.submissionState = JSON.parse(this.submission.model);
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

    numberToArray(n: number, startFrom: number): number[] {
        n = (n > 5) ? 5 : n;
        n = (n < -5) ? -5 : n;
        return this.modelingAssessmentService.numberToArray(n, startFrom);
    }

    isSelected(id, type) {
        if ((!this.selectedEntities || this.selectedEntities.length === 0) && (!this.selectedRelationships || this.selectedRelationships.length === 0)) {
            return true;
        }
        if (type !== 'relationship') {
            return this.selectedEntities.indexOf(id) > -1;
        } else {
            return this.selectedRelationships.indexOf(id) > -1;
        }
    }

    open(content) {
        this.modalService.open(content, {size: 'lg'});
    }

    // function to check whether there are pending changes
    canDeactivate(): Observable<boolean> | boolean {
        if ((!this.submission && JSON.stringify(this.apollonEditor.getState()) !== '') || (this.submission && this.submission.model && this.submission.model !== JSON.stringify(this.apollonEditor.getState()))) {
            return false;
        }
        return true;
    }

    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }
}
