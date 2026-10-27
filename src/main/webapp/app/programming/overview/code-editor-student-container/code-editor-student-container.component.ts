import { Component, OnDestroy, OnInit, inject, input, signal, viewChild } from '@angular/core';
import { ExerciseSubmission } from 'app/exercise/shared/exercise-submission.interface';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { EMPTY, Observable, Subscription, of, throwError } from 'rxjs';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getManualUnreferencedFeedback, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { Course } from 'app/course/shared/entities/course.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { isManualResult as isManualResultFunction } from 'app/exercise/result/result.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseInstructionComponent } from '../../shared/instructions-render/programming-exercise-instruction.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ResultService } from 'app/exercise/result/result.service';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
    imports: [
        FaIconComponent,
        TranslateDirective,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        CodeEditorRepositoryIsLockedComponent,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructionComponent,
        AdditionalFeedbackComponent,
        RouterLink,
        TumAetUiMessageComponent,
    ],
})
export class CodeEditorStudentContainerComponent implements OnInit, OnDestroy, ExerciseSubmission {
    private domainService = inject(DomainService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private submissionPolicyService = inject(SubmissionPolicyService);
    private resultService = inject(ResultService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private route = inject(ActivatedRoute);

    readonly codeEditorContainer = viewChild(CodeEditorContainerComponent);
    readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly participationId = input<number>();
    readonly lightweight = signal(false);
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub?: Subscription;
    private newResultSub?: Subscription;
    // Template-read state written in async callbacks (route params subscription + HTTP loads) must be
    // signal-backed under zoneless change detection, otherwise the loaded editor never renders.
    readonly participation = signal<ProgrammingExerciseStudentParticipation>(undefined!);
    readonly exercise = signal<ProgrammingExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);

    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    readonly loadingParticipation = signal(false);
    readonly participationCouldNotBeFetched = signal(false);
    readonly repositoryIsLocked = signal(false);
    // Written only in a template event handler, which schedules change detection itself — may stay plain.
    showEditorInstructions = true;
    readonly latestResult = signal<Result | undefined>(undefined);
    readonly hasTutorAssessment = signal(false);
    readonly numberOfSubmissionsForSubmissionPolicy = signal<number | undefined>(undefined);
    /** The earlier submission opened from the result history, shown read-only at its commit; undefined for the latest code. */
    readonly viewedSubmission = signal<ProgrammingSubmission | undefined>(undefined);

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details, or, when the route
     * names a submission and result, with that earlier submission and result.
     */
    ngOnInit(): void {
        // switchMap drops a load that a newer navigation replaced, and each load handles its own error, so later navigations still load
        this.paramSub = this.route.params
            .pipe(
                switchMap((params) => {
                    this.loadingParticipation.set(true);
                    this.participationCouldNotBeFetched.set(false);
                    this.viewedSubmission.set(undefined);
                    this.newResultSub?.unsubscribe();
                    const participationId = this.participationId() ?? Number(params['participationId']);
                    const submissionId = params['submissionId'] === undefined ? undefined : Number(params['submissionId']);
                    const participation$ =
                        submissionId === undefined
                            ? this.loadParticipationWithLatestResult(participationId)
                            : this.loadParticipationWithSubmissionAndResult(participationId, submissionId, Number(params['resultId']));
                    return participation$.pipe(
                        tap((participationWithResults) => {
                            this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                            this.participation.set(participationWithResults);
                            const exercise = participationWithResults.exercise as ProgrammingExercise;
                            this.exercise.set(exercise);
                            this.lightweight.set(!exercise?.exerciseGroup);
                            const dueDateHasPassed = hasExerciseDueDatePassed(exercise, participationWithResults);
                            // TODO: load this information from the server in case submission policies are enabled for programming exercises
                            this.repositoryIsLocked.set(dueDateHasPassed && !isPracticeMode(participationWithResults));
                            const allResults = getAllResultsOfAllSubmissions(participationWithResults.submissions);
                            this.latestResult.set(allResults.length >= 1 ? allResults.first() : undefined);
                            this.checkForTutorAssessment(dueDateHasPassed);
                            this.course.set(getCourseFromExercise(exercise));
                            this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exercise.id!).subscribe((submissionPolicy) => {
                                if (submissionPolicy?.active) {
                                    this.exercise().submissionPolicy = submissionPolicy;
                                    this.getNumberOfSubmissionsForSubmissionPolicy();
                                }
                            });
                            if (allResults && allResults[0] && allResults[0].feedbacks) {
                                checkSubsequentFeedbackInAssessment(allResults[0].feedbacks);
                            }
                            if (submissionId === undefined) {
                                this.showNewResults(participationWithResults.id!, exercise.id!);
                            }
                        }),
                        catchError(() => {
                            this.participationCouldNotBeFetched.set(true);
                            this.loadingParticipation.set(false);
                            return EMPTY;
                        }),
                    );
                }),
            )
            .subscribe(() => this.loadingParticipation.set(false));
    }

    submitExercise(): void {
        this.codeEditorContainer()?.commit();
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.newResultSub?.unsubscribe();
    }

    /**
     * Shows each newer result of the latest code as it arrives, e.g. the tests of a newer push, so the file badges and the inline
     * feedback show that result and not the AI feedback of an earlier submission. AI feedback counts once it is done, as after a reload.
     * @param participationId the participation shown in the editor
     * @param exerciseId      the exercise of the participation
     */
    private showNewResults(participationId: number, exerciseId: number) {
        this.newResultSub = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId, true, exerciseId)
            .pipe(
                filter((result): result is Result => !!result?.submission && !(isAthenaAIResult(result) && result.successful === undefined)),
                switchMap((result) =>
                    result.feedbacks
                        ? of(result)
                        : this.resultService.getFeedbackDetailsForResult(participationId, result).pipe(map((response) => cloneWith(result, { feedbacks: response.body ?? [] }))),
                ),
            )
            .subscribe((result) => {
                this.participation.set(cloneWith(this.participation(), { submissions: [cloneWith(result.submission!, { results: [result] })] }));
                this.latestResult.set(result);
            });
    }

    /**
     * Load the participation from server with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            map((participation: ProgrammingExerciseStudentParticipation) => {
                return participation;
            }),
        );
    }

    /**
     * Load the participation from server with only the given earlier submission and result, so the editor, the instructions and the
     * feedback show that result. The result's feedback is loaded separately, since the participation comes without it.
     * @param participationId the participation the submission belongs to
     * @param submissionId    the submission to show
     * @param resultId        the result of that submission to show
     */
    loadParticipationWithSubmissionAndResult(participationId: number, submissionId: number, resultId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithAllResults(participationId).pipe(
            switchMap((participation) => {
                const submission: ProgrammingSubmission | undefined = participation.submissions?.find((candidate) => candidate.id === submissionId);
                const result = submission?.results?.find((candidate) => candidate.id === resultId);
                // Without its commit there is no code to show, and a result the student cannot see is not offered either
                if (!submission?.commitHash || !result) {
                    return throwError(() => new Error('submissionNotFound'));
                }
                const feedbacks$ = result.feedbacks
                    ? of(result.feedbacks)
                    : this.resultService.getFeedbackDetailsForResult(participationId, result).pipe(map((response) => response.body ?? []));
                return feedbacks$.pipe(
                    map((feedbacks) => {
                        result.feedbacks = feedbacks;
                        submission.results = [result];
                        participation.submissions = [submission];
                        this.viewedSubmission.set(submission);
                        return participation;
                    }),
                );
            }),
        );
    }

    checkForTutorAssessment(dueDateHasPassed: boolean) {
        let isManualResult = false;
        let hasTutorFeedback = false;
        const latestResult = this.latestResult();
        if (latestResult) {
            // latest result is the first element of results, see loadParticipationWithLatestResult
            isManualResult = isManualResultFunction(latestResult);
            if (isManualResult) {
                hasTutorFeedback = latestResult.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL);
            }
        }
        // Also check for assessment due date to never show manual feedback before the due date
        this.hasTutorAssessment.set(dueDateHasPassed && isManualResult && hasTutorFeedback);
    }

    getNumberOfSubmissionsForSubmissionPolicy() {
        const participationId = this.participation()?.id;
        if (participationId) {
            this.submissionPolicyService.getParticipationSubmissionCount(participationId).subscribe((numberOfSubmissions) => {
                this.numberOfSubmissionsForSubmissionPolicy.set(numberOfSubmissions);
            });
        }
    }

    /**
     * Check whether a latestResult exists and if, returns the unreferenced feedback of it. For an earlier submission this also includes
     * the AI feedback about a whole file: the editor only shows feedback on a line, and the result control that would open the rest is hidden.
     */
    get unreferencedFeedback(): Feedback[] {
        const latestResult = this.latestResult();
        if (latestResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(latestResult.feedbacks);
            const manualFeedback = getManualUnreferencedFeedback(latestResult.feedbacks) ?? [];
            if (!this.viewedSubmission()) {
                return manualFeedback;
            }
            return [...manualFeedback, ...latestResult.feedbacks.filter((feedback) => Feedback.isNonGradedFeedbackSuggestion(feedback) && !feedback.reference)];
        }
        return [];
    }

    receivedNewResult() {
        this.getNumberOfSubmissionsForSubmissionPolicy();
    }
}
