import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AssessmentAfterComplaint } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Course } from 'app/entities/course.model';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { Feedback } from 'app/entities/feedback.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult, getSubmissionResultById } from 'app/entities/submission.model';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercises/shared/exercise/exercise.utils';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { FileService } from 'app/shared/http/file.service';
import { onError } from 'app/shared/util/global.utils';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import dayjs from 'dayjs/esm';
import { filter, finalize } from 'rxjs/operators';

@Component({
    providers: [FileUploadAssessmentService],
    templateUrl: './file-upload-assessment.component.html',
    styles: [],
    encapsulation: ViewEncapsulation.None,
})
export class FileUploadAssessmentComponent implements OnInit, OnDestroy {
    private changeDetectorRef = inject(ChangeDetectorRef);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private accountService = inject(AccountService);
    private location = inject(Location);
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private complaintService = inject(ComplaintService);
    private fileService = inject(FileService);
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    submissionService = inject(SubmissionService);

    text: string;
    participation: StudentParticipation;
    submission?: FileUploadSubmission;
    unassessedSubmission?: FileUploadSubmission;
    result?: Result;
    unreferencedFeedback: Feedback[] = [];
    exercise?: FileUploadExercise;
    course?: Course;
    exerciseId: number;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError?: string;
    isAssessor = true;
    busy = true;
    showResult = true;
    complaint: Complaint;
    ComplaintType = ComplaintType;
    notFound = false;
    userId: number;
    isLoading = true;
    isTestRun = false;
    courseId: number;
    hasAssessmentDueDatePassed: boolean;
    correctionRound = 0;
    resultId: number;
    examId = 0;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    loadingInitialSubmission = true;
    highlightDifferences = false;

    private cancelConfirmationText: string;

    // Icons
    farListAlt = faListAlt;

    constructor() {
        const translateService = inject(TranslateService);

        this.assessmentsAreValid = false;
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    get assessments(): Feedback[] {
        return [...this.unreferencedFeedback];
    }

    public ngOnInit(): void {
        this.busy = true;

        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun = queryParams.get('testRun') === 'true';
            if (queryParams.get('correction-round')) {
                this.correctionRound = parseInt(queryParams.get('correction-round')!, 10);
            }
        });

        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            const exerciseId = Number(params['exerciseId']);
            this.resultId = Number(params['resultId']) ?? 0;
            this.exerciseId = exerciseId;

            const examId = params['examId'];
            if (examId) {
                this.examId = Number(examId);
                this.exerciseGroupId = Number(params['exerciseGroupId']);
            }

            this.exerciseDashboardLink = getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun);

            const submissionValue = params['submissionId'];
            const submissionId = Number(submissionValue);
            if (submissionValue === 'new') {
                this.loadOptimalSubmission(this.exerciseId);
            } else {
                this.loadSubmission(submissionId);
            }
        });
    }

    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.fileUploadSubmissionService.getSubmissionWithoutAssessment(exerciseId, true, this.correctionRound).subscribe({
            next: (submission?: FileUploadSubmission) => {
                if (!submission) {
                    // there is no submission waiting for assessment at the moment
                    this.navigateBack();
                    this.alertService.info('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                    return;
                }

                this.initializePropertiesFromSubmission(submission);
                this.validateAssessment();
                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            error: (error: HttpErrorResponse) => {
                this.loadingInitialSubmission = false;
                if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.navigateBack();
                } else {
                    this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
                }
            },
        });
    }

    private loadSubmission(submissionId: number): void {
        this.fileUploadSubmissionService
            .get(submissionId, this.correctionRound, this.resultId)
            .pipe(filter((res) => !!res))
            .subscribe({
                next: (res) => {
                    this.initializePropertiesFromSubmission(res.body!);
                    this.validateAssessment();
                },
                error: (error: HttpErrorResponse) => {
                    this.loadingInitialSubmission = false;
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.navigateBack();
                    } else {
                        onError(this.alertService, error);
                    }
                },
            });
    }

    private initializePropertiesFromSubmission(submission: FileUploadSubmission): void {
        this.loadingInitialSubmission = false;
        this.submission = submission;
        this.participation = this.submission.participation as StudentParticipation;
        this.exercise = this.participation.exercise as FileUploadExercise;
        /**
         * CARE: Setting access rights for exercises should not happen this way and is a workaround.
         *       The access rights should always be set when loading the exercise/course in the service!
         * Problem: For a reason, which I do not understand, the exercise is undefined when the exercise is loaded
         *       leading to {@link AccountService#setAccessRightsForExerciseAndReferencedCourse} skipping setting the
         *       access rights.
         *       This problem reoccurs in {@link CodeEditorTutorAssessmentContainerComponent#handleReceivedSubmission}
         */
        this.accountService.setAccessRightsForExercise(this.exercise);
        this.course = getCourseFromExercise(this.exercise);
        this.hasAssessmentDueDatePassed = !!this.exercise.assessmentDueDate && dayjs(this.exercise.assessmentDueDate).isBefore(dayjs());
        if (this.resultId > 0) {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            this.correctionRound = this.submission.results?.findIndex((result) => result.id === this.resultId)!;
            this.result = getSubmissionResultById(this.submission, this.resultId);
        } else {
            this.result = getLatestSubmissionResult(this.submission);
        }
        this.getComplaint();

        if (this.result) {
            this.submission.participation!.results = [this.result];
            this.result!.participation = this.submission.participation;
        }
        if (this.result?.feedbacks) {
            this.unreferencedFeedback = this.result.feedbacks;
        } else if (this.result) {
            this.result!.feedbacks = [];
        }
        if ((!this.result?.assessor || this.result?.assessor.id === this.userId) && !this.result?.completionDate) {
            this.alertService.closeAll();
            this.alertService.info('artemisApp.fileUploadAssessment.messages.lock');
        }

        this.checkPermissions();
        this.calculateTotalScore();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);

        this.busy = false;
        this.isLoading = false;
    }

    public ngOnDestroy(): void {
        this.changeDetectorRef.detach();
    }

    public addFeedback(): void {
        const feedback = new Feedback();
        this.unreferencedFeedback.push(feedback);
        this.validateAssessment();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.unreferencedFeedback.indexOf(assessmentToDelete);
        this.unreferencedFeedback.splice(indexToDelete, 1);
        this.validateAssessment();
    }

    /**
     * Load next assessment in the same page.
     * It calls the api to load the new unassessed submission in the same page.
     * For the new submission to appear on the same page, the url has to be reloaded.
     */
    assessNext() {
        this.isLoading = true;
        this.unreferencedFeedback = [];
        this.fileUploadSubmissionService.getSubmissionWithoutAssessment(this.exercise!.id!, false, this.correctionRound).subscribe({
            next: (submission?: FileUploadSubmission) => {
                this.isLoading = false;
                this.unassessedSubmission = submission;
                if (!submission) {
                    // there are no unassessed submissions
                    this.submission = undefined;
                    return;
                }

                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.onSameUrlNavigation = 'reload';

                const url = getLinkToSubmissionAssessment(
                    ExerciseType.FILE_UPLOAD,
                    this.courseId,
                    this.exerciseId,
                    this.unassessedSubmission!.participation!.id!,
                    this.unassessedSubmission!.id!,
                    this.examId,
                    this.exerciseGroupId,
                );
                this.router.navigate(url);
            },
            error: (error: HttpErrorResponse) => {
                this.isLoading = false;
                onError(this.alertService, error);
            },
        });
    }

    onSaveAssessment() {
        this.isLoading = true;
        this.fileUploadAssessmentService
            .saveAssessment(this.assessments, this.submission!.id!, this.result?.assessmentNote?.note)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: (result: Result) => {
                    this.result = result;
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.saveSuccessful');
                },
                error: () => {
                    this.alertService.closeAll();
                    this.alertService.error('artemisApp.assessment.messages.saveFailed');
                },
            });
    }

    onSubmitAssessment() {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            return;
        }
        this.isLoading = true;
        this.fileUploadAssessmentService
            .saveAssessment(this.assessments, this.submission!.id!, this.result?.assessmentNote?.note, true)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: (result: Result) => {
                    this.result = result;
                    this.updateParticipationWithResult();
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.submitSuccessful');
                },
                error: (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
            });
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.isLoading = true;
            this.fileUploadAssessmentService
                .cancelAssessment(this.submission!.id!)
                .pipe(finalize(() => (this.isLoading = false)))
                .subscribe(() => {
                    this.navigateBack();
                });
        }
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results![0] = this.result!;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    getComplaint(): void {
        this.complaintService.findBySubmissionId(this.submission!.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            error: (err: HttpErrorResponse) => {
                onError(this.alertService, err);
            },
        });
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise, this.submission, this.isTestRun);
    }

    updateAssessment() {
        this.validateAssessment();
    }

    /**
     * Checks if the assessment is valid:
     *   - There must be at least one referenced feedback.
     *   - Each feedback must have either a score or a feedback text or both.
     *   - The score must be a valid number.
     *
     * Additionally, the total score is calculated for all numerical credits.
     */
    public validateAssessment(): void {
        this.assessmentsAreValid = true;
        this.invalidError = undefined;

        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = Feedback.haveCreditsAndComments(this.unreferencedFeedback);

        this.calculateTotalScore();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
    }

    /**
     * Calculates the total score of the current assessment.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    private calculateTotalScore() {
        const maxPoints = getTotalMaxPoints(this.exercise);
        const creditsTotalScore = this.structuredGradingCriterionService.computeTotalScore(this.assessments);
        this.totalScore = getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints);
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
    }

    private checkPermissions() {
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

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param assessmentAfterComplaint the response to the complaint that is sent to the server along with the assessment update along with onSuccess and onError callbacks
     */
    onUpdateAssessmentAfterComplaint(assessmentAfterComplaint: AssessmentAfterComplaint): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }
        this.isLoading = true;
        this.fileUploadAssessmentService
            .updateAssessmentAfterComplaint(this.assessments, assessmentAfterComplaint.complaintResponse, this.submission!.id!, this.result!.assessmentNote?.note)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: (response) => {
                    assessmentAfterComplaint.onSuccess();
                    this.result = response.body!;
                    this.updateParticipationWithResult();
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
                    assessmentAfterComplaint.onError();
                    this.alertService.closeAll();
                    const error = httpErrorResponse.error;
                    if (error && error.errorKey && error.errorKey === 'complaintLock') {
                        this.alertService.error(error.message, error.params);
                    } else {
                        this.alertService.error('artemisApp.assessment.messages.updateAfterComplaintFailed');
                    }
                },
            });
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun, this.isAssessor, this.hasAssessmentDueDatePassed, this.result, this.complaint, this.exercise);
    }

    private onError(error: string) {
        this.alertService.error(error);
    }
}
