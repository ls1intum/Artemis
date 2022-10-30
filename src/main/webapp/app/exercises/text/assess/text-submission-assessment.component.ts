import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, NavigationExtras, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Result } from 'app/entities/result.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { notUndefined, onError } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NEW_ASSESSMENT_PATH } from 'app/exercises/text/assess/text-submission-assessment.route';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import {
    getLatestSubmissionResult,
    getSubmissionResultByCorrectionRound,
    getSubmissionResultById,
    setLatestSubmissionResult,
    setSubmissionResultByCorrectionRound,
} from 'app/entities/submission.model';
import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Course } from 'app/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-text-submission-assessment',
    templateUrl: './text-submission-assessment.component.html',
    styleUrls: ['./text-submission-assessment.component.scss'],
})
export class TextSubmissionAssessmentComponent extends TextAssessmentBaseComponent implements OnInit {
    /*
     * The instance of this component is REUSED for multiple assessments if using the "Assess Next" button!
     * All properties must be initialized with a default value (or null) in the resetComponent() method.
     * For traceability: Keep order in resetComponent() consistent with declaration.
     */

    participation?: StudentParticipation;
    result?: Result;
    unreferencedFeedback: Feedback[];
    complaint?: Complaint;
    totalScore: number;
    isTestRun = false;
    isLoading: boolean;
    saveBusy: boolean;
    submitBusy: boolean;
    cancelBusy: boolean;
    nextSubmissionBusy: boolean;
    isAssessor: boolean;
    assessmentsAreValid: boolean;
    noNewSubmissions: boolean;
    hasAssessmentDueDatePassed: boolean;
    correctionRound: number;
    resultId: number;
    loadingInitialSubmission = true;
    highlightDifferences = false;

    /*
     * Non-reset properties:
     * These properties are not reset on purpose, as they cannot change between assessments.
     */
    private cancelConfirmationText: string;

    // ExerciseId is updated from Route Subscription directly.
    exerciseId: number;
    courseId: number;
    course?: Course;
    examId = 0;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    isExamMode = false;

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    // Icons
    farListAlt = faListAlt;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private location: Location,
        private route: ActivatedRoute,
        protected alertService: AlertService,
        protected accountService: AccountService,
        protected assessmentsService: TextAssessmentService,
        private complaintService: ComplaintService,
        translateService: TranslateService,
        protected structuredGradingCriterionService: StructuredGradingCriterionService,
        private submissionService: SubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
    ) {
        super(alertService, accountService, assessmentsService, structuredGradingCriterionService);
        translateService.get('artemisApp.textAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
        this.correctionRound = 0;
        this.resetComponent();
    }

    /**
     * This method is called before the component is REUSED!
     * All properties MUST be set to a default value (e.g. null) to prevent data corruption by state leaking into following new assessments.
     */
    private resetComponent(): void {
        this.participation = undefined;
        this.submission = undefined;
        this.exercise = undefined;
        this.result = undefined;
        this.unreferencedFeedback = [];
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.complaint = undefined;
        this.totalScore = 0;

        this.isLoading = true;
        this.saveBusy = false;
        this.submitBusy = false;
        this.cancelBusy = false;
        this.nextSubmissionBusy = false;
        this.isAssessor = false;
        this.assessmentsAreValid = false;
        this.noNewSubmissions = false;
        this.highlightDifferences = false;
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun = queryParams.get('testRun') === 'true';
            this.correctionRound = Number(queryParams.get('correction-round'));
        });

        this.activatedRoute.paramMap.subscribe((paramMap) => {
            this.exerciseId = Number(paramMap.get('exerciseId'));
            this.resultId = Number(paramMap.get('resultId')) ?? 0;
            this.courseId = Number(paramMap.get('courseId'));
            if (paramMap.has('examId')) {
                this.examId = Number(paramMap.get('examId'));
                this.exerciseGroupId = Number(paramMap.get('exerciseGroupId'));
                this.isExamMode = true;
            }
            this.exerciseDashboardLink = getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun);
        });
        this.activatedRoute.data.subscribe(({ studentParticipation }) => this.setPropertiesFromServerResponse(studentParticipation));
    }

    private setPropertiesFromServerResponse(studentParticipation: StudentParticipation) {
        this.resetComponent();
        this.loadingInitialSubmission = false;
        if (studentParticipation == undefined) {
            // Show "No New Submission" banner on .../submissions/new/assessment route
            this.noNewSubmissions = this.isNewAssessmentRoute;
            return;
        }

        this.participation = studentParticipation;
        this.submission = this.participation!.submissions![0] as TextSubmission;
        this.exercise = this.participation?.exercise as TextExercise;
        this.course = getCourseFromExercise(this.exercise);
        setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));

        if (this.resultId > 0) {
            this.result = getSubmissionResultById(this.submission, this.resultId);
            this.correctionRound = this.submission.results?.findIndex((result) => result.id === this.resultId)!;
        } else {
            this.result = getSubmissionResultByCorrectionRound(this.submission, this.correctionRound);
        }

        this.hasAssessmentDueDatePassed = !!this.exercise!.assessmentDueDate && dayjs(this.exercise!.assessmentDueDate).isBefore(dayjs());

        this.prepareTextBlocksAndFeedbacks();
        this.getComplaint();
        this.updateUrlIfNeeded();

        this.checkPermissions(this.result);
        this.totalScore = this.computeTotalScore(this.assessments);
        this.isLoading = false;

        // track feedback in athene
        this.assessmentsService.trackAssessment(this.submission, 'start');

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);
    }

    private updateUrlIfNeeded() {
        if (this.isNewAssessmentRoute) {
            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = this.router
                .createUrlTree(
                    getLinkToSubmissionAssessment(
                        ExerciseType.TEXT,
                        this.courseId,
                        this.exerciseId,
                        this.participation!.id!,
                        this.submission!.id!,
                        this.examId,
                        this.exerciseGroupId,
                    ),
                )
                .toString();
            this.location.go(newUrl);
        }
    }

    private get isNewAssessmentRoute(): boolean {
        return this.activatedRoute.routeConfig?.path === NEW_ASSESSMENT_PATH;
    }

    private checkPermissions(result?: Result): void {
        this.isAssessor = result?.assessor?.id === this.userId;
    }

    /**
     * Save the assessment
     */
    save(): void {
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        // track feedback in athene
        this.assessmentsService.trackAssessment(this.submission, 'save');

        this.saveBusy = true;
        this.assessmentsService.save(this.participation!.id!, this.result!.id!, this.assessments, this.textBlocksWithFeedback).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    /**
     * Submit the assessment
     */
    submit(): void {
        if (!this.result?.id) {
            return; // We need to have saved the result before
        }

        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        // track feedback in athene
        this.assessmentsService.trackAssessment(this.submission, 'submit');

        this.submitBusy = true;
        this.assessmentsService.submit(this.participation!.id!, this.result!.id!, this.assessments, this.textBlocksWithFeedback).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
        this.assessmentsAreValid = false;
    }

    protected handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        super.handleSaveOrSubmitSuccessWithAlert(response, translationKey);
        response.body!.feedbacks?.forEach((newFeedback) => {
            newFeedback.conflictingTextAssessments = this.result?.feedbacks?.find((feedback) => feedback.id === newFeedback.id)?.conflictingTextAssessments;
        });
        this.result = response.body!;
        setSubmissionResultByCorrectionRound(this.submission!, this.result, this.correctionRound);
        this.saveBusy = this.submitBusy = false;
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.cancelBusy = true;
        if (confirmCancel && this.exercise && this.submission) {
            this.assessmentsService.cancelAssessment(this.participation!.id!, this.submission!.id!).subscribe(() => this.navigateBack());
        }
    }

    /**
     * Go to next submission
     */
    async nextSubmission(): Promise<void> {
        const url = getLinkToSubmissionAssessment(ExerciseType.TEXT, this.courseId, this.exerciseId, this.participation!.id!, 'new', this.examId, this.exerciseGroupId);
        this.nextSubmissionBusy = true;
        await this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } });
    }

    /**
     * if the conflict badge is clicked, navigate to conflict page and add the submission to the extras.
     * @param feedbackId - selected feedback id with conflicts.
     */
    async navigateToConflictingSubmissions(feedbackId: number): Promise<void> {
        const tempSubmission = this.submission!;
        const latestSubmissionResult = getLatestSubmissionResult(tempSubmission)!;
        latestSubmissionResult.completionDate = undefined;
        latestSubmissionResult.submission = undefined;
        latestSubmissionResult.participation = undefined;

        const url = !this.isExamMode
            ? [
                  '/course-management',
                  this.courseId,
                  'text-exercises',
                  this.exerciseId,
                  'participations',
                  tempSubmission.participation!.id,
                  'submissions',
                  this.submission!.id,
                  'text-feedback-conflict',
                  feedbackId,
              ]
            : [
                  '/course-management',
                  this.courseId,
                  'exams',
                  this.examId,
                  'exercise-groups',
                  this.exerciseGroupId,
                  'text-exercises',
                  this.exerciseId,
                  'participations',
                  tempSubmission.participation!.id,
                  'submissions',
                  this.submission!.id,
                  'text-feedback-conflict',
                  feedbackId,
              ];
        const navigationExtras: NavigationExtras = { state: { submission: tempSubmission } };
        await this.router.navigate(url, navigationExtras);
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    updateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.assessmentsService
            .updateAssessmentAfterComplaint(this.assessments, this.textBlocksWithFeedback, complaintResponse, this.submission?.id!, this.participation?.id!)
            .subscribe({
                next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.updateAfterComplaintSuccessful'),
                error: (httpErrorResponse: HttpErrorResponse) => {
                    this.alertService.closeAll();
                    const error = httpErrorResponse.error;
                    if (error && error.errorKey && error.errorKey === 'complaintLock') {
                        this.alertService.error(error.message, error.params);
                    } else {
                        this.alertService.error('artemisApp.textAssessment.updateAfterComplaintFailed');
                    }
                },
            });
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise!, this.submission!, this.isTestRun);
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = (hasReferencedFeedback && this.unreferencedFeedback.length === 0) || hasUnreferencedFeedback;

        this.totalScore = this.computeTotalScore(this.assessments);
        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
    }

    private prepareTextBlocksAndFeedbacks(): void {
        if (!this.result) {
            return;
        }
        const feedbacks = this.result.feedbacks || [];
        this.unreferencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);

        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(this.submission?.blocks || [], feedbacks);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
    }

    private getComplaint(): void {
        if (!this.submission) {
            return;
        }

        this.isLoading = true;
        this.complaintService.findBySubmissionId(this.submission.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
                this.isLoading = false;
            },
            error: (err: HttpErrorResponse) => {
                this.handleError(err.error);
            },
        });
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
            if (this.exercise.isAtLeastInstructor) {
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
                isBeforeAssessmentDueDate = dayjs().isBefore(this.exercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun, this.isAssessor, this.hasAssessmentDueDatePassed, this.result, this.complaint, this.exercise);
    }

    protected handleError(error: HttpErrorResponse): void {
        super.handleError(error);
        this.saveBusy = this.submitBusy = false;
    }

    /**
     * Invokes exampleSubmissionService when useAsExampleSubmission is emitted in assessment-layout
     */
    useStudentSubmissionAsExampleSubmission(): void {
        if (this.submission && this.exercise) {
            this.exampleSubmissionService.import(this.submission.id!, this.exercise.id!).subscribe({
                next: () => this.alertService.success('artemisApp.exampleSubmission.submitSuccessful'),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }
}
