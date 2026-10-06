import { Location, UpperCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { isAllowedToModifyFeedback } from 'app/assessment/manage/services/assessment.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { parseCorrectionRound } from 'app/assessment/shared/util/correction-round.util';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { getLatestSubmissionResult, getSubmissionResultById } from 'app/exercise/shared/entities/submission/submission.model';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { onError } from 'app/foundation/util/global.utils';
import { AssessmentNotPossibleYetState, alertIfAssessmentNotPossibleYet, getAssessmentNotPossibleYetState } from 'app/assessment/shared/util/assessment-availability.util';
import { AssessmentNotPossibleYetComponent } from 'app/assessment/shared/assessment-not-possible-yet/assessment-not-possible-yet.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import dayjs from 'dayjs/esm';
import { filter, finalize } from 'rxjs/operators';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/exercise/score-display/score-display.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FileService } from 'app/foundation/service/file.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    providers: [FileUploadAssessmentService],
    templateUrl: './file-upload-assessment.component.html',
    encapsulation: ViewEncapsulation.None,
    imports: [
        AssessmentLayoutComponent,
        ResizeableContainerComponent,
        ScoreDisplayComponent,
        TranslateDirective,
        FaIconComponent,
        AssessmentInstructionsComponent,
        UnreferencedFeedbackComponent,
        RouterLink,
        UpperCasePipe,
        ArtemisTranslatePipe,
        AssessmentNotPossibleYetComponent,
    ],
})
export class FileUploadAssessmentComponent implements OnInit {
    private alertService = inject(AlertService);
    private datePipe = inject(ArtemisDatePipe);
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

    text?: string;
    readonly participation = signal<StudentParticipation>(undefined!);
    readonly submission = signal<FileUploadSubmission | undefined>(undefined);
    unassessedSubmission?: FileUploadSubmission;
    readonly result = signal<Result | undefined>(undefined);
    readonly unreferencedFeedback = signal<Feedback[]>([]);
    readonly exercise = signal<FileUploadExercise | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    exerciseId!: number; // set in ngOnInit() from route params
    readonly totalScore = signal(0);
    readonly assessmentsAreValid = signal<boolean>(undefined!);
    readonly invalidError = signal<string | undefined>(undefined);
    readonly isAssessor = signal(true);
    readonly busy = signal(true);
    readonly complaint = signal<Complaint>(undefined!);
    ComplaintType = ComplaintType;
    notFound = false;
    userId?: number;
    readonly isLoading = signal(true);
    readonly isTestRun = signal(false);
    courseId!: number; // set in ngOnInit() from route params
    readonly hasAssessmentDueDatePassed = signal<boolean>(undefined!);
    readonly correctionRound = signal(0);
    /**
     * The round the URL names right now. This component has no resolver, so the `correction-round` parameter can change
     * without a submission being loaded for it. That value must not become the round of the page on its own: the round
     * is sent to the server as the round to request and then indexes the results that come back, and those two may not
     * disagree. It therefore only reaches {@link correctionRound} when a load starts.
     */
    private correctionRoundFromUrl = 0;
    resultId!: number; // set in ngOnInit() from route params
    examId = 0;
    exerciseGroupId?: number;
    readonly exerciseDashboardLink = signal<string[]>([]);
    readonly loadingInitialSubmission = signal(true);
    // Set when the server refuses to open the assessment because the exam is not over yet: the submission exists, so the
    // page explains the wait instead of showing its "submission not found" state.
    readonly assessmentNotPossibleYet = signal<AssessmentNotPossibleYetState | undefined>(undefined);
    highlightDifferences = false;

    private cancelConfirmationText!: string; // set in constructor from a synchronous translate subscription

    // Icons
    farListAlt = faListAlt;

    constructor() {
        const translateService = inject(TranslateService);

        this.assessmentsAreValid.set(false);
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    get assessments(): Feedback[] {
        return [...this.unreferencedFeedback()];
    }

    readonly getTotalMaxPoints = getTotalMaxPoints;

    public ngOnInit(): void {
        this.busy.set(true);

        // Used to check if the assessor is the current user
        void this.accountService.identity().then((user) => {
            if (user?.id) {
                this.userId = user.id;
            }
        });
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun.set(queryParams.get('testRun') === 'true');
            // The URL decides the round, and an unusable value means the first one; see parseCorrectionRound for why
            // Number() alone will not do. Only remembered here, not shown yet; see correctionRoundFromUrl.
            this.correctionRoundFromUrl = parseCorrectionRound(queryParams.get('correction-round'));
        });

        this.route.params.subscribe((params) => {
            this.resetSubmissionState();
            this.courseId = Number(params['courseId']);
            const exerciseId = Number(params['exerciseId']);
            this.resultId = Number(params['resultId']) || 0;
            this.exerciseId = exerciseId;

            const examId = params['examId'];
            if (examId) {
                this.examId = Number(examId);
                this.exerciseGroupId = Number(params['exerciseGroupId']);
            }

            this.exerciseDashboardLink.set(getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun()));

            const submissionValue = params['submissionId'];
            const submissionId = Number(submissionValue);
            // Taken from the URL once per load, so that the round the submission is requested with is also the round
            // its results are indexed by, even when the parameter has changed since the last load.
            this.correctionRound.set(this.correctionRoundFromUrl);
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

        return filePath.split('.').pop() ?? 'N/A';
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.fileUploadSubmissionService.getSubmissionWithoutAssessment(exerciseId, true, this.correctionRound()).subscribe({
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
                const submissionId = this.submission()?.id;
                if (submissionId) {
                    // Build the path through the router. Artemis uses path-based routing, so window.location.hash is
                    // empty and using it here rewrites the address to the application root once the submission loads.
                    const newUrl = this.router
                        .createUrlTree(
                            getLinkToSubmissionAssessment(
                                ExerciseType.FILE_UPLOAD,
                                this.courseId,
                                this.exerciseId,
                                submission.participation?.id,
                                submissionId,
                                this.examId,
                                this.exerciseGroupId,
                            ),
                            { queryParams: this.route.snapshot.queryParams },
                        )
                        .toString();
                    this.location.go(newUrl);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.loadingInitialSubmission.set(false);
                if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.navigateBack();
                } else if (!this.explainIfAssessmentNotPossibleYet(error)) {
                    this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
                }
            },
        });
    }

    private loadSubmission(submissionId: number): void {
        this.fileUploadSubmissionService
            .get(submissionId, this.correctionRound(), this.resultId)
            .pipe(filter((res) => !!res.body))
            .subscribe({
                next: (res) => {
                    if (res.body) {
                        this.initializePropertiesFromSubmission(res.body);
                        this.validateAssessment();
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.loadingInitialSubmission.set(false);
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.navigateBack();
                    } else if (!this.explainIfAssessmentNotPossibleYet(error)) {
                        onError(this.alertService, error);
                    }
                },
            });
    }

    /**
     * Clears everything that belongs to the previously assessed submission. Angular reuses this component for
     * param-only navigations, so without this the page would keep showing the previous assessment while the next one
     * loads — and would keep showing it instead of the explanation if that load is refused because the exam is not over
     * yet, for example because a student was granted more working time in the meantime.
     */
    private resetSubmissionState(): void {
        this.loadingInitialSubmission.set(true);
        this.isLoading.set(true);
        this.assessmentNotPossibleYet.set(undefined);
        this.submission.set(undefined);
        this.result.set(undefined);
        this.unreferencedFeedback.set([]);
        this.complaint.set(undefined!);
    }

    /**
     * Keeps the server's "assessment is not possible yet" explanation on the page, in place of the "submission not
     * found" state that would otherwise contradict it. A toast would fade and leave only the wrong message behind.
     *
     * @param error the failed response of the endpoint that opens the assessment
     * @returns true if the error was the "assessment is not possible yet" one and is now explained on the page
     */
    private explainIfAssessmentNotPossibleYet(error: HttpErrorResponse): boolean {
        const assessmentNotPossibleYet = getAssessmentNotPossibleYetState(error);
        if (!assessmentNotPossibleYet) {
            return false;
        }
        this.assessmentNotPossibleYet.set(assessmentNotPossibleYet);
        this.alertService.closeAll();
        return true;
    }

    private initializePropertiesFromSubmission(submission: FileUploadSubmission): void {
        this.loadingInitialSubmission.set(false);
        this.submission.set(submission);
        const participation = submission.participation as StudentParticipation;
        this.participation.set(participation);
        const exercise = participation.exercise as FileUploadExercise;
        this.exercise.set(exercise);
        /**
         * CARE: Setting access rights for exercises should not happen this way and is a workaround.
         *       The access rights should always be set when loading the exercise/course in the service!
         * Problem: For a reason, which I do not understand, the exercise is undefined when the exercise is loaded
         *       leading to {@link AccountService#setAccessRightsForExerciseAndReferencedCourse} skipping setting the
         *       access rights.
         *       This problem reoccurs in {@link CodeEditorTutorAssessmentContainerComponent#handleReceivedSubmission}
         */
        this.accountService.setAccessRightsForExercise(exercise);
        this.course.set(getCourseFromExercise(exercise));
        this.hasAssessmentDueDatePassed.set(!!exercise.assessmentDueDate && dayjs(exercise.assessmentDueDate).isBefore(dayjs()));
        if (this.resultId > 0) {
            const resultForId = getSubmissionResultById(submission, this.resultId);
            // Read off the result, not off its position in the results array.
            this.correctionRound.set(resultForId?.correctionRound ?? 0);
            this.result.set(resultForId);
        } else {
            this.result.set(getLatestSubmissionResult(submission));
        }
        this.getComplaint();

        const result = this.result();
        if (result) {
            submission.results = [result];
        }
        if (result?.feedbacks) {
            this.unreferencedFeedback.set(result.feedbacks);
        } else if (result) {
            result.feedbacks = [];
        }
        if ((!result?.assessor || result?.assessor?.id === this.userId) && !result?.completionDate) {
            this.alertService.closeAll();
            this.alertService.info('artemisApp.fileUploadAssessment.messages.lock');
        }

        this.checkPermissions();
        this.calculateTotalScore();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), submission);

        this.busy.set(false);
        this.isLoading.set(false);
    }

    public addFeedback(): void {
        const feedback = new Feedback();
        this.unreferencedFeedback().push(feedback);
        this.validateAssessment();
    }

    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.unreferencedFeedback().indexOf(assessmentToDelete);
        this.unreferencedFeedback().splice(indexToDelete, 1);
        this.validateAssessment();
    }

    /**
     * Load next assessment in the same page.
     * It calls the api to load the new unassessed submission in the same page.
     * For the new submission to appear on the same page, the url has to be reloaded.
     */
    assessNext() {
        const exerciseId = this.exercise()?.id;
        if (!exerciseId) {
            this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
            return;
        }

        this.isLoading.set(true);
        this.unreferencedFeedback.set([]);
        this.fileUploadSubmissionService.getSubmissionWithoutAssessment(exerciseId, false, this.correctionRound()).subscribe({
            next: (submission?: FileUploadSubmission) => {
                this.isLoading.set(false);
                this.unassessedSubmission = submission;
                if (!submission) {
                    // there are no unassessed submissions
                    this.navigateBack();
                    this.alertService.info('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                    return;
                }

                const participationId = submission.participation?.id;
                const submissionId = submission.id;
                if (!participationId || !submissionId) {
                    this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
                    return;
                }

                const url = getLinkToSubmissionAssessment(
                    ExerciseType.FILE_UPLOAD,
                    this.courseId,
                    this.exerciseId,
                    participationId,
                    submissionId,
                    this.examId,
                    this.exerciseGroupId,
                );
                // Carry the correction round and keep the other parameters: the component reads the round from the URL,
                // so dropping it sent the next submission into the first correction round.
                void this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound() }, queryParamsHandling: 'merge' });
            },
            error: (error: HttpErrorResponse) => {
                this.isLoading.set(false);
                // the current assessment stays on the page, so here the explanation belongs in an alert rather than in
                // place of it — without this the tutor would read "You are not authorized to access this page"
                if (!alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe)) {
                    onError(this.alertService, error);
                }
            },
        });
    }

    onSaveAssessment() {
        const submissionId = this.submission()?.id;
        if (!submissionId) {
            this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
            return;
        }

        this.isLoading.set(true);
        this.fileUploadAssessmentService
            .saveAssessment(this.assessments, submissionId, this.result()?.assessmentNote?.note)
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: (result: Result) => {
                    this.result.set(result);
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.saveSuccessful');
                },
                error: (error: HttpErrorResponse) => {
                    if (alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe)) {
                        return;
                    }
                    this.alertService.closeAll();
                    this.alertService.error('artemisApp.assessment.messages.saveFailed');
                },
            });
    }

    onSubmitAssessment() {
        this.validateAssessment();
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            return;
        }

        const submissionId = this.submission()?.id;
        if (!submissionId) {
            this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
            return;
        }

        this.isLoading.set(true);
        this.fileUploadAssessmentService
            .saveAssessment(this.assessments, submissionId, this.result()?.assessmentNote?.note, true)
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: (result: Result) => {
                    this.result.set(result);
                    this.updateParticipationWithResult();
                    this.alertService.closeAll();
                    this.alertService.success('artemisApp.assessment.messages.submitSuccessful');
                },
                error: (error: HttpErrorResponse) => {
                    if (!alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe)) {
                        this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`);
                    }
                },
            });
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const submissionId = this.submission()?.id;
        if (!submissionId) {
            return;
        }

        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.isLoading.set(true);
            this.fileUploadAssessmentService
                .cancelAssessment(submissionId, this.result()?.id)
                .pipe(finalize(() => this.isLoading.set(false)))
                .subscribe(() => {
                    this.navigateBack();
                });
        }
    }

    private updateParticipationWithResult(): void {
        if (!this.submission()?.results || !this.result()) {
            return;
        }
        // Commit a new submission reference so the change propagates under zoneless OnPush.
        this.submission.update((submission) => cloneWith(submission!, { results: [this.result()!, ...(submission!.results?.slice(1) ?? [])] }));
    }

    getComplaint(): void {
        const submissionId = this.submission()?.id;
        if (!submissionId) {
            return;
        }

        this.complaintService.findBySubmissionId(submissionId).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint.set(this.complaintService.convertComplaintFromServer(res.body, this.result()));
            },
            error: (err: HttpErrorResponse) => {
                onError(this.alertService, err);
            },
        });
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise(), this.submission(), this.isTestRun());
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
    public validateAssessment(updatedFeedbacks?: Feedback[]): void {
        if (updatedFeedbacks) {
            this.unreferencedFeedback.set(updatedFeedbacks);
        }
        this.assessmentsAreValid.set(true);
        this.invalidError.set(undefined);

        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid.set(Feedback.haveCreditsAndComments(this.unreferencedFeedback()));

        this.calculateTotalScore();

        if (this.submission()) {
            this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission()!);
        }
    }

    /**
     * Calculates the total score of the current assessment.
     * This function originally checked whether the total score is negative
     * or greater than the max. score, but we decided to remove the restriction
     * and instead set the score boundaries on the server.
     */
    private calculateTotalScore() {
        const maxPoints = getTotalMaxPoints(this.exercise());
        const creditsTotalScore = this.structuredGradingCriterionService.computeTotalScore(this.assessments);
        this.totalScore.set(getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints));
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
    }

    private checkPermissions() {
        this.isAssessor.set(this.result()?.assessor?.id === this.userId);
    }

    /**
     * Boolean which determines whether the user can override a result.
     * If no exercise is loaded, for example during loading between exercises, we return false.
     * Instructors can always override a result.
     * Tutors can override their own results within the assessment due date, if there is no complaint about their assessment.
     * They cannot override a result anymore, if there is a complaint. Another tutor must handle the complaint.
     */
    get canOverride(): boolean {
        if (this.exercise()) {
            if (this.exercise()?.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            if (this.complaint() && this.isAssessor()) {
                // If there is a complaint, the original assessor cannot override the result anymore.
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            const assessmentDueDate = this.exercise()?.assessmentDueDate;
            if (assessmentDueDate) {
                isBeforeAssessmentDueDate = dayjs().isBefore(assessmentDueDate);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor() && isBeforeAssessmentDueDate;
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
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }

        const submissionId = this.submission()?.id;
        if (!submissionId) {
            this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
            assessmentAfterComplaint.onError();
            return;
        }

        const feedbacks = this.complaintService.getFeedbacksForUpdateAfterComplaint(this.assessments);
        const complaintResponse = this.complaintService.getComplaintResponseForUpdateAfterComplaint(assessmentAfterComplaint.complaintResponse);

        this.isLoading.set(true);
        this.fileUploadAssessmentService
            .updateAssessmentAfterComplaint(feedbacks, complaintResponse, submissionId, this.result()?.assessmentNote?.note)
            .pipe(finalize(() => this.isLoading.set(false)))
            .subscribe({
                next: (response) => {
                    assessmentAfterComplaint.onSuccess();
                    if (response.body) {
                        this.result.set(response.body);
                        this.updateParticipationWithResult();
                    }
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
        return !isAllowedToModifyFeedback(this.isTestRun(), this.isAssessor(), this.hasAssessmentDueDatePassed(), this.result(), this.complaint(), this.exercise());
    }

    private onError(error: string) {
        this.alertService.error(error);
    }
}
