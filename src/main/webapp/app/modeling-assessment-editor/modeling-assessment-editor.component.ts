import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DiagramType, UMLModel } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Result, ResultService } from '../entities/result';
import { AccountService } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';
import { genericRetryStrategy, ModelingAssessmentService } from 'app/modeling-assessment-editor/modeling-assessment.service';
import { retryWhen } from 'rxjs/operators';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-modeling-assessment-editor',
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
    submissionId: number;
    totalScore = 0;
    busy: boolean;
    userId: number;
    isAuthorized = false;
    isAtLeastInstructor = false;

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
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        this.route.params.subscribe(params => {
            this.submissionId = Number(params['submissionId']);
            this.loadSubmission(this.submissionId);
        });
    }

    checkAuthorization() {
        this.isAuthorized = this.result && this.result.assessor && this.result.assessor.id === this.userId;
    }

    ngOnDestroy() {}

    loadSubmission(submissionId: number) {
        this.modelingSubmissionService.getSubmission(submissionId).subscribe(
            (submission: ModelingSubmission) => {
                this.submission = submission;
                this.modelingExercise = this.submission.participation.exercise as ModelingExercise;
                this.result = this.submission.result;
                if (this.result.feedbacks) {
                    this.result = this.modelingAssessmentService.convertResult(this.result);
                } else {
                    this.result.feedbacks = [];
                }
                this.submission.participation.results = [this.result];
                this.result.participation = this.submission.participation;
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
                this.checkAuthorization();
                this.validateFeedback();
            },
            error => {
                this.submission = undefined;
                this.modelingExercise = undefined;
                this.result = undefined;
                this.model = undefined;
                this.jhiAlertService.error(`Retrieving requested submission failed.`);
            },
        );
    }

    onSaveAssessment() {
        this.removeCircularDependencies();
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

    onSubmitAssessment() {
        this.removeCircularDependencies();
        this.modelingAssessmentService.save(this.result.feedbacks, this.submission.id, true).subscribe(
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
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailedWithConflict');
                } else {
                    this.jhiAlertService.error('arTeMiSApp.apollonDiagram.assessment.submitFailed');
                }
            },
        );
    }

    onFeedbackChanged(feedback: Feedback[]) {
        this.result.feedbacks = feedback;
        this.validateFeedback();
    }

    assessNextOptimal() {
        this.busy = true;
        this.modelingAssessmentService
            .getOptimalSubmissions(this.modelingExercise.id)
            .pipe(retryWhen(genericRetryStrategy({ maxRetryAttempts: 5, scalingDuration: 1000 })))
            .subscribe(
                (optimal: number[]) => {
                    this.busy = false;
                    this.router.navigateByUrl(`modeling-exercise/${this.modelingExercise.id}/submissions/${optimal.pop()}/assessment`);
                },
                () => {
                    this.busy = false;
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                },
            );
    }

    /**
     * Removes the circular dependencies in the nested objects.
     * Otherwise, we would get a JSON error when trying to send the submission to the server.
     */
    private removeCircularDependencies() {
        this.submission.result.participation = null;
        this.submission.result.submission = null;
    }

    private validateFeedback() {
        if (!this.result.feedbacks) {
            this.assessmentsAreValid = false;
            return;
        }
        for (const feedback of this.result.feedbacks) {
            if (feedback.credits == null) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }
}
