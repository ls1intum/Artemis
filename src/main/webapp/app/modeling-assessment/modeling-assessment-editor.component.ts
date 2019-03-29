import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DiagramType, UMLModel } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Result, ResultService } from '../entities/result';
import { AccountService } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Conflict } from 'app/modeling-assessment/conflict.model';
import { genericRetryStrategy, ModelingAssessmentService } from 'app/modeling-assessment/modeling-assessment.service';
import { retryWhen } from 'rxjs/operators';

@Component({
    selector: 'jhi-apollon-diagram-tutor',
    templateUrl: './modeling-assessment-editor.component.html',
    styleUrls: ['./modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentEditorComponent implements OnInit, OnDestroy {
    submission: ModelingSubmission;
    model: UMLModel;
    modelingExercise: ModelingExercise;
    result: Result;
    conflicts: Map<string, Conflict>;

    assessmentsAreValid = false;
    invalidError = '';
    totalScore = 0;
    busy: boolean;
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
        private accountService: AccountService,
    ) {}

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
                nextOptimal = query['optimal'] === 'true'; // TODO CZ: do we need this flag?
            });
            this.loadSubmission(submissionId, nextOptimal);
        });
    }

    ngOnDestroy() {}

    initComponent() {}

    loadSubmission(submissionId: number, nextOptimal: boolean) {
        this.modelingSubmissionService.getSubmission(submissionId).subscribe(res => {
            this.submission = res;
            this.modelingExercise = this.submission.participation.exercise as ModelingExercise;
            this.result = this.submission.result;
            if (this.result.feedbacks) {
                this.result = this.modelingAssessmentService.convertResult(this.result);
            } else {
                this.result.feedbacks = [];
            }
            // this.updateElementFeedbackMapping(this.result.feedbacks, true);
            this.submission.participation.results = [this.result];
            this.result.participation = this.submission.participation;
            /**
             * set diagramType to class diagram if it is null
             */
            if (this.modelingExercise.diagramType == null) {
                this.modelingExercise.diagramType = DiagramType.ClassDiagram;
            }
            if (this.submission.model) {
                this.model = JSON.parse(this.submission.model);
            } else {
                this.jhiAlertService.error(`No model could be found for this submission.`);
            }
            if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.rated) {
                this.jhiAlertService.info('arTeMiSApp.apollonDiagram.lock');
            }
            if (nextOptimal) {
                this.modelingAssessmentService.getPartialAssessment(submissionId).subscribe((result: Result) => {
                    this.result = result;
                });
            }
            if (this.result) {
                this.calculateTotalScore();
            }
        });
    }

    saveAssessment() {
        this.removeCircularDependencies();
        //TODO get actual feedbacks from modeling-assessments.component
        this.calculateTotalScore();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id).subscribe(
            (result: Result) => {
                this.result = result;
                // this.updateElementFeedbackMapping(result.feedbacks);
                this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.saveSuccessful');
            },
            () => {
                this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.saveFailed');
            },
        );
    }

    submitAssessment() {
        this.removeCircularDependencies();
        // this.result.feedbacks = this.generateFeedbackFromAssessment();
        this.calculateTotalScore();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id, true, this.ignoreConflicts).subscribe(
            (result: Result) => {
                result.participation.results = [result];
                this.result = result;
                // this.updateElementFeedbackMapping(result.feedbacks);
                this.jhiAlertService.success('arTeMiSApp.apollonDiagram.assessment.submitSuccessful');
                this.conflicts = undefined;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 409) {
                    this.conflicts = new Map();
                    (error.error as Conflict[]).forEach(conflict => this.conflicts.set(conflict.conflictedElementId, conflict));
                    // this.highlightElementsWithConflict();
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailed');
                }
            },
        );
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    calculateTotalScore() {
        if (!this.result.feedbacks || this.result.feedbacks.length === 0) {
            this.totalScore = 0;
        }
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
    private removeCircularDependencies() {
        this.submission.result.participation = null;
        this.submission.result.submission = null;
    }

    assessNextOptimal() {
        this.busy = true;
        this.modelingAssessmentService
            .getOptimalSubmissions(this.modelingExercise.id)
            .pipe(retryWhen(genericRetryStrategy({ maxRetryAttempts: 5, scalingDuration: 1000 })))
            .subscribe(
                (optimal: number[]) => {
                    this.busy = false;
                    this.router.navigateByUrl(`/apollon-diagrams/exercise/${this.modelingExercise.id}/${optimal.pop()}/tutor`);
                    this.initComponent();
                },
                () => {
                    this.busy = false;
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                },
            );
    }
}
