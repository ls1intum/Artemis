import { Component, OnInit, inject } from '@angular/core';
import { Location } from '@angular/common';
import { firstValueFrom } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercises/shared/exercise/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { Feedback, FeedbackHighlightColor, FeedbackType } from 'app/entities/feedback.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { Submission, getSubmissionResultByCorrectionRound, getSubmissionResultById } from 'app/entities/submission.model';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';
import { AssessmentAfterComplaint } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { AthenaService } from 'app/assessment/athena.service';
import { faCircleNotch, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-modeling-assessment-editor',
    templateUrl: './modeling-assessment-editor.component.html',
    styleUrls: ['./modeling-assessment-editor.component.scss'],
})
export class ModelingAssessmentEditorComponent implements OnInit {
    private alertService = inject(AlertService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private accountService = inject(AccountService);
    private location = inject(Location);
    private translateService = inject(TranslateService);
    private complaintService = inject(ComplaintService);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private submissionService = inject(SubmissionService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private athenaService = inject(AthenaService);

    totalScore = 0;
    submission?: ModelingSubmission;
    model?: UMLModel;
    modelingExercise?: ModelingExercise;
    course?: Course;
    result?: Result;
    referencedFeedback: Feedback[] = [];
    unreferencedFeedback: Feedback[] = [];
    automaticFeedback: Feedback[] = [];
    feedbackSuggestions: Feedback[] = []; // all pending Athena feedback suggestions (neither accepted nor rejected yet)
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

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faQuestionCircle = faQuestionCircle;

    constructor() {
        const translateService = this.translateService;

        translateService.get('artemisApp.modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * Retrieve all feedback for the current exercise regardless of whether it is referenced or unreferenced
     */
    private get feedback(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    /**
     * Retrieve unreferenced entries from the feedback suggestions loaded from Athena.
     * The suggestions are displayed in cards underneath the modeling editor canvas.
     */
    get unreferencedFeedbackSuggestions(): Feedback[] {
        return this.feedbackSuggestions.filter((feedback) => !feedback.reference);
    }

    /**
     * Retrieve whether feedback suggestions are enabled based on whether a feedback suggestions module is set on the
     * current modeling exercise.
     */
    get isFeedbackSuggestionsEnabled(): boolean {
        return Boolean(this.modelingExercise?.feedbackSuggestionModule);
    }

    ngOnInit() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });

        this.route.queryParamMap.subscribe((queryParams) => {
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

    /**
     * Load the feedback suggestions for the current submission from Athena.
     * @param exercise The current exercise
     * @param submission The current submission
     */
    private async loadFeedbackSuggestions(exercise: ModelingExercise, submission: Submission): Promise<Feedback[]> {
        try {
            return (await firstValueFrom(this.athenaService.getModelingFeedbackSuggestions(exercise, submission))) ?? [];
        } catch (error) {
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.loadFeedbackSuggestionsFailed');
            return [];
        }
    }

    /**
     * Load the modeling submission for a given ID
     * @param submissionId The ID of the modeling submission that should be loaded
     */
    private loadSubmission(submissionId: number): void {
        this.modelingSubmissionService.getSubmission(submissionId, this.correctionRound, this.resultId).subscribe({
            next: (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);
                this.validateFeedback();
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private loadRandomSubmission(exerciseId: number): void {
        this.modelingSubmissionService.getSubmissionWithoutAssessment(exerciseId, true, this.correctionRound).subscribe({
            next: async (submission?: ModelingSubmission) => {
                if (!submission) {
                    // there are no unassessed submissions
                    this.submission = undefined;
                    return;
                }

                await this.handleReceivedSubmission(submission);
                this.validateFeedback();

                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private async handleReceivedSubmission(submission: ModelingSubmission): Promise<void> {
        this.loadingInitialSubmission = false;
        this.submission = submission;
        const studentParticipation = this.submission.participation as StudentParticipation;
        this.modelingExercise = studentParticipation.exercise as ModelingExercise;
        this.course = getCourseFromExercise(this.modelingExercise);
        if (this.resultId > 0) {
            this.result = getSubmissionResultById(submission, this.resultId);
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            this.correctionRound = submission.results?.findIndex((result) => result.id === this.resultId)!;
        } else {
            this.result = getSubmissionResultByCorrectionRound(this.submission, this.correctionRound);
        }
        this.hasAssessmentDueDatePassed = !!this.modelingExercise?.assessmentDueDate && dayjs(this.modelingExercise.assessmentDueDate).isBefore(dayjs());

        if (this.submission.model) {
            this.model = JSON.parse(this.submission.model);
        } else {
            this.alertService.closeAll();
            this.alertService.warning('artemisApp.modelingAssessmentEditor.messages.noModel');
        }

        this.checkPermissions();
        this.getComplaint();

        if (this.result && this.submission?.participation) {
            this.submission.participation.results = [this.result];
            this.result.participation = this.submission.participation;
        }

        if (!this.modelingExercise.diagramType) {
            this.modelingExercise.diagramType = UMLDiagramType.ClassDiagram;
        }

        if (this.result?.feedbacks) {
            this.result = this.modelingAssessmentService.convertResult(this.result);
        } else if (this.result) {
            this.result.feedbacks = [];
        }

        // Only load suggestions for new assessments, they don't make sense later.
        // The assessment is new if it only contains automatic feedback.
        if (this.modelingExercise.feedbackSuggestionModule && (this.result?.feedbacks?.length ?? 0) === this.automaticFeedback.length) {
            this.feedbackSuggestions = await this.loadFeedbackSuggestions(this.modelingExercise, this.submission);

            if (this.result) {
                this.result.feedbacks = [...(this.result?.feedbacks || []), ...this.feedbackSuggestions.filter((feedback) => Boolean(feedback.reference))];
            }
        }

        this.handleFeedback(this.result?.feedbacks);

        if ((!this.result?.assessor || this.result.assessor.id === this.userId) && !this.result?.completionDate) {
            this.alertService.closeAll();
            this.alertService.info('artemisApp.modelingAssessmentEditor.messages.lock');
        }

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);

        this.isLoading = false;
    }

    /**
     * Show a set of feedbacks and feedback suggestions in the Apollon modeling editor
     * @param feedbacks The feedbacks to show in the editor
     */
    private updateApollonEditorWithFeedback(feedbacks: Feedback[]): void {
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference);

        if (!this.isApollonModelLoaded) {
            this.isApollonModelLoaded = true;
            this.calculateTotalScore();
            this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
        }

        this.validateFeedback();
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
     * Afterward, it triggers the highlighting of feedback elements, if necessary.
     */
    private handleFeedback(feedback?: Feedback[]): void {
        if (!feedback || feedback.length === 0) {
            return;
        }

        this.referencedFeedback = feedback.filter((feedbackElement) => feedbackElement.reference);
        this.unreferencedFeedback = feedback.filter((feedbackElement) => !feedbackElement.reference);

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

    /**
     * Remove a feedback suggestion because it was accepted or discarded.
     * @param feedback Feedback suggestion to remove
     */
    removeSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((feedbackSuggestion) => feedbackSuggestion !== feedback);
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun, this.isAssessor, this.hasAssessmentDueDatePassed, this.result, this.complaint, this.modelingExercise);
    }

    private handleErrorResponse(error: HttpErrorResponse): void {
        this.loadingInitialSubmission = false;
        this.submission = undefined;

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
        this.alertService.error('artemisApp.modelingAssessmentEditor.messages.loadSubmissionFailed');
    }

    onSaveAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.result!.id!, this.feedback, this.submission!.id!, this.result!.assessmentNote?.note).subscribe({
            next: (result: Result) => {
                this.result = result;
                this.handleFeedback(this.result.feedbacks);
                this.alertService.closeAll();
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
            },
            error: () => {
                this.alertService.closeAll();
                this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
            },
        });
    }

    onSubmitAssessment() {
        if ((this.model && this.referencedFeedback.length < Object.keys(this.model.elements).length) || !this.assessmentsAreValid) {
            const confirmationMessage = this.translateService.instant('artemisApp.modelingAssessmentEditor.messages.confirmSubmission');

            // if the assessment is before the assessment due date, don't show the confirm submission button
            const isBeforeAssessmentDueDate = this.modelingExercise?.assessmentDueDate && dayjs().isBefore(this.modelingExercise.assessmentDueDate);
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
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.result!.id!, this.feedback, this.submission!.id!, this.result!.assessmentNote?.note, true).subscribe({
            next: (result: Result) => {
                result.participation!.results = [result];
                this.result = result;

                this.alertService.closeAll();
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.submitSuccessful');

                this.highlightMissingFeedback = false;
            },
            error: (error: HttpErrorResponse) => {
                let errorMessage = 'artemisApp.modelingAssessmentEditor.messages.submitFailed';
                if (error.error && error.error.entityName && error.error.message) {
                    errorMessage = `artemisApp.${error.error.entityName}.${error.error.message}`;
                }
                this.alertService.closeAll();
                this.alertService.error(errorMessage);
            },
        });
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param assessmentAfterComplaint the response to the complaint that is sent to the server along with the assessment update along with onSuccess and onError callbacks
     */
    onUpdateAssessmentAfterComplaint(assessmentAfterComplaint: AssessmentAfterComplaint): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }
        this.modelingAssessmentService
            .updateAssessmentAfterComplaint(this.feedback, assessmentAfterComplaint.complaintResponse, this.submission!.id!, this.result?.assessmentNote?.note)
            .subscribe({
                next: (response) => {
                    assessmentAfterComplaint.onSuccess();
                    this.result = response.body!;
                    // reconnect
                    this.result.participation!.results = [this.result];
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintSuccessful');
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
                    assessmentAfterComplaint.onError();
                    this.alertService.closeAll();
                    const error = httpErrorResponse.error;
                    if (error && error.errorKey && error.errorKey === 'complaintLock') {
                        this.alertService.error(error.message, error.params);
                    } else {
                        this.alertService.error('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintFailed');
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

    /**
     * On change handler for feedback changes coming from the Apollon modeling editor. Whenever an assessment is altered
     * in the editor, this method is invoked and the assessment component updated to show the new entries.
     * @param feedback The feedback present in the editor.
     */
    onFeedbackChanged(feedback: Feedback[]) {
        this.updateApollonEditorWithFeedback(feedback);
    }

    assessNext() {
        this.isLoading = true;
        this.nextSubmissionBusy = true;
        this.modelingSubmissionService.getSubmissionWithoutAssessment(this.modelingExercise!.id!, true, this.correctionRound).subscribe({
            next: (submission?: ModelingSubmission) => {
                if (!submission) {
                    // there are no unassessed submissions
                    this.submission = undefined;
                    return;
                }

                this.nextSubmissionBusy = false;
                this.isLoading = false;

                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.onSameUrlNavigation = 'reload';

                // navigate to root and then to new assessment page to trigger re-initialization of the components
                const url = getLinkToSubmissionAssessment(ExerciseType.MODELING, this.courseId, this.exerciseId, undefined, submission.id!, this.examId, this.exerciseGroupId);
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
        for (const element of Object.values(this.model.elements)) {
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
        const maxPoints = getTotalMaxPoints(this.modelingExercise!);
        const creditsTotalScore = this.structuredGradingCriterionService.computeTotalScore(this.feedback);
        this.totalScore = getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints);
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
