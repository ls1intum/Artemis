import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UMLModel } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { now } from 'moment';
import { ComplaintService } from 'app/complaints/complaint.service';
import { filter } from 'rxjs/operators';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { Feedback, FeedbackHighlightColor, FeedbackType } from 'app/entities/feedback.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { Authority } from 'app/shared/constants/authority.constants';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { getLatestSubmissionResult } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-modeling-assessment-editor',
    templateUrl: './modeling-assessment-editor.component.html',
    styleUrls: ['./modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentEditorComponent implements OnInit {
    totalScore = 0;
    submission?: ModelingSubmission;
    model?: UMLModel;
    modelingExercise?: ModelingExercise;
    result?: Result;
    generalFeedback = new Feedback();
    referencedFeedback: Feedback[] = [];
    unreferencedFeedback: Feedback[] = [];
    highlightedElements: Map<string, string>; // map elementId -> highlight color
    highlightMissingFeedback = false;

    assessmentsAreValid = false;
    nextSubmissionBusy: boolean;
    courseId: number;
    userId: number;
    isAssessor = false;
    isAtLeastInstructor = false;
    hideBackButton: boolean;
    complaint: Complaint;
    ComplaintType = ComplaintType;
    isLoading = true;
    isTestRun = false;
    hasAutomaticFeedback = false;
    hasAssessmentDueDatePassed: boolean;

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
        private complaintService: ComplaintService,
        private structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {
        translateService.get('modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    private get feedback(): Feedback[] {
        if (Feedback.hasDetailText(this.generalFeedback)) {
            return [this.generalFeedback, ...this.referencedFeedback, ...this.unreferencedFeedback];
        } else {
            return [...this.referencedFeedback, ...this.unreferencedFeedback];
        }
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);

        this.route.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
            const exerciseId = Number(params.get('exerciseId'));
            const submissionId = params.get('submissionId');
            if (submissionId === 'new') {
                this.loadOptimalSubmission(exerciseId);
            } else {
                this.loadSubmission(Number(submissionId));
            }
        });
        this.route.queryParamMap.subscribe((queryParams) => {
            this.hideBackButton = queryParams.get('hideBackButton') === 'true';
            this.isTestRun = queryParams.get('testRun') === 'true';
        });
    }

    private loadSubmission(submissionId: number): void {
        this.modelingSubmissionService.getSubmission(submissionId).subscribe(
            (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);
            },
            (error: HttpErrorResponse) => {
                if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.navigateBack();
                } else {
                    this.onError();
                }
            },
        );
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.modelingSubmissionService.getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId, true).subscribe(
            (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there is no submission waiting for assessment at the moment
                    this.navigateBack();
                    this.jhiAlertService.info('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.navigateBack();
                } else {
                    this.onError();
                }
            },
        );
    }

    private handleReceivedSubmission(submission: ModelingSubmission): void {
        this.submission = submission;
        const studentParticipation = this.submission.participation as StudentParticipation;
        this.modelingExercise = studentParticipation.exercise as ModelingExercise;
        this.result = getLatestSubmissionResult(this.submission);
        this.hasAssessmentDueDatePassed = !!this.modelingExercise!.assessmentDueDate && moment(this.modelingExercise!.assessmentDueDate).isBefore(now());
        if (this.result?.hasComplaint) {
            this.getComplaint(this.result.id);
        }
        if (this.result?.feedbacks) {
            this.result = this.modelingAssessmentService.convertResult(this.result);
            this.handleFeedback(this.result.feedbacks);
        } else {
            this.result!.feedbacks = [];
        }
        if (this.result && this.submission?.participation) {
            this.submission.participation.results = [this.result];
            this.result.participation = this.submission.participation;
        }
        if (!this.modelingExercise.diagramType) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }
        if (this.submission.model) {
            this.model = JSON.parse(this.submission.model);
        } else {
            this.jhiAlertService.clear();
            this.jhiAlertService.error('modelingAssessmentEditor.messages.noModel');
        }
        if ((!this.result?.assessor || this.result.assessor.id === this.userId) && !this.result?.completionDate) {
            this.jhiAlertService.clear();
            this.jhiAlertService.info('modelingAssessmentEditor.messages.lock');
        }
        this.checkPermissions();
        this.validateFeedback();
        this.isLoading = false;
    }

    private getComplaint(resultId?: number): void {
        if (this.result && resultId) {
            this.complaintService
                .findByResultId(resultId)
                .pipe(filter((res) => !!res.body))
                .subscribe(
                    (res) => {
                        this.complaint = res.body!;
                    },
                    () => {
                        this.onError();
                    },
                );
        }
    }

    /**
     * Checks the given feedback list for general feedback (i.e. feedback without a reference). If there is one, it is assigned to the generalFeedback variable and removed from
     * the original feedback list. The remaining list is then assigned to the referencedFeedback variable containing only feedback elements with a reference and valid score.
     * Additionally, it checks if the feedback list contains any automatic feedback elements and sets the hasAutomaticFeedback flag accordingly. Afterwards, it triggers the
     * highlighting of feedback elements, if necessary.
     */
    private handleFeedback(feedback?: Feedback[]): void {
        if (!feedback || feedback.length === 0) {
            return;
        }

        const generalFeedbackIndex = feedback.findIndex((feedbackElement) => !feedbackElement.reference && feedbackElement.type !== FeedbackType.MANUAL_UNREFERENCED);
        if (generalFeedbackIndex >= 0) {
            this.generalFeedback = feedback[generalFeedbackIndex] || new Feedback();
            feedback.splice(generalFeedbackIndex, 1);
        }

        this.referencedFeedback = feedback.filter((feedbackElement) => feedbackElement.reference);
        this.unreferencedFeedback = feedback.filter((feedbackElement) => feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);

        this.hasAutomaticFeedback = feedback.some((feedbackItem) => feedbackItem.type === FeedbackType.AUTOMATIC);
        this.highlightAutomaticFeedback();

        if (this.highlightMissingFeedback) {
            this.highlightElementsWithMissingFeedback();
        }
    }

    private checkPermissions(): void {
        this.isAssessor = this.result?.assessor?.id === this.userId;
        if (this.modelingExercise) {
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.modelingExercise.course || this.modelingExercise.exerciseGroup!.exam!.course);
        }
    }

    /**
     * Boolean which determines whether the user can override a result.
     * If no exercise is loaded, for example during loading between exercises, we return false.
     * Instructors can always override a result.
     * Tutors can override their own results within the assessment due date, if there is no complaint about their assessment.
     * They cannot override a result anymore, if there is a complaint. Another tutor must handle the complaint.
     */
    get canOverride(): boolean {
        if (this.modelingExercise) {
            if (this.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            if (this.complaint && this.isAssessor) {
                // If there is a complaint, the original assessor cannot override the result anymore.
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.modelingExercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = moment().isBefore(this.modelingExercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !this.isAtLeastInstructor && !!this.complaint && this.isAssessor;
    }

    onError(): void {
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);
        this.submission = undefined;
        this.modelingExercise = undefined;
        this.result = undefined;
        this.model = undefined;
        this.jhiAlertService.clear();
        this.jhiAlertService.error('modelingAssessmentEditor.messages.loadSubmissionFailed');
    }

    onSaveAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.jhiAlertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.feedback, this.submission!.id!).subscribe(
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
        if ((this.model && this.referencedFeedback.length < this.model.elements.length) || !this.assessmentsAreValid) {
            const confirmationMessage = this.translateService.instant('modelingAssessmentEditor.messages.confirmSubmission');

            // if the assessment is before the assessment due date, don't show the confirm submission button
            const isBeforeAssessmentDueDate = this.modelingExercise && this.modelingExercise.assessmentDueDate && moment().isBefore(this.modelingExercise.assessmentDueDate);
            if (isBeforeAssessmentDueDate) {
                this.submitAssessment();
            } else {
                const confirm = window.confirm(confirmationMessage);
                if (confirm) {
                    this.submitAssessment();
                } else {
                    this.highlightMissingFeedback = true;
                    this.highlightElementsWithMissingFeedback();
                }
            }
        } else {
            this.submitAssessment();
        }
    }

    private submitAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.jhiAlertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.feedback, this.submission!.id!, true).subscribe(
            (result: Result) => {
                result.participation!.results = [result];
                this.result = result;

                this.jhiAlertService.clear();
                this.jhiAlertService.success('modelingAssessmentEditor.messages.submitSuccessful');

                this.highlightMissingFeedback = false;
            },
            (error: HttpErrorResponse) => {
                let errorMessage = 'modelingAssessmentEditor.messages.submitFailed';
                if (error.error && error.error.entityName && error.error.message) {
                    errorMessage = `artemisApp.${error.error.entityName}.${error.error.message}`;
                }
                this.jhiAlertService.clear();
                this.jhiAlertService.error(errorMessage);
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
        this.modelingAssessmentService.updateAssessmentAfterComplaint(this.feedback, complaintResponse, this.submission!.id!).subscribe(
            (response) => {
                this.result = response.body!;
                // reconnect
                this.result.participation!.results = [this.result];
                this.jhiAlertService.clear();
                this.jhiAlertService.success('modelingAssessmentEditor.messages.updateAfterComplaintSuccessful');
            },
            (httpErrorResponse: HttpErrorResponse) => {
                this.jhiAlertService.clear();
                const error = httpErrorResponse.error;
                if (error && error.errorKey && error.errorKey === 'complaintLock') {
                    this.jhiAlertService.error(error.message, error.params);
                } else {
                    this.jhiAlertService.error('modelingAssessmentEditor.messages.updateAfterComplaintFailed');
                }
            },
        );
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(this.submission!.id!).subscribe(() => {
                this.navigateBack();
            });
        }
    }

    onFeedbackChanged(feedback: Feedback[]) {
        this.referencedFeedback = feedback.filter((feedbackElement) => feedbackElement.reference);
        this.validateFeedback();
    }

    assessNextOptimal() {
        this.nextSubmissionBusy = true;
        this.modelingAssessmentService.getOptimalSubmissions(this.modelingExercise!.id!).subscribe(
            (optimal: number[]) => {
                this.nextSubmissionBusy = false;
                if (optimal.length === 0) {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                } else {
                    this.jhiAlertService.clear();
                    this.router.onSameUrlNavigation = 'reload';
                    // navigate to root and then to new assessment page to trigger re-initialization of the components
                    this.router
                        .navigateByUrl('/', { skipLocationChange: true })
                        .then(() =>
                            this.router.navigateByUrl(
                                `/course-management/${this.courseId}/modeling-exercises/${this.modelingExercise!.id}/submissions/${optimal.pop()}/assessment`,
                            ),
                        );
                }
            },
            (error: HttpErrorResponse) => {
                this.nextSubmissionBusy = false;
                if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.navigateBack();
                } else {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.info('assessmentDashboard.noSubmissionFound');
                }
            },
        );
    }

    /**
     * Validates the feedback:
     *   - There must be any form of feedback, either general feedback or feedback referencing a model element or both
     *   - Each reference feedback must have a score that is a valid number
     */
    validateFeedback() {
        this.calculateTotalScore();
        if (
            (!this.referencedFeedback || this.referencedFeedback.length === 0) &&
            (!this.unreferencedFeedback || this.unreferencedFeedback.length === 0) &&
            (!this.generalFeedback || !this.generalFeedback.detailText || this.generalFeedback.detailText.length === 0)
        ) {
            this.assessmentsAreValid = false;
            return;
        }
        for (const feedback of this.referencedFeedback) {
            if (feedback.credits == undefined || isNaN(feedback.credits)) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.modelingExercise, this.submission, this.isTestRun);
    }

    /**
     * Add all elements for which no corresponding feedback element exist to the map of highlighted elements. To make sure that we do not have outdated elements in the map, all
     * elements with the corresponding "missing feedback color" get removed first.
     */
    private highlightElementsWithMissingFeedback() {
        if (!this.model) {
            return;
        }

        this.highlightedElements = this.highlightedElements
            ? this.removeHighlightedFeedbackOfColor(this.highlightedElements, FeedbackHighlightColor.RED)
            : new Map<string, string>();

        const referenceIds = this.referencedFeedback.map((feedback) => feedback.referenceId);
        for (const element of this.model.elements) {
            if (!referenceIds.includes(element.id)) {
                this.highlightedElements.set(element.id, FeedbackHighlightColor.RED);
            }
        }
    }

    /**
     * Add all automatic feedback elements to the map of highlighted elements. To make sure that we do not have outdated elements in the map, all elements with the corresponding
     * "automatic feedback color" get removed first. The automatic feedback will not be highlighted anymore after the assessment has been completed.
     */
    private highlightAutomaticFeedback() {
        if (this.result && this.result.completionDate) {
            return;
        }

        this.highlightedElements = this.highlightedElements
            ? this.removeHighlightedFeedbackOfColor(this.highlightedElements, FeedbackHighlightColor.CYAN)
            : new Map<string, string>();

        for (const feedbackItem of this.referencedFeedback) {
            if (feedbackItem.type === FeedbackType.AUTOMATIC && feedbackItem.referenceId) {
                this.highlightedElements.set(feedbackItem.referenceId, FeedbackHighlightColor.CYAN);
            }
        }
    }

    /**
     * Remove all elements with the given highlight color from the map of highlighted feedback elements.
     *
     * @param highlightedElements the map of highlighted feedback elements
     * @param color the color of the elements that should be removed
     */
    private removeHighlightedFeedbackOfColor(highlightedElements: Map<string, string>, color: string) {
        return new Map<string, string>([...highlightedElements].filter(([, value]) => value !== color));
    }
    /**
     * Calculates the total score of the current assessment.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    calculateTotalScore() {
        this.totalScore = this.structuredGradingCriterionService.computeTotalScore(this.feedback);
        // Cap totalScore to maxPoints
        const maxPoints = this.modelingExercise!.maxScore! + this.modelingExercise!.bonusPoints! ?? 0.0;
        if (this.totalScore > maxPoints) {
            this.totalScore = maxPoints;
        }
        // Do not allow negative score
        if (this.totalScore < 0) {
            this.totalScore = 0;
        }
    }
}
