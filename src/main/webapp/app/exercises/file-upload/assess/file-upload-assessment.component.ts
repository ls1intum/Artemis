import { ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { Location } from '@angular/common';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { filter, finalize } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileService } from 'app/shared/http/file.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { getLatestSubmissionResult, getSubmissionResultById } from 'app/entities/submission.model';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';

@Component({
    providers: [FileUploadAssessmentService],
    templateUrl: './file-upload-assessment.component.html',
    styles: [],
    encapsulation: ViewEncapsulation.None,
})
export class FileUploadAssessmentComponent implements OnInit, OnDestroy {
    text: string;
    participation: StudentParticipation;
    submission: FileUploadSubmission;
    unassessedSubmission: FileUploadSubmission;
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
    hasNewSubmissions = true;
    resultId: number;
    examId = 0;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    loadingInitialSubmission = true;
    highlightDifferences = false;

    private cancelConfirmationText: string;

    // Icons
    farListAlt = faListAlt;

    constructor(
        private changeDetectorRef: ChangeDetectorRef,
        private alertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private fileUploadAssessmentService: FileUploadAssessmentService,
        private accountService: AccountService,
        private location: Location,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private complaintService: ComplaintService,
        private fileService: FileService,
        public structuredGradingCriterionService: StructuredGradingCriterionService,
        public submissionService: SubmissionService,
    ) {
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
            next: (submission: FileUploadSubmission) => {
                this.initializePropertiesFromSubmission(submission);
                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            error: (error: HttpErrorResponse) => {
                this.loadingInitialSubmission = false;
                if (error.status === 404) {
                    // there is no submission waiting for assessment at the moment
                    this.navigateBack();
                    this.alertService.info('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
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
            next: (response: FileUploadSubmission) => {
                this.isLoading = false;
                this.unassessedSubmission = response;

                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.onSameUrlNavigation = 'reload';

                const url = getLinkToSubmissionAssessment(
                    ExerciseType.FILE_UPLOAD,
                    this.courseId,
                    this.exerciseId,
                    this.unassessedSubmission.participation!.id!,
                    this.unassessedSubmission.id!,
                    this.examId,
                    this.exerciseGroupId,
                );
                this.router.navigate(url);
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.hasNewSubmissions = false;
                } else {
                    this.isLoading = false;
                    onError(this.alertService, error);
                }
            },
        });
    }

    onSaveAssessment() {
        this.isLoading = true;
        this.fileUploadAssessmentService
            .saveAssessment(this.assessments, this.submission.id!)
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
            .saveAssessment(this.assessments, this.submission.id!, true)
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
        this.assessmentsAreValid = false;
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.isLoading = true;
            this.fileUploadAssessmentService
                .cancelAssessment(this.submission.id!)
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
        this.complaintService.findBySubmissionId(this.submission.id!).subscribe({
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

        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = hasUnreferencedFeedback;

        this.calculateTotalScore();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);
    }

    /**
     * Calculates the total score of the current assessment.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    private calculateTotalScore() {
        this.totalScore = this.structuredGradingCriterionService.computeTotalScore(this.assessments);
        // Cap totalScore to maxPoints
        if (this.exercise) {
            const maxPoints = this.exercise.maxPoints! + this.exercise.bonusPoints! ?? 0.0;
            if (this.totalScore > maxPoints) {
                this.totalScore = maxPoints;
            }
            // Do not allow negative score
            if (this.totalScore < 0) {
                this.totalScore = 0;
            }
        }
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFileWithAccessToken(filePath);
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
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            return;
        }
        this.isLoading = true;
        this.fileUploadAssessmentService
            .updateAssessmentAfterComplaint(this.assessments, complaintResponse, this.submission.id!)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe({
                next: (response) => {
                    this.result = response.body!;
                    this.updateParticipationWithResult();
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
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
