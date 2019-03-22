import { Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DiagramType, ApollonEditor, ApollonOptions, UMLModel, ApollonMode } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import * as $ from 'jquery';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Result, ResultService } from '../entities/result';
import { ModelingAssessmentService } from '../entities/modeling-assessment';
import { AccountService } from '../core';
import { Submission } from '../entities/submission';
import { HttpErrorResponse } from '@angular/common/http';
import { Conflict } from 'app/entities/modeling-assessment/conflict.model';
import { isNullOrUndefined } from 'util';
import { Feedback } from 'app/entities/feedback';
import { ModelElementType } from 'app/entities/modeling-assessment/uml-element.model';

@Component({
    selector: 'jhi-apollon-diagram-tutor',
    templateUrl: './apollon-diagram-tutor.component.html',
    styleUrls: ['./apollon-diagram-tutor.component.scss'],
    providers: [ModelingAssessmentService]
})
export class ApollonDiagramTutorComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer')
    editorContainer: ElementRef;
    @Output()
    onNewResult = new EventEmitter<Result>();

    apollonEditor: ApollonEditor | null = null;
    selectedEntities: string[];
    selectedRelationships: string[];

    submission: ModelingSubmission;
    modelingExercise: ModelingExercise;
    result: Result;
    conflicts: Map<string, Conflict>;
    assessmentsNames: Map<string, Map<string, string>>;
    assessmentsAreValid = false;
    invalidError = '';
    totalScore = 0;
    positions: Map<string, Point>;
    busy: boolean;
    done = true;
    timeout: any;
    userId: number;
    isAuthorized: boolean;
    ignoreConflicts: false;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingExerciseService: ModelingExerciseService,
        private resultService: ResultService,
        private modelingAssessmentService: ModelingAssessmentService,
        private accountService: AccountService
    ) {
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then(user => {
            this.userId = user.id;
        });
        this.isAuthorized = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        this.route.params.subscribe(params => {
            const submissionId = Number(params['submissionId']);
            let nextOptimal: boolean;
            this.route.queryParams.subscribe(query => {
                nextOptimal = query['optimal'] === 'true';
            });

            this.modelingSubmissionService.getSubmission(submissionId).subscribe(res => {
                this.submission = res;
                this.modelingExercise = this.submission.participation.exercise as ModelingExercise;
                this.result = this.submission.result;
                if (this.result.feedbacks) {
                    this.result = this.modelingAssessmentService.convertResult(this.result);
                } else {
                    this.result.feedbacks = [];
                }
                this.submission.participation.results = [this.result];
                this.result.participation = this.submission.participation;
                /**
                 * set diagramType to class diagram if exercise is null, use case or communication
                 * apollon does not support use case and communication yet
                 */
                if (
                    this.modelingExercise.diagramType === null ||
                    this.modelingExercise.diagramType === DiagramType.UseCaseDiagram ||
                    this.modelingExercise.diagramType === DiagramType.ObjectDiagram
                ) {
                    this.modelingExercise.diagramType = DiagramType.ClassDiagram;
                }
                if (this.submission.model) {
                    this.initializeApollonEditor(JSON.parse(this.submission.model));
                } else {
                    this.jhiAlertService.error(`No model could be found for this submission.`);
                }
                if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.rated) {
                    this.jhiAlertService.info('arTeMiSApp.apollonDiagram.lock');
                }
                if (nextOptimal) {
                    this.modelingAssessmentService.getPartialAssessment(submissionId).subscribe((result: Result) => {
                        this.result = result;
                        this.initializeAssessments();
                        this.checkScoreBoundaries();
                    });
                } else {
                    if (this.result && this.result.feedbacks) {
                        this.initializeAssessments();
                        this.checkScoreBoundaries();
                    }
                }
            });
        });
    }

    ngOnDestroy() {
        clearTimeout(this.timeout);
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }
    }

    /**
     * Initializes the Apollon editor in read only mode.
     */
    initializeApollonEditor(initialModel: UMLModel) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            mode: ApollonMode.ReadOnly,
            model: initialModel,
            type: this.modelingExercise.diagramType
        });

        this.apollonEditor.subscribeToSelectionChange(selection => {
            const selectedEntities: string[] = [];
            for (const entity of selection.elements) {
                selectedEntities.push(entity);
            }
            this.selectedEntities = selectedEntities;
            const selectedRelationships: string[] = [];
            for (const rel of selection.relationships) {
                selectedRelationships.push(rel);
            }
            this.selectedRelationships = selectedRelationships;
        });

        this.initializeAssessments();

        const apollonDiv = $('.apollon-editor > div');
        const assessmentsDiv = $('.assessments__container');
        assessmentsDiv.scrollTop(apollonDiv.scrollTop());
        assessmentsDiv.scrollLeft(apollonDiv.scrollLeft());

        apollonDiv.on('scroll', function() {
            assessmentsDiv.scrollTop(apollonDiv.scrollTop());
            assessmentsDiv.scrollLeft(apollonDiv.scrollLeft());
        });
    }

    /**
     * Creates the assessment elements for each model element.
     * The default score is 0.
     */
    initializeAssessments() {
        if (!this.apollonEditor || !this.result || !this.result.feedbacks) {
            return;
        }
        const model = this.apollonEditor.model;
        let cardinalityAllEntities = model.elements.length + model.relationships.length;
        for (const elem of model.elements) {
            cardinalityAllEntities += model.elements.byId[elem].attributes.length + model.elements.byId[elem].methods.length;
        }

        if (this.result.feedbacks.length < cardinalityAllEntities) {
            const isPartialAssessment = this.result.feedbacks.length !== 0;
            for (const elem of model.elements) {
                const assessment = new Feedback(elem, ModelElementType.CLASS, 0, '');
                this.pushAssessmentIfNotExists(elem, assessment, isPartialAssessment);
                for (const attribute of model.elements.byId[elem].attributes) {
                    const attributeAssessment = new Feedback(attribute.id, ModelElementType.ATTRIBUTE, 0, '');
                    this.pushAssessmentIfNotExists(attribute.id, attributeAssessment, isPartialAssessment);
                }
                for (const method of model.elements.byId[elem].methods) {
                    const methodAssessment = new Feedback(method.id, ModelElementType.METHOD, 0, '');
                    this.pushAssessmentIfNotExists(method.id, methodAssessment, isPartialAssessment);
                }
            }
            for (const relationship of model.relationships) {
                const assessment = new Feedback(relationship, ModelElementType.RELATIONSHIP, 0, '');
                this.pushAssessmentIfNotExists(relationship, assessment, isPartialAssessment);
            }
        }

        if (this.result.feedbacks) {
            this.setAssessmentsNames();
            this.getElementPositions();
        }
    }

    /**
     * Adds the partial assessment to the assessment array if it does not exist in the assessment array.
     */
    pushAssessmentIfNotExists(id: string, newAssessment: Feedback, partialAssessment: boolean) {
        if (partialAssessment) {
            for (const feedback of this.result.feedbacks) {
                if (feedback.referenceId === id) {
                    return;
                }
            }
        }
        this.result.feedbacks.push(newAssessment);
    }

    saveAssessment() {
        this.checkScoreBoundaries();
        this.removeCircularDependencies();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id).subscribe((result: Result) => {
            this.result = result;
            this.onNewResult.emit(this.result);
            this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.saveSuccessful');
        }, () => {
            this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.saveFailed');
        });
    }

    submit() {
        this.checkScoreBoundaries();
        this.removeCircularDependencies();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id, true, this.ignoreConflicts).subscribe((result: Result) => {
            result.participation.results = [result];
            this.result = result;
            this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.submitSuccessful');
            this.conflicts = undefined;
            this.done = false;
        }, (error: HttpErrorResponse) => {
            if (error.status === 409) {
                this.conflicts = new Map();
                (error.error as Conflict[]).forEach(conflict => this.conflicts.set(conflict.elementInConflict.id, conflict));
                this.highlightElementsWithConflict();
                this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailedWithConflict');
            } else {
                this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailed');
            }
        });
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score the boundaries on the server.
     */
    checkScoreBoundaries() {
        if (!this.result.feedbacks || this.result.feedbacks.length === 0) {
            this.totalScore = 0;
        }
        const maxScore = this.modelingExercise.maxScore;
        let totalScore = 0;
        for (const feedback of this.result.feedbacks) {
            totalScore += feedback.credits;
            // TODO: due to the JS rounding problems, it might be the case that we get something like 16.999999999999993 here, so we better round this number
            if (feedback.credits == null) {
                this.assessmentsAreValid = false;
                return (this.invalidError = 'The score field must be a number and can not be empty!');
            }
        }
        this.totalScore = totalScore;
        this.assessmentsAreValid = true;
        this.invalidError = '';
    }

    /**
     * Removes the circular dependencies in the nested objects.
     * Otherwise, we would get a JSON error when trying to send the submission to the server.
     */
    removeCircularDependencies() {
        this.submission.result.participation = null;
        this.submission.result.submission = null;
    }

    setAssessmentsNames() {
        this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.result, this.apollonEditor.model);
    }

    /**
     * Checks whether a model element is selected or not.
     * Is used for displaying the corresponding assessment element.
     */
    isSelected(id: string, type: ModelElementType) {
        if (type === ModelElementType.RELATIONSHIP) {
            if (!this.selectedRelationships) {
                return false;
            } else if (this.selectedRelationships && this.selectedRelationships.indexOf(id) > -1) {
                return true;
            }
        } else if (type === ModelElementType.CLASS) {
            if (!this.selectedEntities) {
                return false;
            } else if (this.selectedEntities && this.selectedEntities.indexOf(id) > -1) {
                return true;
            }
        } else {
            if (this.apollonEditor) {
                const model = this.apollonEditor.model;
                if (this.selectedEntities) {
                    for (const entity of model.elements) {
                        if (type === ModelElementType.ATTRIBUTE) {
                            for (const attribute of model.elements.byId[entity].attributes) {
                                if (attribute.id === id && this.selectedEntities.indexOf(entity) > -1) {
                                    return true;
                                }
                            }
                        } else if (type === ModelElementType.METHOD) {
                            for (const method of model.elements.byId[entity].methods) {
                                if (method.id === id && this.selectedEntities.indexOf(entity) > -1) {
                                    return true;
                                }
                            }
                        }
                    }
                } else {
                    return false;
                }
            }
        }
    }

    getElementPositions() {
        this.positions = this.modelingAssessmentService.getElementPositions(this.result, this.apollonEditor.getState());
    }

    assessNextOptimal(attempts: number) {
        if (attempts > 4) {
            this.busy = false;
            this.done = true;
            this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
            return;
        }
        this.busy = true;
        this.timeout = setTimeout(
            () => {
                this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(optimal => {
                    const nextOptimalSubmissionIds = optimal.body.map((submission: Submission) => submission.id);
                    if (nextOptimalSubmissionIds.length === 0) {
                        this.assessNextOptimal(attempts + 1);
                    } else {
                        // TODO: Workaround We have to fake path change to make angular reload the component
                        const addition = this.router.url.includes('apollon-diagrams2') ? '' : '2';
                        this.router.navigateByUrl(
                            `/apollon-diagrams${addition}/exercise/${this.modelingExercise.id}/${nextOptimalSubmissionIds.pop()}/tutor`
                        );
                    }
                });
            },
            attempts === 0 ? 0 : 500 + (attempts - 1) * 1000
        );
    }

    numberToArray(n: number, startFrom: number): number[] {
        n = n > 5 ? 5 : n;
        n = n < -5 ? -5 : n;
        return this.modelingAssessmentService.numberToArray(n, startFrom);
    }

    previousState() {
        this.router.navigate(['course', this.modelingExercise.course.id, 'exercise', this.modelingExercise.id, 'assessment']);
    }

    private highlightElementsWithConflict() {
        const model = this.apollonEditor.model;
        const entitiesToHighlight: string[] = model.elements.allIds.filter((id: string) => {
            if (this.conflicts.has(id)) {
                return true;
            }
            if (model.elements.byId[id].attributes.find(attribute => this.conflicts.has(attribute.id))) {
                return true;
            }
            if (model.elements.byId[id].methods.find(method => this.conflicts.has(method.id))) {
                return true;
            }
            return false;
        });
        const relationshipsToHighlight: string [] = model.relationships.allIds.filter(id => this.conflicts.has(id));

        entitiesToHighlight.forEach(id => {
            document.getElementById(id).style.fill = 'rgb(248, 214, 217)';
        });

        // TODO MJ highlight relation entities. currently do not have unique id
        // relationshipsToHighlight.forEach(id => {
        //     document.getElementById(id).style.color = 'rgb(248, 214, 217)';
        // })
    }
}
