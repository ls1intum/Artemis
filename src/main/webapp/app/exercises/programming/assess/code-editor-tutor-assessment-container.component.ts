import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import * as moment from 'moment';
import { now } from 'moment';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { cloneDeep, orderBy as _orderBy } from 'lodash';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Location } from '@angular/common';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { Course } from 'app/entities/course.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

@Component({
    selector: 'jhi-code-editor-tutor-assessment',
    templateUrl: './code-editor-tutor-assessment-container.component.html',
})
export class CodeEditorTutorAssessmentContainerComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    participationForManualResult: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    submission?: ProgrammingSubmission;
    manualResult?: Result;
    automaticResult?: Result;
    userId: number;
    // for assessment-layout
    isLoading = false;
    isTestRun = false;
    saveBusy = false;
    submitBusy = false;
    cancelBusy = false;
    nextSubmissionBusy = false;
    isAtLeastInstructor = false;
    isAssessor = false;
    assessmentsAreValid = false;
    complaint: Complaint;
    private cancelConfirmationText: string;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;
    showEditorInstructions = true;
    hasAssessmentDueDatePassed: boolean;

    private get course(): Course | undefined {
        return this.exercise?.course || this.exercise?.exerciseGroup?.exam?.course;
    }

    generalFeedback = new Feedback();
    unreferencedFeedback: Feedback[] = [];
    referencedFeedback: Feedback[] = [];
    automaticFeedback: Feedback[] = [];

    constructor(
        private manualResultService: ProgrammingAssessmentManualResultService,
        private router: Router,
        private location: Location,
        private accountService: AccountService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private domainService: DomainService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private complaintService: ComplaintService,
        private translateService: TranslateService,
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun = queryParams.get('testRun') === 'true';
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;
            const participationId = Number(params['participationId']);
            this.programmingExerciseParticipationService.getStudentParticipationWithResults(participationId).subscribe(
                (participationWithResults: ProgrammingExerciseStudentParticipation) => {
                    // Set domain to make file editor work properly
                    this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                    this.participation = participationWithResults;
                    this.automaticResult = this.getLatestAutomaticResult(this.participation.results);
                    this.manualResult = this.getLatestManualResult(this.participation.results);

                    // Add participation with manual results to display manual result
                    this.participationForManualResult = cloneDeep(this.participation);
                    this.participationForManualResult.results = this.manualResult.hasFeedback ? [this.manualResult] : [];
                    // Either submission from latest manual or automatic result
                    this.submission = this.getLatestResult(this.participation.results)?.submission as ProgrammingSubmission;
                    this.exercise = this.participation.exercise as ProgrammingExercise;
                    this.hasAssessmentDueDatePassed = !!this.exercise!.assessmentDueDate && moment(this.exercise!.assessmentDueDate).isBefore(now());

                    this.checkPermissions();
                    this.handleFeedback();

                    if (this.manualResult && this.manualResult.hasComplaint) {
                        this.getComplaint();
                    }
                },
                () => {
                    this.participationCouldNotBeFetched = true;
                    this.loadingParticipation = false;
                },
                () => {
                    this.loadingParticipation = false;
                },
            );
        });
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy = true;
        this.avoidCircularStructure();
        this.manualResultService.save(this.participation.id!, this.manualResult!).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            (error: HttpErrorResponse) => this.onError(`error.${error?.error?.errorKey}`),
        );
    }

    /**
     * Submit the assessment
     */
    submit(): void {
        this.submitBusy = true;
        this.avoidCircularStructure();
        this.manualResultService.save(this.participation.id!, this.manualResult!, true).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            (error: HttpErrorResponse) => this.onError(`error.${error?.error?.errorKey}`),
        );
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.cancelBusy = true;
        if (confirmCancel && this.exercise && this.submission) {
            // TODO: Implement lock for programming submissions, otherwise cancel will only work when saving before.
            // this.manualResultService.cancelAssessment(this.submission.id).subscribe(() => this.navigateBack());
            this.navigateBack();
        }
    }

    /**
     * Go to next submission
     */
    nextSubmission() {
        this.programmingSubmissionService.getProgrammingSubmissionForExerciseWithoutAssessment(this.exercise.id!).subscribe(
            (response: ProgrammingSubmission) => {
                const unassessedSubmission = response;
                this.router.onSameUrlNavigation = 'reload';
                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.navigateByUrl(
                    `/course-management/${this.course!.id}/programming-exercises/${this.exercise.id}/code-editor/${unassessedSubmission.participation?.id}/assessment`,
                    {},
                );
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.onError('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                } else {
                    this.onError(error?.message);
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
        this.setFeedbacksForManualResult();
        this.manualResultService.updateAfterComplaint(this.manualResult!.feedbacks!, complaintResponse, this.manualResult!.submission!.id!).subscribe(
            (result: Result) => {
                this.participationForManualResult.results![0] = this.manualResult = result;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
            },
            () => {
                this.jhiAlertService.clear();
                this.onError('artemisApp.assessment.messages.updateAfterComplaintFailed');
            },
        );
    }

    /**
     * Navigates back to previous view
     */
    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise, this.submission, this.isTestRun);
    }

    /**
     * Boolean which determines whether the user can override a result.
     * If no exercise is loaded, for example during loading between exercises, we return false.
     * Instructors can always override a result.
     * Tutors can override their own results within the assessment due date, if there is no complaint about their assessment.
     * They cannot override a result anymore, if there is a complaint. Another tutor must handle the complaint.
     */
    get canOverride(): boolean {
        if (this.exercise) {
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
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = moment().isBefore(this.exercise.assessmentDueDate);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    /**
     * Updates the referenced feedbacks, which are the inline feedbacks added directly in the code.
     * @param feedbacks Inline feedbacks directly in the code
     */
    onUpdateFeedback(feedbacks: Feedback[]) {
        // Filter out other feedback than manual feedback
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != null && feedbackElement.type === FeedbackType.MANUAL);
        this.validateFeedback();
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.jhiAlertService.error(error);
        this.saveBusy = this.cancelBusy = this.submitBusy = this.nextSubmissionBusy = false;
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.calculateTotalScore();
        const hasReferencedFeedback = this.referencedFeedback.filter(Feedback.isPresent).length > 0;
        const hasUnreferencedFeedback = this.unreferencedFeedback.filter(Feedback.isPresent).length > 0;
        const hasGeneralFeedback = Feedback.hasDetailText(this.generalFeedback);
        this.assessmentsAreValid = hasReferencedFeedback || hasGeneralFeedback || hasUnreferencedFeedback;
    }

    /**
     * Defines whether the inline feedback should be read only or not
     */
    readOnly() {
        return !this.isAtLeastInstructor && !!this.complaint && this.isAssessor;
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.participationForManualResult.results![0] = this.manualResult = response.body!;
        this.jhiAlertService.clear();
        this.jhiAlertService.success(translationKey);
        this.saveBusy = this.submitBusy = false;
    }

    private getLatestResult(results?: Result[]) {
        const sortedResults = this.sortResults(results);
        return sortedResults.length > 0 ? sortedResults[0] : undefined;
    }

    private sortResults(results?: Result[]) {
        return _orderBy(results || [], 'id', 'desc');
    }

    private getLatestAutomaticResult(results?: Result[]) {
        const automaticResults = (results || []).filter((result) => result.assessmentType === AssessmentType.AUTOMATIC);
        const sortedResults = this.sortResults(automaticResults);
        return sortedResults.length > 0 ? sortedResults[0] : undefined;
    }

    private getLatestManualResult(results?: Result[]): Result {
        const manualResults = (results || []).filter((result) => Result.isManualResult(result));
        const sortedResults = this.sortResults(manualResults);
        const initialManualResult = new Result();
        // Manual results are always rated
        initialManualResult.rated = true;
        return sortedResults.length > 0 ? sortedResults[0] : initialManualResult;
    }

    /**
     * Checks if there is a manual result and the user is the assessor. If there is no manual result, then the user is the assessor.
     * Checks if the user is at least instructor in course.
     * @private
     */
    private checkPermissions() {
        if (this.manualResult?.assessor) {
            this.isAssessor = this.manualResult.assessor.id === this.userId;
        } else {
            this.isAssessor = true;
        }
        if (this.exercise) {
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course!);
        }
    }

    private getComplaint(): void {
        this.complaintService.findByResultId(this.manualResult!.id!).subscribe(
            (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            (err: HttpErrorResponse) => {
                this.onError(err?.message);
            },
        );
    }

    private handleFeedback(): void {
        // Setup automatic feedback
        this.automaticFeedback = this.automaticResult?.feedbacks!;
        this.automaticFeedback.forEach((feedback) => {
            feedback.id = undefined;
            if (!feedback.credits) {
                feedback.credits = 0;
            }
        });

        // Setup not automatically generated feedbacks
        const feedbacks = this.manualResult?.feedbacks || [];
        this.unreferencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference == null && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);

        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != null && feedbackElement.type === FeedbackType.MANUAL);
        const generalFeedbackIndex = feedbacks.findIndex(
            (feedbackElement) => feedbackElement.reference == null && feedbackElement.type !== FeedbackType.MANUAL_UNREFERENCED && feedbackElement.type !== FeedbackType.AUTOMATIC,
        );
        if (generalFeedbackIndex !== -1) {
            this.generalFeedback = feedbacks[generalFeedbackIndex];
        } else {
            this.generalFeedback = new Feedback();
        }
        this.validateFeedback();
    }

    private setFeedbacksForManualResult() {
        if (Feedback.hasDetailText(this.generalFeedback)) {
            this.manualResult!.feedbacks = [this.generalFeedback, ...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        } else {
            this.manualResult!.feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        }
    }

    private setAttributesForManualResult(totalScore: number) {
        this.setFeedbacksForManualResult();
        this.manualResult!.hasFeedback = this.manualResult!.feedbacks!.length > 0;
        this.manualResult!.resultString = `${this.automaticResult!.resultString}, ${totalScore} of ${this.exercise.maxScore} points`;
        this.manualResult!.score = Math.round((totalScore / this.exercise.maxScore!) * 100);
        // This is done to update the result string in result.component.ts
        this.manualResult = cloneDeep(this.manualResult);
    }

    private avoidCircularStructure() {
        if (this.manualResult?.participation?.results) {
            this.manualResult.participation.results = [];
        }
    }

    private calculateTotalScore() {
        const feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        const maxPoints = this.exercise.maxScore! + (this.exercise.bonusPoints! ?? 0.0);
        let totalScore = 0.0;
        let scoreAutomaticTests = 0.0;
        const gradingInstructions = {}; // { instructionId: noOfEncounters }

        feedbacks.forEach((feedback) => {
            // Check for feedback from automatic tests and store them separately
            if (feedback.type === FeedbackType.AUTOMATIC && !Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                scoreAutomaticTests += feedback.credits!;
            } else {
                if (feedback.gradingInstruction) {
                    totalScore = this.structuredGradingCriterionService.calculateScoreForGradingInstructions(feedback, totalScore, gradingInstructions);
                } else {
                    totalScore += feedback.credits!;
                }
            }
        });

        // Cap automatic test feedback to maxScore + bonus points of exercise
        if (scoreAutomaticTests > maxPoints) {
            scoreAutomaticTests = maxPoints;
        }
        totalScore += scoreAutomaticTests;
        // Do not allow negative score
        if (totalScore < 0) {
            totalScore = 0;
        }
        // Cap totalScore to maxPoints
        if (totalScore > maxPoints) {
            totalScore = maxPoints;
        }

        totalScore = +totalScore.toFixed(2);

        // Set attributes of manual result
        this.setAttributesForManualResult(totalScore);
    }
}
