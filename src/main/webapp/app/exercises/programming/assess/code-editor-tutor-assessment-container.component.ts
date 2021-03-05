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
import { ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { cloneDeep } from 'lodash';
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
import { switchMap, tap } from 'rxjs/operators';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { diff_match_patch } from 'diff-match-patch';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { getPositiveAndCappedTotalScore } from 'app/exercises/shared/exercise/exercise-utils';
import { createAdjustedRepositoryUrl } from '../shared/utils/programming-exercise.utils';

@Component({
    selector: 'jhi-code-editor-tutor-assessment',
    templateUrl: './code-editor-tutor-assessment-container.component.html',
})
export class CodeEditorTutorAssessmentContainerComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    readonly dmp = new diff_match_patch();
    readonly IncludedInOverallScore = IncludedInOverallScore;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    submission?: ProgrammingSubmission;
    manualResult?: Result;
    automaticResult?: Result;
    userId: number;
    // for assessment-layout
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
    adjustedRepositoryURL: string;
    correctionRound: number;

    private get course(): Course | undefined {
        return this.exercise?.course || this.exercise?.exerciseGroup?.exam?.course;
    }

    unreferencedFeedback: Feedback[] = [];
    referencedFeedback: Feedback[] = [];
    automaticFeedback: Feedback[] = [];

    isFirstAssessment = false;
    lockLimitReached = false;

    templateParticipation: TemplateProgrammingExerciseParticipation;
    templateFileSession: { [fileName: string]: string } = {};

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
        private repositoryFileService: CodeEditorRepositoryFileService,
        private programmingExerciseService: ProgrammingExerciseService,
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
            this.correctionRound = Number(queryParams.get('correction-round'));
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR]);
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;

            let participationId;
            if (!params['participationId']) {
                // Check if error is thrown and show info about error
                const response = this.route.snapshot.data.studentParticipationId;
                if (response?.error?.errorKey === 'lockedSubmissionsLimitReached') {
                    this.lockLimitReached = true;
                    return;
                } else if (response?.error?.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.onError('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                    return;
                } else if (response?.error) {
                    this.onError(response?.error?.detail || 'Not Found');
                    return;
                }
                participationId = Number(response);
                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${participationId}`);
                this.location.go(newUrl);
            } else {
                participationId = Number(params['participationId']);
            }

            this.programmingExerciseParticipationService
                .getStudentParticipationWithResultOfCorrectionRound(participationId, this.correctionRound)
                .pipe(
                    tap(
                        (participationWithResult: ProgrammingExerciseStudentParticipation) => {
                            // Set domain to make file editor work properly
                            this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResult]);
                            this.participation = participationWithResult;
                            this.manualResult = this.participation.results![0];

                            // Either submission from latest manual or automatic result
                            this.submission = this.manualResult.submission as ProgrammingSubmission;
                            this.submission.participation = this.participation;
                            this.exercise = this.participation.exercise as ProgrammingExercise;
                            this.hasAssessmentDueDatePassed = !!this.exercise!.assessmentDueDate && moment(this.exercise!.assessmentDueDate).isBefore(now());

                            this.checkPermissions();
                            this.handleFeedback();

                            if (this.manualResult && this.manualResult.hasComplaint) {
                                this.getComplaint();
                            }
                            this.adjustedRepositoryURL = createAdjustedRepositoryUrl(this.participation);
                        },
                        (error: HttpErrorResponse) => {
                            this.participationCouldNotBeFetched = true;
                            if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                                this.lockLimitReached = true;
                            }
                        },
                        () => (this.loadingParticipation = false),
                    ),
                    switchMap(() => this.programmingExerciseService.findWithTemplateAndSolutionParticipation(this.exercise.id!)),
                    tap((programmingExercise) => (this.templateParticipation = programmingExercise.body!.templateParticipation!)),
                    switchMap(() => {
                        // Get all files with content from template repository
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.templateParticipation]);
                        const observable = this.repositoryFileService.getFilesWithContent();
                        // Set back to student participation
                        this.domainService.setDomain([DomainType.PARTICIPATION, this.participation]);
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

    /**
     * Triggers when a new file was selected in the code editor. Compares the content of the file with the template (if available), calculates the diff
     * and highlights the changed/added lines or all lines if the file is not in the template.
     *
     * @param selectedFile name of the file which is currently displayed
     */
    onFileLoad(selectedFile: string): void {
        if (selectedFile && this.codeEditorContainer?.selectedFile) {
            // When the selectedFile is not part of the template, then this is a new file and all lines in code editor are highlighted
            if (!this.templateFileSession[selectedFile]) {
                const lastLine = this.codeEditorContainer.aceEditor.editorSession.getLength() - 1;
                this.codeEditorContainer.aceEditor.markerIds.push(
                    this.codeEditorContainer.aceEditor.editorSession.addMarker(new this.codeEditorContainer.aceEditor.Range(0, 0, lastLine, 1), 'diff-newLine', 'fullLine'),
                );
            } else {
                // Calculation of the diff, see: https://github.com/google/diff-match-patch/wiki/Line-or-Word-Diffs
                const diffArray = this.dmp.diff_linesToChars_(this.templateFileSession[selectedFile], this.codeEditorContainer.aceEditor.editorSession.getValue());
                const lineText1 = diffArray.chars1;
                const lineText2 = diffArray.chars2;
                const lineArray = diffArray.lineArray;
                const diffs = this.dmp.diff_main(lineText1, lineText2, false);
                this.dmp.diff_charsToLines_(diffs, lineArray);

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
                        this.codeEditorContainer.aceEditor.markerIds.push(
                            this.codeEditorContainer.aceEditor.editorSession.addMarker(
                                new this.codeEditorContainer.aceEditor.Range(firstLineToHighlight, 0, lastLineToHighlight, 1),
                                'diff-newLine',
                                'fullLine',
                            ),
                        );
                        counter += lines.length;
                    }
                });
            }
        }
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy = true;
        this.avoidCircularStructure();
        this.manualResultService.saveAssessment(this.participation.id!, this.manualResult!, undefined).subscribe(
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
        this.manualResultService.saveAssessment(this.participation.id!, this.manualResult!, true).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            (error: HttpErrorResponse) => this.onError(`error.${error?.error?.errorKey}`),
        );
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
    }

    /**
     * Go to next submission
     */
    nextSubmission() {
        this.programmingSubmissionService.getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment(this.exercise.id!, true, this.correctionRound).subscribe(
            (response: ProgrammingSubmission) => {
                const unassessedSubmission = response;
                this.router.onSameUrlNavigation = 'reload';
                // navigate to the new assessment page to trigger re-initialization of the components
                let url = `/course-management/${this.course!.id}/programming-exercises/${this.exercise.id}/code-editor/${unassessedSubmission.participation?.id}/assessment`;
                url += `?correction-round=${this.correctionRound}`;
                this.router.navigateByUrl(url, {});
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.onError('artemisApp.exerciseAssessmentDashboard.noSubmissions');
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    // the lock limit is reached
                    this.onError('artemisApp.submission.lockedSubmissionsLimitReached');
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
                this.participation.results![0] = this.manualResult = result;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
            },
            (httpErrorResponse: HttpErrorResponse) => {
                this.jhiAlertService.clear();
                const error = httpErrorResponse.error;
                if (error && error.errorKey && error.errorKey === 'complaintLock') {
                    this.jhiAlertService.error(error.message, error.params);
                } else {
                    this.onError('artemisApp.assessment.messages.updateAfterComplaintFailed');
                }
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
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined && feedbackElement.type === FeedbackType.MANUAL);
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
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = (hasReferencedFeedback && this.unreferencedFeedback.length === 0) || hasUnreferencedFeedback;
    }

    /**
     * Defines whether the inline feedback should be read only or not
     */
    readOnly() {
        return !this.isAtLeastInstructor && !!this.complaint && this.isAssessor;
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.participation.results![0] = this.manualResult = response.body!;
        this.jhiAlertService.clear();
        this.jhiAlertService.success(translationKey);
        this.saveBusy = this.submitBusy = false;
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
        const feedbacks = this.manualResult?.feedbacks || [];
        this.automaticFeedback = feedbacks.filter((feedback) => feedback.type === FeedbackType.AUTOMATIC);
        // When manual result only contains automatic feedback elements (when assessing for the first time), no manual assessment was yet saved or submitted.
        if (feedbacks.length === this.automaticFeedback.length) {
            this.isFirstAssessment = true;
        }

        this.unreferencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference != undefined && feedbackElement.type === FeedbackType.MANUAL);
        this.validateFeedback();
    }

    private setFeedbacksForManualResult() {
        this.manualResult!.feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
    }

    private createResultString(totalScore: number, maxScore: number | undefined): string {
        return `${totalScore} of ${maxScore} points`;
    }

    private setAttributesForManualResult(totalScore: number) {
        this.setFeedbacksForManualResult();
        // Manual result is always rated and has feedback
        this.manualResult!.rated = true;
        this.manualResult!.hasFeedback = true;
        // Append the automatic result string which the manual result holds with the score part, to create the manual result string
        // In the case no automatic result exists before the assessment, the resultString is undefined. In this case we just want to see the manual assessment.
        if (this.isFirstAssessment) {
            if (this.manualResult!.resultString) {
                this.manualResult!.resultString += ', ' + this.createResultString(totalScore, this.exercise.maxPoints);
            } else {
                this.manualResult!.resultString = this.createResultString(totalScore, this.exercise.maxPoints);
            }
            this.isFirstAssessment = false;
        } else {
            /* Result string has following structure e.g: "1 of 13 passed, 2 issues, 10 of 100 points" The last part of the result string has to be updated,
             * as the points the student has achieved have changed
             */
            const resultStringParts: string[] = this.manualResult!.resultString!.split(', ');
            resultStringParts[resultStringParts.length - 1] = this.createResultString(totalScore, this.exercise.maxPoints);
            this.manualResult!.resultString = resultStringParts.join(', ');
        }

        this.manualResult!.score = Math.round((totalScore / this.exercise.maxPoints!) * 100);
        // This is done to update the result string in result.component.ts
        this.manualResult = cloneDeep(this.manualResult);
    }

    private avoidCircularStructure() {
        if (this.manualResult?.participation?.results) {
            this.manualResult.participation.results = [];
        }
        if (this.manualResult?.submission?.participation?.results) {
            this.manualResult.submission.participation.results = [];
        }
    }

    private calculateTotalScore() {
        const feedbacks = [...this.referencedFeedback, ...this.unreferencedFeedback, ...this.automaticFeedback];
        const maxPoints = this.exercise.maxPoints! + (this.exercise.bonusPoints! ?? 0.0);
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

        // Set attributes of manual result
        this.setAttributesForManualResult(totalScore);
    }
}
