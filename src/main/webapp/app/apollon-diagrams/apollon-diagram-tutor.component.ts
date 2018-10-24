import { Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import ApollonEditor, { ApollonOptions, Point, State } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import * as $ from 'jquery';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { DiagramType, ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Result, ResultService } from '../entities/result';
import { ModelElementType, ModelingAssessment, ModelingAssessmentService } from '../entities/modeling-assessment';
import { Principal } from '../core';
import { Submission } from '../entities/submission';

@Component({
    selector: 'jhi-apollon-diagram-tutor',
    templateUrl: './apollon-diagram-tutor.component.html',
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
    assessments: ModelingAssessment[];
    assessmentsNames: Map<string, Map<string, string>>;
    assessmentsAreValid: boolean;
    invalidError = '';
    totalScore = 0;
    positions: Map<string, Point>;
    busy: boolean;
    done: boolean;
    timeout: any;
    accountId: number;
    isAuthorized: boolean;

    constructor(
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private modelingSubmissionService: ModelingSubmissionService,
        private modelingExerciseService: ModelingExerciseService,
        private resultService: ResultService,
        private modelingAssessmentService: ModelingAssessmentService,
        private principal: Principal
    ) {
        this.assessments = [];
        this.assessmentsAreValid = false;
        this.done = true;
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.principal.identity().then(account => {
            this.accountId = account.id;
        });
        this.isAuthorized = this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        this.route.params.subscribe(params => {
            const id = Number(params['submissionId']);
            const exerciseId = Number(params['exerciseId']);
            let nextOptimal: boolean;
            this.route.queryParams.subscribe(query => {
                nextOptimal = query['optimal'] === 'true';
            });

            this.modelingAssessmentService.getDataForEditor(exerciseId, id).subscribe(data => {
                this.modelingExercise = data.modelingExercise as ModelingExercise;
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
                this.submission = data.modelingSubmission as ModelingSubmission;
                if (this.submission.model) {
                    this.initializeApollonEditor(JSON.parse(this.submission.model));
                } else {
                    this.jhiAlertService.error(`No model could be found for this submission.`);
                }
                data.result.participation.results = [data.result];
                this.result = data.result as Result;
                if ((this.result.assessor == null || this.result.assessor.id === this.accountId) && !this.result.rated) {
                    this.jhiAlertService.info('arTeMiSApp.apollonDiagram.lock');
                }
                if (nextOptimal) {
                    this.modelingAssessmentService.getPartialAssessment(exerciseId, id).subscribe(assessments => {
                        this.assessments = assessments.body;
                        this.initializeAssessments();
                        this.checkScoreBoundaries();
                    });
                } else {
                    if (data.assessments) {
                        this.assessments = data.assessments;
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
    initializeApollonEditor(initialState: State) {
        if (this.apollonEditor !== null) {
            this.apollonEditor.destroy();
        }

        this.apollonEditor = new ApollonEditor(this.editorContainer.nativeElement, {
            initialState,
            mode: 'READ_ONLY',
            diagramType: <ApollonOptions['diagramType']>this.modelingExercise.diagramType
        });

        this.apollonEditor.subscribeToSelectionChange(selection => {
            const selectedEntities: string[] = [];
            for (const entity of selection.entityIds) {
                selectedEntities.push(entity);
            }
            this.selectedEntities = selectedEntities;
            const selectedRelationships: string[] = [];
            for (const rel of selection.relationshipIds) {
                selectedRelationships.push(rel);
            }
            this.selectedRelationships = selectedRelationships;
        });

        this.initializeAssessments();

        const apollonDiv = $('.apollon-editor > div > div');
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
        if (!this.apollonEditor) {
            return;
        }
        const editorState = this.apollonEditor.getState();
        let cardinalityAllEntities = editorState.entities.allIds.length + editorState.relationships.allIds.length;
        for (const elem of editorState.entities.allIds) {
            cardinalityAllEntities += editorState.entities.byId[elem].attributes.length + editorState.entities.byId[elem].methods.length;
        }

        if (this.assessments.length < cardinalityAllEntities) {
            const isPartialAssessment = this.assessments.length !== 0;
            for (const elem of editorState.entities.allIds) {
                const assessment = new ModelingAssessment(elem, ModelElementType.CLASS, 0, '');
                this.pushAssessmentIfNotExists(elem, assessment, isPartialAssessment);
                for (const attribute of editorState.entities.byId[elem].attributes) {
                    const attributeAssessment = new ModelingAssessment(attribute.id, ModelElementType.ATTRIBUTE, 0, '');
                    this.pushAssessmentIfNotExists(attribute.id, attributeAssessment, isPartialAssessment);
                }
                for (const method of editorState.entities.byId[elem].methods) {
                    const methodAssessment = new ModelingAssessment(method.id, ModelElementType.METHOD, 0, '');
                    this.pushAssessmentIfNotExists(method.id, methodAssessment, isPartialAssessment);
                }
            }
            for (const elem of editorState.relationships.allIds) {
                const assessment = new ModelingAssessment(elem, ModelElementType.RELATIONSHIP, 0, '');
                this.pushAssessmentIfNotExists(elem, assessment, isPartialAssessment);
            }
        }

        if (this.assessments) {
            this.setAssessmentsNames();
            this.getElementPositions();
        }
    }

    /**
     * Adds the partial assessment to the assessment array if it does not exist in the assessment array.
     */
    pushAssessmentIfNotExists(id: string, newAssessment: ModelingAssessment, partialAssessment: boolean) {
        if (partialAssessment) {
            for (const elem of this.assessments) {
                if (elem.id === id) {
                    return;
                }
            }
        }
        this.assessments.push(newAssessment);
    }

    saveAssessment() {
        this.checkScoreBoundaries();
        this.modelingAssessmentService.save(this.assessments, this.modelingExercise.id, this.result.id).subscribe(res => {
            this.result = res.body;
            this.onNewResult.emit(this.result);
            this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.saveSuccessful');
        });
    }

    submit() {
        this.checkScoreBoundaries();
        this.modelingAssessmentService.submit(this.assessments, this.modelingExercise.id, this.result.id).subscribe(res => {
            res.body.participation.results = [res.body];
            this.result = res.body;
            this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.submitSuccessful');
            this.done = false;
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
        if (!this.assessments || this.assessments.length === 0) {
            this.totalScore = 0;
        }
        const maxScore = this.modelingExercise.maxScore;
        let totalScore = 0;
        for (const assessment of this.assessments) {
            totalScore += assessment.credits;
            // TODO: due to the JS rounding problems, it might be the case that we get something like 16.999999999999993 here, so we better round this number
            if (assessment.credits == null) {
                this.assessmentsAreValid = false;
                return (this.invalidError = 'The score field must be a number and can not be empty!');
            }
        }
        this.totalScore = totalScore;

        /*if (totalScore < 0) {
            this.assessmentsAreValid = false;
            this.invalidError = 'The total score (' + totalScore + ') is negative!';*/
        // We want tutors to be able to give more than maxScore - this also helps compass coming up with a more accurate result
        /*} else if (totalScore > maxScore) {
            this.assessmentsAreValid = false;
            this.invalidError = 'The total score (' + totalScore + ') is greater than the max score (' + maxScore + ')!';*/
        this.assessmentsAreValid = true;
        this.invalidError = '';
    }

    setAssessmentsNames() {
        this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.assessments, this.apollonEditor.getState());
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
                const editorState = this.apollonEditor.getState();
                if (this.selectedEntities) {
                    for (const entity of editorState.entities.allIds) {
                        if (type === ModelElementType.ATTRIBUTE) {
                            for (const attribute of editorState.entities.byId[entity].attributes) {
                                if (attribute.id === id && this.selectedEntities.indexOf(entity) > -1) {
                                    return true;
                                }
                            }
                        } else if (type === ModelElementType.METHOD) {
                            for (const method of editorState.entities.byId[entity].methods) {
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
        this.positions = this.modelingAssessmentService.getElementPositions(this.assessments, this.apollonEditor.getState());
    }

    assessNextOptimal(attempts: number) {
        if (attempts > 4) {
            this.busy = false;
            this.done = true;
            this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
            return;
        }
        this.busy = true;
        this.timeout = setTimeout(() => {
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
        }, attempts === 0 ? 0 : 500 + (attempts - 1) * 1000);
    }

    numberToArray(n: number, startFrom: number): number[] {
        n = n > 5 ? 5 : n;
        n = n < -5 ? -5 : n;
        return this.modelingAssessmentService.numberToArray(n, startFrom);
    }

    previousState() {
        this.router.navigate(['course', this.modelingExercise.course.id, 'exercise', this.modelingExercise.id, 'assessment']);
    }
}
