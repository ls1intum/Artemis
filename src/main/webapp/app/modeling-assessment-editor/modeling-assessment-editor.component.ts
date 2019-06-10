import { Component, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DiagramType, UMLModel, UMLElement } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { ModelingSubmission, ModelingSubmissionService } from '../entities/modeling-submission';
import { ModelingExercise, ModelingExerciseService } from '../entities/modeling-exercise';
import { Result, ResultService } from '../entities/result';
import { AccountService } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ModelingAssessmentService } from 'app/modeling-assessment-editor/modeling-assessment.service';
import { Feedback } from 'app/entities/feedback';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';

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
    generalFeedback: Feedback;
    referencedFeedback: Feedback[];
    conflicts: Conflict[];
    highlightedElementIds: string[];
    ignoreConflicts = false;

    assessmentsAreValid = false;
    busy: boolean;
    userId: number;
    isAuthorized = false;
    isAtLeastInstructor = false;
    showBackButton: boolean;
    hasComplaint: boolean;
    canOverride = false;
    isLoading: boolean;

    private cancelConfirmationText: string;

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
        private location: Location,
        private translateService: TranslateService,
    ) {
        translateService.get('modelingAssessmentEditor.messages.confirmCancel').subscribe(text => (this.cancelConfirmationText = text));
        this.generalFeedback = new Feedback();
        this.referencedFeedback = [];
        this.isLoading = true;
    }

    get feedback(): Feedback[] {
        if (!this.referencedFeedback) {
            return [this.generalFeedback];
        }
        return [this.generalFeedback, ...this.referencedFeedback];
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then(user => {
            this.userId = user.id;
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);

        this.route.params.subscribe(params => {
            const submissionId: String = params['submissionId'];
            const exerciseId = Number(params['exerciseId']);
            if (submissionId === 'new') {
                this.loadOptimalSubmission(exerciseId);
            } else {
                this.loadSubmission(Number(submissionId));
            }
        });
        this.route.queryParams.subscribe(params => {
            this.showBackButton = params['showBackButton'] === 'true';
        });
    }

    ngOnDestroy() {}

    private loadSubmission(submissionId: number): void {
        this.modelingSubmissionService.getSubmission(submissionId).subscribe(
            (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);
            },
            error => {
                this.onError();
            },
        );
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.modelingSubmissionService.getModelingSubmissionForExerciseWithoutAssessment(exerciseId, true).subscribe(
            (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission.id}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there is no submission waiting for assessment at the moment
                    this.goToExerciseDashboard();
                    this.jhiAlertService.info('arTeMiSApp.tutorExerciseDashboard.noSubmissions');
                } else {
                    this.onError();
                }
            },
        );
    }

    private handleReceivedSubmission(submission: ModelingSubmission): void {
        this.submission = submission;
        this.modelingExercise = this.submission.participation.exercise as ModelingExercise;
        this.result = this.submission.result;
        this.hasComplaint = this.result.hasComplaint;
        if (this.result.feedbacks) {
            this.result = this.modelingAssessmentService.convertResult(this.result);
            this.handleFeedback(this.result.feedbacks);
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
            this.jhiAlertService.clear();
            this.jhiAlertService.error('modelingAssessmentEditor.messages.noModel');
        }
        if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.completionDate) {
            this.jhiAlertService.clear();
            this.jhiAlertService.info('modelingAssessmentEditor.messages.lock');
        }
        this.checkPermissions();
        this.validateFeedback();
        this.isLoading = false;
    }

    /**
     * Checks the given feedback list for general feedback (i.e. feedback without a reference). If there is one, it is assigned to the generalFeedback variable and removed from
     * the original feedback list. The remaining list is then assigned to the referencedFeedback variable containing only feedback elements with a reference and valid score.
     */
    private handleFeedback(feedback: Feedback[]): void {
        if (!feedback || feedback.length === 0) {
            return;
        }
        const generalFeedbackIndex = feedback.findIndex(feedbackElement => feedbackElement.reference == null);
        if (generalFeedbackIndex >= 0) {
            this.generalFeedback = feedback[generalFeedbackIndex] || new Feedback();
            feedback.splice(generalFeedbackIndex, 1);
        }
        this.referencedFeedback = feedback;
    }

    private checkPermissions(): void {
        this.isAuthorized = this.result && this.result.assessor && this.result.assessor.id === this.userId;
        const isBeforeAssessmentDueDate = this.modelingExercise && this.modelingExercise.assessmentDueDate && moment().isBefore(this.modelingExercise.assessmentDueDate);
        // tutors are allowed to override one of their assessments before the assessment due date, instructors can override any assessment at any time
        this.canOverride = (this.isAuthorized && isBeforeAssessmentDueDate) || this.isAtLeastInstructor;
    }

    onError(): void {
        this.submission = undefined;
        this.modelingExercise = undefined;
        this.result = undefined;
        this.model = undefined;
        this.jhiAlertService.clear();
        this.jhiAlertService.error('modelingAssessmentEditor.messages.loadSubmissionFailed');
    }

    onSaveAssessment() {
        this.modelingAssessmentService.saveAssessment(this.feedback, this.submission.id).subscribe(
            (result: Result) => {
                this.result = result;
                this.handleFeedback(this.result.feedbacks);
                this.jhiAlertService.clear();
                this.jhiAlertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            () => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('modelingAssessmentEditor.messages.saveFailed');
            },
        );
    }

    onSubmitAssessment() {
        // TODO: we should warn the tutor if not all model elements have been assessed, and ask him to confirm that he really wants to submit the assessment
        // in case he says no, we should potentially highlight the elements that are not yet assessed
        if (this.referencedFeedback.length < this.model.elements.length || !this.assessmentsAreValid) {
            const confirmationMessage = this.translateService.instant('modelingAssessmentEditor.messages.confirmSubmission');
            const confirm = window.confirm(confirmationMessage);
            if (confirm) {
                this.submitAssessment();
            } else {
                this.highlightedElementIds = [];
                this.model.elements.forEach((element: UMLElement) => {
                    if (this.referencedFeedback.findIndex(feedback => feedback.referenceId === element.id) < 0) {
                        this.highlightedElementIds.push(element.id);
                    }
                });
            }
        } else {
            this.submitAssessment();
        }
    }

    private submitAssessment() {
        this.modelingAssessmentService.saveAssessment(this.feedback, this.submission.id, true, this.ignoreConflicts).subscribe(
            (result: Result) => {
                result.participation.results = [result];
                this.result = result;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');
                this.conflicts = undefined;
                this.ignoreConflicts = false;
            },
            (error: HttpErrorResponse) => {
                if (error.status === 409) {
                    this.conflicts = error.error as Conflict[];
                    this.conflicts.forEach((conflict: Conflict) => {
                        this.modelingAssessmentService.convertResult(conflict.result);
                        conflict.conflictingResults.forEach((conflictingResult: ConflictingResult) => this.modelingAssessmentService.convertResult(conflictingResult.result));
                    });
                    this.highlightConflictingElements();
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.submitFailedWithConflict');
                } else {
                    let errorMessage = 'modelingAssessmentEditor.messages.submitFailed';
                    if (error.error && error.error.entityName && error.error.message) {
                        errorMessage = `arTeMiSApp.${error.error.entityName}.${error.error.message}`;
                    }
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error(errorMessage);
                }
            },
        );
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.modelingAssessmentService.updateAssessmentAfterComplaint(this.feedback, complaintResponse, this.submission.id).subscribe(
            (result: Result) => {
                this.result = result;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('modelingAssessmentEditor.messages.updateAfterComplaintSuccessful');
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('modelingAssessmentEditor.messages.updateAfterComplaintFailed');
            },
        );
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(this.submission.id).subscribe(() => {
                this.goToExerciseDashboard();
            });
        }
    }

    onFeedbackChanged(feedback: Feedback[]) {
        this.referencedFeedback = feedback;
        this.validateFeedback();
    }

    assessNextOptimal() {
        this.busy = true;
        this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise.id).subscribe(
            (optimal: number[]) => {
                this.busy = false;
                if (optimal.length === 0) {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                } else {
                    this.jhiAlertService.clear();
                    this.router.onSameUrlNavigation = 'reload';
                    // navigate to root and then to new assessment page to trigger re-initialization of the components
                    this.router
                        .navigateByUrl('/', { skipLocationChange: true })
                        .then(() => this.router.navigateByUrl(`modeling-exercise/${this.modelingExercise.id}/submissions/${optimal.pop()}/assessment?showBackButton=true`));
                }
            },
            () => {
                this.busy = false;
                this.jhiAlertService.clear();
                this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
            },
        );
    }

    private highlightConflictingElements() {
        this.highlightedElementIds = [];
        this.conflicts.forEach((conflict: Conflict) => {
            this.highlightedElementIds.push(conflict.modelElementId);
        });
    }

    /**
     * Validates the feedback:
     *   - There must be any form of feedback, either general feedback or feedback referencing a model element or both
     *   - Each reference feedback must have a score that is a valid number
     */
    validateFeedback() {
        if (
            (!this.referencedFeedback || this.referencedFeedback.length === 0) &&
            (!this.generalFeedback || !this.generalFeedback.detailText || this.generalFeedback.detailText.length === 0)
        ) {
            this.assessmentsAreValid = false;
            return;
        }
        if (this.highlightedElementIds && this.highlightedElementIds.length > 0) {
            this.highlightedElementIds = this.highlightedElementIds.filter(element => element !== this.referencedFeedback[this.referencedFeedback.length - 1].referenceId);
        }
        for (const feedback of this.referencedFeedback) {
            if (feedback.credits == null || isNaN(feedback.credits)) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }

    goToExerciseDashboard() {
        if (this.modelingExercise && this.modelingExercise.course) {
            this.router.navigateByUrl(`/course/${this.modelingExercise.course.id}/exercise/${this.modelingExercise.id}/tutor-dashboard`);
        } else {
            this.location.back();
        }
    }
}
