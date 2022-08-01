import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UMLModel } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/complaints/complaint.service';
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
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { getSubmissionResultByCorrectionRound, getSubmissionResultById } from 'app/entities/submission.model';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';

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
    course?: Course;
    result?: Result;
    referencedFeedback: Feedback[] = [];
    unreferencedFeedback: Feedback[] = [];
    highlightedElements: Map<string, string>; // map elementId -> highlight color
    highlightMissingFeedback = false;

    assessmentsAreValid = false;
    nextSubmissionBusy: boolean;
    courseId: number;
    examId = 0;
    exerciseId: number;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    userId: number;
    isAssessor = false;
    hideBackButton: boolean;
    complaint: Complaint;
    ComplaintType = ComplaintType;
    isLoading = true;
    isTestRun = false;
    hasAutomaticFeedback = false;
    hasAssessmentDueDatePassed: boolean;
    correctionRound = 0;
    resultId: number;
    loadingInitialSubmission = true;
    highlightDifferences = false;
    resizeOptions = { verticalResize: true };
    isApollonModelLoaded = false;

    private cancelConfirmationText: string;

    constructor(
        private alertService: AlertService,
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
        private submissionService: SubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
    ) {
        translateService.get('modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    private get feedback(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });

        this.route.queryParamMap.subscribe((queryParams) => {
            this.hideBackButton = queryParams.get('hideBackButton') === 'true';
            this.isTestRun = queryParams.get('testRun') === 'true';
            this.correctionRound = Number(queryParams.get('correction-round'));
        });
        this.route.paramMap.subscribe((params) => {
            this.courseId = Number(params.get('courseId'));
            this.exerciseId = Number(params.get('exerciseId'));
            if (params.has('examId')) {
                this.examId = Number(params.get('examId'));
                this.exerciseGroupId = Number(params.get('exerciseGroupId'));
            }

            this.exerciseDashboardLink = getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun);

            const submissionId = params.get('submissionId');
            this.resultId = Number(params.get('resultId')) ?? 0;
            if (submissionId === 'new') {
                this.loadRandomSubmission(this.exerciseId);
            } else {
                this.loadSubmission(Number(submissionId));
            }
        });
    }

    private loadSubmission(submissionId: number): void {
        this.modelingSubmissionService.getSubmission(submissionId, this.correctionRound, this.resultId).subscribe({
            next: (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private loadRandomSubmission(exerciseId: number): void {
        this.modelingSubmissionService.getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId, true, this.correctionRound).subscribe({
            next: (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private handleReceivedSubmission(submission: ModelingSubmission): void {
        this.loadingInitialSubmission = false;
        this.submission = submission;
        const studentParticipation = this.submission.participation as StudentParticipation;
        this.modelingExercise = studentParticipation.exercise as ModelingExercise;
        this.course = getCourseFromExercise(this.modelingExercise);
        if (this.resultId > 0) {
            this.result = getSubmissionResultById(submission, this.resultId);
            this.correctionRound = submission.results?.findIndex((result) => result.id === this.resultId)!;
        } else {
            this.result = getSubmissionResultByCorrectionRound(this.submission, this.correctionRound);
        }
        this.hasAssessmentDueDatePassed = !!this.modelingExercise!.assessmentDueDate && dayjs(this.modelingExercise!.assessmentDueDate).isBefore(dayjs());

        this.getComplaint();

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
            this.alertService.closeAll();
            this.alertService.warning('modelingAssessmentEditor.messages.noModel');
        }
        if ((!this.result?.assessor || this.result.assessor.id === this.userId) && !this.result?.completionDate) {
            this.alertService.closeAll();
            this.alertService.info('modelingAssessmentEditor.messages.lock');
        }
        this.checkPermissions();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);

        this.isLoading = false;
    }

    private getComplaint(): void {
        if (!this.submission) {
            return;
        }
        this.complaintService.findBySubmissionId(this.submission.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            error: () => {
                this.onError();
            },
        });
    }

    /**
     * Checks the given feedback list for unreferenced feedback. The remaining list is then assigned to the
     * referencedFeedback variable containing only feedback elements with a reference and valid score.
     * Additionally, it checks if the feedback list contains any automatic feedback elements and sets the hasAutomaticFeedback flag accordingly.
     * Afterwards, it triggers the highlighting of feedback elements, if necessary.
     */
    private handleFeedback(feedback?: Feedback[]): void {
        if (!feedback || feedback.length === 0) {
            return;
        }

        this.referencedFeedback = feedback.filter((feedbackElement) => feedbackElement.reference);
        this.unreferencedFeedback = feedback.filter((feedbackElement) => feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);

        this.hasAutomaticFeedback = feedback.some((feedbackItem) => feedbackItem.type === FeedbackType.AUTOMATIC);
        this.highlightAutomaticFeedback();

        if (this.highlightMissingFeedback) {
            this.highlightElementsWithMissingFeedback();
        }
        this.calculateTotalScore();
    }

    private checkPermissions(): void {
        this.isAssessor = this.result?.assessor?.id === this.userId;
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
            if (this.modelingExercise.isAtLeastInstructor) {
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
                isBeforeAssessmentDueDate = dayjs().isBefore(this.modelingExercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(
            this.modelingExercise?.isAtLeastInstructor ?? false,
            this.isTestRun,
            this.isAssessor,
            this.hasAssessmentDueDatePassed,
            this.result,
            this.complaint,
            this.modelingExercise,
        );
    }

    private handleErrorResponse(error: HttpErrorResponse): void {
        this.loadingInitialSubmission = false;
        this.submission = undefined;

        // there is no submission waiting for assessment at the moment
        if (error.status === 404) {
            return;
        }

        this.isLoading = false;
        if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
            this.navigateBack();
        } else {
            this.onError();
        }
    }

    onError(): void {
        this.submission = undefined;
        this.modelingExercise = undefined;
        this.result = undefined;
        this.model = undefined;
        this.alertService.closeAll();
        this.alertService.error('modelingAssessmentEditor.messages.loadSubmissionFailed');
    }

    onSaveAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.alertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.result!.id!, this.feedback, this.submission!.id!).subscribe({
            next: (result: Result) => {
                this.result = result;
                this.handleFeedback(this.result.feedbacks);
                this.alertService.closeAll();
                this.alertService.success('modelingAssessmentEditor.messages.saveSuccessful');
            },
            error: () => {
                this.alertService.closeAll();
                this.alertService.error('modelingAssessmentEditor.messages.saveFailed');
            },
        });
    }

    onSubmitAssessment() {
        if ((this.model && this.referencedFeedback.length < this.model.elements.length) || !this.assessmentsAreValid) {
            const confirmationMessage = this.translateService.instant('modelingAssessmentEditor.messages.confirmSubmission');

            // if the assessment is before the assessment due date, don't show the confirm submission button
            const isBeforeAssessmentDueDate = this.modelingExercise && this.modelingExercise.assessmentDueDate && dayjs().isBefore(this.modelingExercise.assessmentDueDate);
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
            this.alertService.error('modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }
        this.modelingAssessmentService.saveAssessment(this.result!.id!, this.feedback, this.submission!.id!, true).subscribe({
            next: (result: Result) => {
                result.participation!.results = [result];
                this.result = result;

                this.alertService.closeAll();
                this.alertService.success('modelingAssessmentEditor.messages.submitSuccessful');

                this.highlightMissingFeedback = false;
            },
            error: (error: HttpErrorResponse) => {
                let errorMessage = 'modelingAssessmentEditor.messages.submitFailed';
                if (error.error && error.error.entityName && error.error.message) {
                    errorMessage = `artemisApp.${error.error.entityName}.${error.error.message}`;
                }
                this.alertService.closeAll();
                this.alertService.error(errorMessage);
            },
        });
        this.assessmentsAreValid = false;
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.modelingAssessmentService.updateAssessmentAfterComplaint(this.feedback, complaintResponse, this.submission!.id!).subscribe({
            next: (response) => {
                this.result = response.body!;
                // reconnect
                this.result.participation!.results = [this.result];
                this.alertService.closeAll();
                this.alertService.success('modelingAssessmentEditor.messages.updateAfterComplaintSuccessful');
            },
            error: (httpErrorResponse: HttpErrorResponse) => {
                this.alertService.closeAll();
                const error = httpErrorResponse.error;
                if (error && error.errorKey && error.errorKey === 'complaintLock') {
                    this.alertService.error(error.message, error.params);
                } else {
                    this.alertService.error('modelingAssessmentEditor.messages.updateAfterComplaintFailed');
                }
            },
        });
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

        if (!this.isApollonModelLoaded) {
            this.isApollonModelLoaded = true;
            this.calculateTotalScore();
            this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
            return;
        }

        this.validateFeedback();
    }

    assessNext() {
        this.isLoading = true;
        this.nextSubmissionBusy = true;
        this.modelingSubmissionService.getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.modelingExercise!.id!, true, this.correctionRound).subscribe({
            next: (unassessedSubmission: ModelingSubmission) => {
                this.nextSubmissionBusy = false;
                this.isLoading = false;

                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.onSameUrlNavigation = 'reload';

                // navigate to root and then to new assessment page to trigger re-initialization of the components
                const url = getLinkToSubmissionAssessment(
                    ExerciseType.MODELING,
                    this.courseId,
                    this.exerciseId,
                    undefined,
                    unassessedSubmission.id!,
                    this.examId,
                    this.exerciseGroupId,
                );
                this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } }));
            },
            error: (error: HttpErrorResponse) => {
                this.nextSubmissionBusy = false;
                this.handleErrorResponse(error);
            },
        });
    }

    /**
     * Validates the feedback:
     *   - There must be any form of feedback, either unreferencing feedback or feedback referencing a model element or both
     *   - Each reference feedback must have a score that is a valid number
     */
    validateFeedback() {
        this.calculateTotalScore();
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = (hasReferencedFeedback && this.unreferencedFeedback.length === 0) || hasUnreferencedFeedback;
        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
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
        const maxPoints = this.modelingExercise!.maxPoints! + this.modelingExercise!.bonusPoints! ?? 0.0;
        if (this.totalScore > maxPoints) {
            this.totalScore = maxPoints;
        }
        // Do not allow negative score
        if (this.totalScore < 0) {
            this.totalScore = 0;
        }
    }

    /**
     * Invokes exampleSubmissionService when useAsExampleSubmission is emitted in assessment-layout
     */
    useStudentSubmissionAsExampleSubmission(): void {
        if (this.submission && this.modelingExercise) {
            this.exampleSubmissionService.import(this.submission.id!, this.modelingExercise.id!).subscribe({
                next: () => this.alertService.success('artemisApp.exampleSubmission.submitSuccessful'),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }
}
