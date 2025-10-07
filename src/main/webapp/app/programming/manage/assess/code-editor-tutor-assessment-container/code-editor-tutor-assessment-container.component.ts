import { Component, EventEmitter, HostListener, Input, OnDestroy, OnInit, Output, ViewChild, inject } from '@angular/core';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { Observable, Subscription, firstValueFrom } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, CanDeactivateFn, Router, RouterLink } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Location, NgTemplateOutlet } from '@angular/common';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { assessmentNavigateBack } from 'app/shared/util/navigate-back.util';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { switchMap, tap } from 'rxjs/operators';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment, getLocalRepositoryLink } from 'app/shared/util/navigation.utils';
import { getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { isAllowedToModifyFeedback } from 'app/assessment/manage/services/assessment.service';
import { breakCircularResultBackReferences } from 'app/exercise/result/result.utils';
import { faExternalLink, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { cloneDeep } from 'lodash-es';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { FeedbackSuggestionsPendingConfirmationDialogComponent } from 'app/exercise/feedback/feedback-suggestions-pending-confirmation-dialog/feedback-suggestions-pending-confirmation-dialog.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ProgrammingAssessmentRepoExportButtonComponent } from '../repo-export/export-button/programming-assessment-repo-export-button.component';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';

@Component({
    selector: 'jhi-code-editor-tutor-assessment',
    templateUrl: './code-editor-tutor-assessment-container.component.html',
    imports: [
        FaIconComponent,
        TranslateDirective,
        AssessmentLayoutComponent,
        NgTemplateOutlet,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        RouterLink,
        ProgrammingAssessmentRepoExportButtonComponent,
        ResultComponent,
        AssessmentInstructionsComponent,
        UnreferencedFeedbackComponent,
    ],
})
export class CodeEditorTutorAssessmentContainerComponent implements OnInit, OnDestroy {
    private manualResultService = inject(ProgrammingAssessmentManualResultService);
    private router = inject(Router);
    private location = inject(Location);
    private accountService = inject(AccountService);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private domainService = inject(DomainService);
    private complaintService = inject(ComplaintService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private repositoryFileService = inject(CodeEditorRepositoryFileService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modalService = inject(NgbModal);
    private athenaService = inject(AthenaService);

    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    readonly diffMatchPatch = new DiffMatchPatch();
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly getCourseFromExercise = getCourseFromExercise;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    submission?: ProgrammingSubmission;
    manualResult?: Result;
    userId: number;
    // for assessment-layout
    isTestRun = false;
    saveBusy = false;
    submitBusy = false;
    cancelBusy = false;
    nextSubmissionBusy = false;
    isAssessor = false;
    assessmentsAreValid = false;
    complaint: Complaint;
    private cancelConfirmationText: string;
    private acceptComplaintWithoutMoreScoreText: string;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;
    showEditorInstructions = true;
    hasAssessmentDueDatePassed: boolean;
    correctionRound: number;
    courseId: number;
    examId = 0;
    exerciseId: number;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    localRepositoryLink: string[];
    loadingInitialSubmission = true;
    highlightDifferences = false;

    isAtLeastEditor = false;

    unreferencedFeedback: Feedback[] = [];
    referencedFeedback: Feedback[] = [];
    automaticFeedback: Feedback[] = [];
    feedbackSuggestions: Feedback[] = []; // all pending Athena feedback suggestions (neither accepted nor rejected yet)
    totalScoreBeforeAssessment: number;

    isFirstAssessment = false;
    lockLimitReached = false;

    templateParticipation: TemplateProgrammingExerciseParticipation;
    templateFileSession: { [fileName: string]: string } = {};

    hasPendingChanges = false;

    // listener, will get notified upon loading of feedback
    @Output() onFeedbackLoaded = new EventEmitter();
    // function override, if set will be executed instead of going to the next submission page
    @Input() overrideNextSubmission?: (submissionId: number) => any = undefined;

    // Icons
    faTimesCircle = faTimesCircle;
    faExternalLink = faExternalLink;

    /**
     * Get all feedback suggestions without a reference. They will be shown in cards below the build output.
     */
    get unreferencedFeedbackSuggestions() {
        return this.feedbackSuggestions.filter((feedback) => !feedback.reference);
    }

    constructor() {
        const translateService = inject(TranslateService);

        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
        translateService.get('artemisApp.assessment.messages.acceptComplaintWithoutMoreScore').subscribe((text) => (this.acceptComplaintWithoutMoreScoreText = text));
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation id with the latest result and result details.
     */
    async ngOnInit(): Promise<void> {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun = queryParams.get('testRun') === 'true';
            this.correctionRound = Number(queryParams.get('correction-round'));
        });
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;

            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
            const examId = params['examId'];
            if (examId) {
                this.examId = Number(examId);
                this.exerciseGroupId = Number(params['exerciseGroupId']);
            }

            this.exerciseDashboardLink = getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun);

            const submissionId = params['submissionId'];
            const submissionObservable = submissionId === 'new' ? this.loadRandomSubmission(this.exerciseId) : this.loadSubmission(Number(submissionId));
            submissionObservable
                .pipe(
                    tap({
                        next: async (submission?: ProgrammingSubmission) => {
                            await this.onSubmissionReceived(submissionId, submission);
                        },
                        error: (error: HttpErrorResponse) => {
                            this.handleErrorResponse(error);
                        },
                        complete: () => (this.loadingParticipation = false),
                    }),
                    // The following is needed for highlighting changed code lines
                    switchMap(() => this.programmingExerciseService.findWithTemplateAndSolutionParticipation(this.exercise.id!, false, true)),
                    tap((response) => {
                        const programmingExercise = response.body!;
                        this.templateParticipation = programmingExercise.templateParticipation!;
                        this.exercise.gradingCriteria = programmingExercise.gradingCriteria;
                        this.isAtLeastEditor = !!this.exercise.isAtLeastEditor;
                    }),
                    switchMap(() => {
                        // Get all files with content from template repository
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.templateParticipation]);
                        const observable = this.repositoryFileService.getFilesWithContent();
                        // Set back to student participation
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.participation]);
                        this.localRepositoryLink = getLocalRepositoryLink(
                            this.courseId,
                            this.exerciseId,
                            RepositoryType.USER,
                            this.participation.id!,
                            this.exerciseGroupId,
                            this.examId,
                        );
                        return observable;
                    }),
                    tap((templateFilesObj) => {
                        if (templateFilesObj) {
                            this.templateFileSession = templateFilesObj;
                        }
                    }),
                )
                .subscribe();
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

    private async onSubmissionReceived(submissionId: string, submission?: ProgrammingSubmission) {
        if (!submission) {
            // there are no unassessed submissions
            this.submission = submission;
            return;
        }

        // validate feedback here already so that overrides are possible for assessment note changes
        // without touching the feedbacks
        await this.handleReceivedSubmission(submission).then(() => this.validateFeedback());
        if (submissionId === 'new') {
            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
            this.location.go(newUrl);
        }
    }

    @HostListener('window:beforeunload', ['$event'])
    handleBeforeUnload(event: BeforeUnloadEvent) {
        if (this.hasPendingChanges && this.submission !== undefined) {
            // Required to trigger the native prompt in modern browsers
            event.preventDefault();
        }
    }

    private loadRandomSubmission(exerciseId: number): Observable<ProgrammingSubmission | undefined> {
        return this.programmingSubmissionService.getSubmissionWithoutAssessment(exerciseId, true, this.correctionRound);
    }

    private loadSubmission(submissionId: number): Observable<ProgrammingSubmission> {
        return this.programmingSubmissionService.lockAndGetProgrammingSubmissionParticipation(submissionId, this.correctionRound);
    }

    private async handleReceivedSubmission(submission: ProgrammingSubmission): Promise<void> {
        this.loadingInitialSubmission = false;

        // Set domain to correctly fetch data
        this.domainService.setDomain([DomainType.PARTICIPATION, submission.participation!]);
        this.submission = submission;
        this.manualResult = getLatestSubmissionResult(this.submission);
        if (!this.manualResult?.submission) {
            this.manualResult!.submission = this.submission;
        }
        this.participation = submission.participation!;
        this.participation.submissions = [this.submission];
        this.exercise = this.participation.exercise as ProgrammingExercise;
        /**
         * CARE: Setting access rights for exercises should not happen this way and is a workaround.
         *       The access rights should always be set when loading the exercise/course in the service!
         * Problem: For a reason, which I do not understand, the exercise is undefined when the exercise is loaded
         *       leading to {@link AccountService#setAccessRightsForExerciseAndReferencedCourse} skipping setting the
         *       access rights.
         *       This problem reoccurs in {@link FileUploadAssessmentComponent#initializePropertiesFromSubmission}
         */
        this.accountService.setAccessRightsForExercise(this.exercise);
        this.hasAssessmentDueDatePassed = !!this.exercise?.assessmentDueDate && dayjs(this.exercise.assessmentDueDate).isBefore(dayjs());

        this.checkPermissions();
        this.handleFeedback();
        this.getComplaint();
        this.calculateTotalScore();
        // Only load suggestions for new assessments, they don't make sense later.
        // The assessment is new if it only contains automatic feedback.
        if ((this.manualResult?.feedbacks?.length ?? 0) === this.automaticFeedback.length) {
            await this.loadFeedbackSuggestions();
        }
    }

    private handleErrorResponse(error: HttpErrorResponse): void {
        this.loadingInitialSubmission = false;
        this.participationCouldNotBeFetched = true;
        if (error?.error?.errorKey === 'lockedSubmissionsLimitReached') {
            this.lockLimitReached = true;
        } else if (error?.error) {
            this.onError(error?.error?.detail || 'Not Found');
        }
    }

    /**
     * Load the feedback suggestions for the current submission from Athena.
     */
    private async loadFeedbackSuggestions(): Promise<void> {
        this.feedbackSuggestions = (await firstValueFrom(this.athenaService.getProgrammingFeedbackSuggestions(this.exercise, this.submission!.id!))) ?? [];
        const allFeedback = [...this.referencedFeedback, ...this.unreferencedFeedback]; // pre-compute to not have to do this in the loop
        // Don't show feedback suggestions that have the same description and reference - probably it is coming from an earlier suggestion anyway
        this.feedbackSuggestions = this.feedbackSuggestions.filter((suggestion) =>
            allFeedback.every((feedback) => feedback.detailText !== suggestion.detailText || feedback.reference !== suggestion.reference),
        );
    }

    /**
     * For a file, computes the diff between the template and the submission currently being viewed, then highlights changed or edited lines in the editor.
     * If the file did not exist in the template, all lines will be highlighted.
     * @param selectedFile The file that has been selected in the editor.
     */
    highlightChangedLines(selectedFile: string) {
        if (selectedFile && this.codeEditorContainer?.selectedFile) {
            if (!this.templateFileSession[selectedFile]) {
                const lastLine = this.codeEditorContainer.getNumberOfLines() - 1;
                this.highlightLines(0, lastLine);
            } else {
                // Calculation of the diff, see: https://github.com/google/diff-match-patch/wiki/Line-or-Word-Diffs
                const diffArray = this.diffMatchPatch.diff_linesToChars(this.templateFileSession[selectedFile], this.codeEditorContainer.getText());
                const lineText1 = diffArray.chars1;
                const lineText2 = diffArray.chars2;
                const lineArray = diffArray.lineArray;
                const diffs = this.diffMatchPatch.diff_main(lineText1, lineText2, false);
                this.diffMatchPatch.diff_charsToLines(diffs, lineArray);

                // Setup counter to know on which range to highlight in the code editor
                let counter = 0;
                diffs.forEach((diffElement) => {
                    // No changes
                    if (diffElement[0] === 0) {
                        const lines = diffElement[1].split(/\r?\n/);
                        counter += lines.length - 1;
                    }
                    // Newly added
                    if (diffElement[0] === 1) {
                        const lines = diffElement[1].split(/\r?\n/).filter(Boolean);
                        const firstLineToHighlight = counter;
                        const lastLineToHighlight = counter + lines.length - 1;
                        this.highlightLines(firstLineToHighlight, lastLineToHighlight);
                        counter += lines.length;
                    }
                });
            }
        }
    }

    private highlightLines(firstLine: number, lastLine: number) {
        // We add 1 to make the lines 1-based.
        this.codeEditorContainer.highlightLines(firstLine + 1, lastLine + 1);
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy = true;
        this.handleSaveOrSubmit(undefined, 'artemisApp.textAssessment.saveSuccessful');
    }

    /**
     * Show confirmation dialog for discarding suggestions before submitting (if there are any)
     * @return true if the user confirmed the discard (=> continue to submit), false otherwise
     */
    async discardPendingSubmissionsWithConfirmation(): Promise<boolean> {
        if (this.feedbackSuggestions.length > 0) {
            const modalRef = this.modalService.open(FeedbackSuggestionsPendingConfirmationDialogComponent, { size: 'lg', backdrop: 'static', animation: true });
            const suggestionsDiscardConfirmed: boolean = await firstValueFrom(modalRef.closed);
            if (!suggestionsDiscardConfirmed) {
                return false;
            }
            this.feedbackSuggestions = []; // Discard all pending suggestions
        }
        return true;
    }

    /**
     * Submit the assessment
     */
    async submit(): Promise<void> {
        if (!(await this.discardPendingSubmissionsWithConfirmation())) {
            return;
        }
        this.submitBusy = true;
        this.handleSaveOrSubmit(true, 'artemisApp.textAssessment.submitSuccessful');
    }

    /**
     * Shared functionality for save and submit
     *
     * @param submit true if the user submits, undefined if the user saves
     * @param translationKey key for the alert to be shown on success
     */
    private handleSaveOrSubmit(submit: boolean | undefined, translationKey: string) {
        this.avoidCircularStructure();
        this.manualResultService.saveAssessment(this.participation.id!, this.manualResult!, submit).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, translationKey),
            error: (error: HttpErrorResponse) => this.onError(`error.${error?.error?.errorKey}`),
        });
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        this.cancelBusy = true;
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel && this.exercise && this.submission) {
            this.manualResultService.cancelAssessment(this.submission.id!).subscribe(() => this.navigateBack());
        }
        this.cancelBusy = false;
        this.hasPendingChanges = false;
    }

    /**
     * Go to next submission
     */
    nextSubmission() {
        this.loadingParticipation = true;
        this.submission = undefined;
        this.programmingSubmissionService.getSubmissionWithoutAssessment(this.exercise.id!, true, this.correctionRound).subscribe({
            next: (response?: ProgrammingSubmission) => {
                this.loadingParticipation = false;

                // there are no unassessed submissions
                if (!response) {
                    this.submission = undefined;
                    return;
                }

                // if override set, skip navigation
                if (this.overrideNextSubmission) {
                    this.overrideNextSubmission(response.id!);
                    return;
                }

                const url = getLinkToSubmissionAssessment(
                    ExerciseType.PROGRAMMING,
                    this.courseId,
                    this.exerciseId,
                    response.participation?.id,
                    response.id!,
                    this.examId,
                    this.exerciseGroupId,
                    undefined,
                );
                this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } });
            },
            error: (error: HttpErrorResponse) => {
                this.loadingParticipation = false;
                if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    // the lock limit is reached
                    this.onError('artemisApp.submission.lockedSubmissionsLimitReached');
                } else {
                    this.onError(error?.message);
                }
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
            this.alertService.error('artemisApp.programmingAssessment.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }
        if (!this.checkFeedbackChangeForAcceptedComplaint(assessmentAfterComplaint)) {
            assessmentAfterComplaint.onError();
            return;
        }

        this.setFeedbacksForManualResult();
        this.manualResultService
            .updateAfterComplaint(this.manualResult!.feedbacks!, assessmentAfterComplaint.complaintResponse, this.submission!.id!, this.manualResult!.assessmentNote?.note)
            .subscribe({
                next: (result: Result) => {
                    assessmentAfterComplaint.onSuccess();
                    this!.submission!.results![0] = this.manualResult = result;
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
                        this.onError('artemisApp.assessment.messages.updateAfterComplaintFailed');
                    }
                },
            });
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
                isBeforeAssessmentDueDate = dayjs().isBefore(this.exercise.assessmentDueDate);
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
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined && feedbackElement.type === FeedbackType.MANUAL);
        this.validateFeedback();
        this.hasPendingChanges = true;
    }

    /**
     * Remove a feedback suggestion because it was accepted or discarded.
     * The actual feedback creation when accepting happens in code-editor-monaco-component/unreferenced-feedback because they have full control over the suggestion cards.
     * @param feedback Feedback suggestion that is removed
     */
    removeSuggestion(feedback: Feedback) {
        this.feedbackSuggestions = this.feedbackSuggestions.filter((feedbackSuggestion) => feedbackSuggestion !== feedback);
        this.hasPendingChanges = true;
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(error);
        this.saveBusy = this.cancelBusy = this.submitBusy = this.nextSubmissionBusy = false;
    }

    /**
     *  Validate the feedback of the assessment with the guarantee that it has changed.
     */
    validateUpdatedFeedback(): void {
        this.validateFeedback();
        this.hasPendingChanges = true;
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.calculateTotalScore();
        if (this.exercise.allowComplaintsForAutomaticAssessments) {
            // We don't need manual feedback here
            this.assessmentsAreValid = true;
            return;
        }
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = (hasReferencedFeedback && this.unreferencedFeedback.length === 0) || hasUnreferencedFeedback;
    }

    /**
     * Defines whether the inline feedback should be read only or not
     */
    readOnly() {
        return !isAllowedToModifyFeedback(this.isTestRun, this.isAssessor, this.hasAssessmentDueDatePassed, this.manualResult, this.complaint, this.exercise);
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.submission!.results![0] = this.manualResult = response.body!;
        this.alertService.closeAll();
        this.alertService.success(translationKey);
        this.saveBusy = this.submitBusy = false;
        this.checkPermissions();
        this.hasPendingChanges = false;
    }

    /**
     * Checks if there is a manual result and the user is the assessor. If there is no manual result, then the user is the assessor.
     * Checks if the user is at least instructor in course.
     */
    private checkPermissions() {
        if (this.manualResult?.assessor) {
            this.isAssessor = this.manualResult.assessor.id === this.userId;
        } else {
            this.isAssessor = true;
        }
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
            error: (err: HttpErrorResponse) => {
                this.onError(err?.message);
            },
        });
    }

    private handleFeedback(): void {
        const feedbacks = this.manualResult?.feedbacks ?? [];
        this.totalScoreBeforeAssessment = this.calculateTotalScoreOfFeedbacks(feedbacks);
        this.automaticFeedback = feedbacks.filter((feedback) => feedback.type === FeedbackType.AUTOMATIC);
        // When manual result only contains automatic feedback elements (when assessing for the first time), no manual assessment was yet saved or submitted.
        if (feedbacks.length === this.automaticFeedback.length) {
            this.isFirstAssessment = true;
        }

        this.unreferencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined && feedbackElement.type === FeedbackType.MANUAL);
        this.onFeedbackLoaded.emit();
    }

    checkFeedbackChangeForAcceptedComplaint(assessmentAfterComplaint: AssessmentAfterComplaint) {
        if (!assessmentAfterComplaint.complaintResponse.complaint?.accepted) {
            return true;
        }
        const allNewFeedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        const newTotalScore = this.calculateTotalScoreOfFeedbacks(allNewFeedbacks);
        if (this.totalScoreBeforeAssessment >= newTotalScore) {
            return window.confirm(this.acceptComplaintWithoutMoreScoreText);
        }
        return true;
    }

    private setFeedbacksForManualResult() {
        this.manualResult!.feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
    }

    private setAttributesForManualResult(totalScore: number) {
        this.setFeedbacksForManualResult();
        // Manual result is always rated and has feedback
        this.manualResult!.rated = true;
        this.isFirstAssessment = false;

        this.manualResult!.score = (totalScore / this.exercise.maxPoints!) * 100;
        // This is done to update the result string in result.component.ts
        this.manualResult = cloneDeep(this.manualResult);
    }

    private avoidCircularStructure() {
        if (this.manualResult) {
            breakCircularResultBackReferences(this.manualResult);
        }
    }

    private calculateTotalScore() {
        const feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        const totalScore = this.calculateTotalScoreOfFeedbacks(feedbacks);
        // Set attributes of manual result
        this.setAttributesForManualResult(totalScore);
    }

    private calculateTotalScoreOfFeedbacks(feedbacks: Feedback[]): number {
        const maxPoints = getTotalMaxPoints(this.exercise);
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
        totalScore = getPositiveAndCappedTotalScore(totalScore, maxPoints);

        return totalScore;
    }
}

export const canLeaveCodeEditorTutorAssessmentContainer: CanDeactivateFn<CodeEditorTutorAssessmentContainerComponent> = (component) => {
    if (component.hasPendingChanges && component.submission !== undefined) {
        const translate = inject(TranslateService);
        return window.confirm(translate.instant('artemisApp.programmingAssessment.confirmLeave'));
    }
    return true;
};
