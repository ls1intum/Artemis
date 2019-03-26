import {Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, ViewChild} from '@angular/core';
import {JhiAlertService} from 'ng-jhipster';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ApollonOptions, Point, State} from '@ls1intum/apollon';
import {ActivatedRoute, Router} from '@angular/router';
import {ModelingSubmission, ModelingSubmissionService} from '../../entities/modeling-submission';
import {DiagramType, ModelingExercise, ModelingExerciseService} from '../../entities/modeling-exercise';
import {Result, ResultService} from '../../entities/result';
import {genericRetryStrategy, ModelingAssessmentService} from '../../entities/modeling-assessment';
import {AccountService} from '../../core';
import {HttpErrorResponse} from '@angular/common/http';
import {Conflict} from 'app/entities/modeling-assessment/conflict.model';
import {isNullOrUndefined} from 'util';
import {retryWhen} from 'rxjs/operators';

@Component({
    selector: 'jhi-modeling-assessment',
    templateUrl: './modeling-assessment.component.html',
    styleUrls: ['./modeling-assessment.component.scss'],
    providers: [ModelingAssessmentService]
})
export class ModelingAssessmentComponent implements OnInit, OnDestroy {
    @ViewChild('editorContainer')
    editorContainer: ElementRef;
    @Output()
    onNewResult = new EventEmitter<Result>();

    submission: ModelingSubmission;
    modelingExercise: ModelingExercise;
    diagramType: DiagramType;
    result: Result;
    conflicts: Map<string, Conflict>;
    assessmentsAreValid = false;
    invalidError = '';
    totalScore = 0;
    busy: boolean;
    done = true;
    timeout: any;
    userId: number;
    isAuthorized: boolean;
    ignoreConflicts: false;
    // assessmentsNames: Map<string, Map<string, string>>;
    // positions: Map<string, Point>;

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

            this.modelingSubmissionService.getSubmission(submissionId).subscribe((submission: ModelingSubmission) => {
                this.submission = submission;
                this.modelingExercise = this.submission.participation.exercise as ModelingExercise;
                this.result = this.submission.result;
                if (this.result.feedbacks) {
                    this.result = this.modelingAssessmentService.convertResult(this.result);
                } else {
                    this.result.feedbacks = [];
                }
                this.submission.participation.results = [this.result]; // TODO why is this necessary
                this.result.participation = this.submission.participation;
                /**
                 * set diagramType to class diagram if exercise is null, use case or communication
                 * apollon does not support use case and communication yet
                 */
                this.diagramType = isNullOrUndefined(
                    this.modelingExercise.diagramType ||
                    this.modelingExercise.diagramType === DiagramType.USE_CASE ||
                    this.modelingExercise.diagramType === DiagramType.COMMUNICATION
                )
                    ? DiagramType.CLASS
                    : this.modelingExercise.diagramType;
                if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.rated) {
                    this.jhiAlertService.info('arTeMiSApp.apollonDiagram.lock');
                }
                if (nextOptimal) {
                    this.modelingAssessmentService.getPartialAssessment(submissionId).subscribe((result: Result) => {
                        this.result = result;
                        // this.initializeAssessments();
                        this.checkScoreBoundaries();
                    });
                } else {
                    if (this.result && this.result.feedbacks) {
                        // this.initializeAssessments();
                        this.checkScoreBoundaries();
                    }
                }
            });
        });
    }

    ngOnDestroy() {
        clearTimeout(this.timeout);
    }

    saveAssessment() {
        this.checkScoreBoundaries();
        this.removeCircularDependencies();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id).subscribe(
            (result: Result) => {
                this.result = result;
                this.onNewResult.emit(this.result);
                this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.saveSuccessful');
            },
            () => {
                this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.saveFailed');
            }
        );
    }

    submit() {
        this.checkScoreBoundaries();
        this.removeCircularDependencies();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id, true, this.ignoreConflicts).subscribe(
            (result: Result) => {
                result.participation.results = [result];
                this.result = result;
                this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.submitSuccessful');
                this.conflicts = undefined;
                this.done = false;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 409) {
                    this.conflicts = new Map();
                    (error.error as Conflict[]).forEach(conflict => this.conflicts.set(conflict.elementInConflict.id, conflict));
                    // this.highlightElementsWithConflict();
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailed');
                }
            }
        );
    }

    /**
     * Creates the assessment elements for each model element.
     * The default score is 0.
     */
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

    assessNextOptimal() {
        this.busy = true;
        this.modelingAssessmentService
            .getOptimalSubmissions(this.modelingExercise.id)
            .pipe(
                retryWhen(genericRetryStrategy({maxRetryAttempts: 5, scalingDuration: 1000}))
            )
            .subscribe(
                (optimal: number[]) => {
                    this.busy = false;
                    this.router.navigateByUrl(`/apollon-diagrams/exercise/${this.modelingExercise.id}/${optimal.pop()}/tutor`);
                },
                () => {
                    this.busy = false;
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                }
            );
    }

    previousState() {
        this.router.navigate(['course', this.modelingExercise.course.id, 'exercise', this.modelingExercise.id, 'assessment']);
    }

    /**
     * Adds the partial assessment to the assessment array if it does not exist in the assessment array.
     */
    // pushAssessmentIfNotExists(id: string, newAssessment: Feedback, partialAssessment: boolean) {
    //     if (partialAssessment) {
    //         for (const feedback of this.result.feedbacks) {
    //             if (feedback.referenceId === id) {
    //                 return;
    //             }
    //         }
    //     }
    //     this.result.feedbacks.push(newAssessment);
    // }

    // numberToArray(n: number, startFrom: number): number[] {
    //     n = n > 5 ? 5 : n;
    //     n = n < -5 ? -5 : n;
    //     return this.modelingAssessmentService.numberToArray(n, startFrom);
    // }
    // isSelected(id: string, type: ModelElementType) {
    //     if (type === ModelElementType.RELATIONSHIP) {
    //         if (!this.selectedRelationships) {
    //             return false;
    //         } else if (this.selectedRelationships && this.selectedRelationships.indexOf(id) > -1) {
    //             return true;
    //         }
    //     } else if (type === ModelElementType.CLASS) {
    //         if (!this.selectedEntities) {
    //             return false;
    //         } else if (this.selectedEntities && this.selectedEntities.indexOf(id) > -1) {
    //             return true;
    //         }
    //     } else {
    //         if (this.apollonEditor) {
    //             const editorState = this.apollonEditor.getState();
    //             if (this.selectedEntities) {
    //                 for (const entity of editorState.entities.allIds) {
    //                     if (type === ModelElementType.ATTRIBUTE) {
    //                         for (const attribute of editorState.entities.byId[entity].attributes) {
    //                             if (attribute.id === id && this.selectedEntities.indexOf(entity) > -1) {
    //                                 return true;
    //                             }
    //                         }
    //                     } else if (type === ModelElementType.METHOD) {
    //                         for (const method of editorState.entities.byId[entity].methods) {
    //                             if (method.id === id && this.selectedEntities.indexOf(entity) > -1) {
    //                                 return true;
    //                             }
    //                         }
    //                     }
    //                 }
    //             } else {
    //                 return false;
    //             }
    //         }
    //     }
    // }

    /**
     * Checks whether a model element is selected or not.
     * Is used for displaying the corresponding assessment element.
     */

    // getElementPositions() {
    //     this.positions = this.modelingAssessmentService.getElementPositions(this.result, this.apollonEditor.getState());
    // }
    // initializeAssessments() {
    //     if (!this.apollonEditor || !this.result || !this.result.feedbacks) {
    //         return;
    //     }
    //     const editorState = this.apollonEditor.getState();
    //     let cardinalityAllEntities = editorState.entities.allIds.length + editorState.relationships.allIds.length;
    //     for (const elem of editorState.entities.allIds) {
    //         cardinalityAllEntities += editorState.entities.byId[elem].attributes.length + editorState.entities.byId[elem].methods.length;
    //     }
    //
    //     if (this.result.feedbacks.length < cardinalityAllEntities) {
    //         const isPartialAssessment = this.result.feedbacks.length !== 0;
    //         for (const elem of editorState.entities.allIds) {
    //             const assessment = new Feedback(elem, ModelElementType.CLASS, 0, '');
    //             this.pushAssessmentIfNotExists(elem, assessment, isPartialAssessment);
    //             for (const attribute of editorState.entities.byId[elem].attributes) {
    //                 const attributeAssessment = new Feedback(attribute.id, ModelElementType.ATTRIBUTE, 0, '');
    //                 this.pushAssessmentIfNotExists(attribute.id, attributeAssessment, isPartialAssessment);
    //             }
    //             for (const method of editorState.entities.byId[elem].methods) {
    //                 const methodAssessment = new Feedback(method.id, ModelElementType.METHOD, 0, '');
    //                 this.pushAssessmentIfNotExists(method.id, methodAssessment, isPartialAssessment);
    //             }
    //         }
    //         for (const elem of editorState.relationships.allIds) {
    //             const assessment = new Feedback(elem, ModelElementType.RELATIONSHIP, 0, '');
    //             this.pushAssessmentIfNotExists(elem, assessment, isPartialAssessment);
    //         }
    //     }
    //
    //     if (this.result.feedbacks) {
    //         // this.setAssessmentsNames();
    //         this.getElementPositions();
    //     }
    // }

    // setAssessmentsNames() {
    //     this.assessmentsNames = this.modelingAssessmentService.getNamesForAssessments(this.result, this.apollonEditor.getState());
    // }

    // private highlightElementsWithConflict() {
    //     const state: State = this.apollonEditor.getState();
    //     const entitiesToHighlight: string[] = state.entities.allIds.filter((id: string) => {
    //         if (this.conflicts.has(id)) {
    //             return true;
    //         }
    //         if (state.entities.byId[id].attributes.find(attribute => this.conflicts.has(attribute.id))) {
    //             return true;
    //         }
    //         if (state.entities.byId[id].methods.find(method => this.conflicts.has(method.id))) {
    //             return true;
    //         }
    //         return false;
    //     });
    //     const relationshipsToHighlight: string[] = state.relationships.allIds.filter(id => this.conflicts.has(id));
    //
    //     entitiesToHighlight.forEach(id => {
    //         document.getElementById(id).style.fill = 'rgb(248, 214, 217)';
    //     });
    //
    //     // TODO MJ highlight relation entities. currently do not have unique id
    //     // relationshipsToHighlight.forEach(id => {
    //     //     document.getElementById(id).style.color = 'rgb(248, 214, 217)';
    //     // })
    // }
}
