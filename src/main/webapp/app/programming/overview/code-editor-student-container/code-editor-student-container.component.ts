import { Component, OnDestroy, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { ExerciseSubmission } from 'app/exercise/shared/exercise-submission.interface';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { Observable, Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getManualUnreferencedFeedback, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
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
import { GeneralFeedbackComponent } from 'app/exercise/general-feedback/general-feedback.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
    // Fills the panel of the exercise page, so the editor below can take its full height
    host: { class: 'block h-full' },
    imports: [
        FaIconComponent,
        TranslateDirective,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        CodeEditorRepositoryIsLockedComponent,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructionComponent,
        GeneralFeedbackComponent,
    ],
})
export class CodeEditorStudentContainerComponent implements OnInit, OnDestroy, ExerciseSubmission {
    private domainService = inject(DomainService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private submissionPolicyService = inject(SubmissionPolicyService);
    private route = inject(ActivatedRoute);
    private participationWebsocketService = inject(ParticipationWebsocketService);

    readonly codeEditorContainer = viewChild(CodeEditorContainerComponent);
    readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly participationId = input<number>();
    readonly lightweight = signal(false);
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub?: Subscription;
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
    /** The latest successful preliminary AI feedback on the latest submission, shown in the editor next to the latest result. */
    readonly athenaResult = signal<Result | undefined>(undefined);
    /** The feedback shown in the editor: that of the latest result and of the preliminary AI feedback. */
    readonly editorFeedback = computed<Feedback[]>(() => [...(this.latestResult()?.feedbacks ?? []), ...(this.athenaResult()?.feedbacks ?? [])]);
    private latestSubmissionId?: number;
    private resultSubscription?: Subscription;
    readonly hasTutorAssessment = signal(false);
    readonly numberOfSubmissionsForSubmissionPolicy = signal<number | undefined>(undefined);

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe((params) => {
            this.loadingParticipation.set(true);
            this.participationCouldNotBeFetched.set(false);
            const participationId = this.participationId() ?? Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
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
                        // The server sends the latest result and, separately, the latest preliminary AI feedback of the latest submission
                        this.latestResult.set(allResults.find((result) => !isAthenaAIResult(result)));
                        this.athenaResult.set(allResults.find((result) => isAthenaAIResult(result) && result.successful === true));
                        this.latestSubmissionId = participationWithResults.submissions?.[0]?.id;
                        this.subscribeForNewResults(participationWithResults.id!, exercise.id);
                        this.checkForTutorAssessment(dueDateHasPassed);
                        this.course.set(getCourseFromExercise(exercise));
                        this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exercise.id!).subscribe((submissionPolicy) => {
                            if (submissionPolicy?.active) {
                                this.exercise().submissionPolicy = submissionPolicy;
                                this.getNumberOfSubmissionsForSubmissionPolicy();
                            }
                        });
                        const latestFeedbacks = this.latestResult()?.feedbacks;
                        if (latestFeedbacks) {
                            checkSubsequentFeedbackInAssessment(latestFeedbacks);
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.loadingParticipation.set(false);
                    },
                    error: () => {
                        this.participationCouldNotBeFetched.set(true);
                        this.loadingParticipation.set(false);
                    },
                });
        });
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
        this.resultSubscription?.unsubscribe();
    }

    /**
     * Keeps the feedback in the editor current: the participation is loaded once, but a build result and the preliminary AI
     * feedback a student requests both arrive over the websocket, in either order.
     */
    private subscribeForNewResults(participationId: number, exerciseId?: number) {
        this.resultSubscription?.unsubscribe();
        this.resultSubscription = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(participationId, true, exerciseId)
            .pipe(filter((result): result is Result => !!result))
            .subscribe((result) => this.receiveResult(result));
    }

    private receiveResult(result: Result) {
        const submissionId = result.submission?.id;
        if (submissionId === undefined || (this.latestSubmissionId !== undefined && submissionId < this.latestSubmissionId)) {
            return;
        }
        if (this.latestSubmissionId === undefined || submissionId > this.latestSubmissionId) {
            // New code was submitted: the AI feedback refers to lines of the previous version
            this.latestSubmissionId = submissionId;
            this.athenaResult.set(undefined);
        }
        if (isAthenaAIResult(result)) {
            if (result.successful === true) {
                this.athenaResult.set(result);
            }
        } else if (result.id !== undefined && result.id > (this.latestResult()?.id ?? -1)) {
            this.latestResult.set(result);
        }
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
     * Returns the unreferenced feedback of the latest result, followed by the preliminary AI feedback that refers to no code line
     */
    get unreferencedFeedback(): Feedback[] {
        const latestResult = this.latestResult();
        const aiFeedback = this.athenaResult()?.feedbacks?.filter((feedback) => !feedback.reference) ?? [];
        if (latestResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(latestResult.feedbacks);
            return [...(getManualUnreferencedFeedback(latestResult.feedbacks) ?? []), ...aiFeedback];
        }
        return aiFeedback;
    }

    receivedNewResult() {
        this.getNumberOfSubmissionsForSubmissionPolicy();
    }
}
