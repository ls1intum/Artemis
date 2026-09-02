import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent, TumUiMessageSeverity, TumUiStatusDotState } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityFacade } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { HyperionArtifactsComponent } from 'app/hyperion/exercise-generation/run/hyperion-artifacts.component';
import { HyperionRunHeaderComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-header.component';
import { HyperionRunOutcomeCheck, HyperionRunOutcomeComponent, HyperionRunOutcomeView } from 'app/hyperion/exercise-generation/run/hyperion-run-outcome.component';
import { HyperionRunProgressComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-progress.component';
import { HyperionRunUsageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-usage.component';
import { HyperionRunOutcome, runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { activityView } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { latestTerminalEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/** The status word shown next to the dot, and the dot state that goes with it. */
type RunStatus = 'queued' | 'running' | 'cancelling' | 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled' | 'unknown';

const STATUS_DOT_STATE: Record<RunStatus, TumUiStatusDotState> = {
    queued: 'queued',
    running: 'running',
    cancelling: 'running',
    saved: 'success',
    needsReview: 'warning',
    partial: 'warning',
    failed: 'error',
    cancelled: 'neutral',
    unknown: 'neutral',
};

const OUTCOME_STATUS: Record<HyperionRunOutcome, RunStatus> = {
    saved: 'saved',
    needsReview: 'needsReview',
    partial: 'partial',
    failed: 'failed',
    cancelled: 'cancelled',
};

const OUTCOME_SEVERITY: Record<HyperionRunOutcome, TumUiMessageSeverity> = {
    saved: 'success',
    needsReview: 'warn',
    partial: 'warn',
    failed: 'error',
    cancelled: 'warn',
};

/** The `generation.outcome.*` key prefix for each outcome; the run page owns this so the copy stays in one place. */
const OUTCOME_COPY: Record<HyperionRunOutcome, string> = {
    saved: 'saved',
    needsReview: 'review',
    partial: 'partial',
    failed: 'failed',
    cancelled: 'cancelled',
};

/**
 * The page a Hyperion whole-exercise generation run lives on.
 *
 * A run creates a real exercise and takes many minutes, so it has a URL: it survives a reload, it can be sent to a
 * colleague, and an instructor can walk away and come back to it. The dialog that starts a run is only for the brief.
 */
@Component({
    selector: 'jhi-hyperion-run-page',
    templateUrl: './hyperion-run-page.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [HyperionGenerationActivityFacade],
    imports: [
        TranslateDirective,
        HyperionArtifactsComponent,
        HyperionRunHeaderComponent,
        HyperionRunOutcomeComponent,
        HyperionRunProgressComponent,
        HyperionRunUsageComponent,
        TumUiButtonComponent,
        TumUiCardComponent,
        TumUiMessageComponent,
    ],
})
export class HyperionRunPageComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly facade = inject(HyperionGenerationActivityFacade);
    private readonly registry = inject(HyperionJobRegistryService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly routeParams = toSignal(this.route.params, { initialValue: this.route.snapshot.params });
    private readonly resolvedExercise = toSignal(this.route.data, { initialValue: this.route.snapshot.data });

    /** The exercise as last read from the server; re-read whenever a run reports that it changed the live exercise. */
    private readonly refreshedExercise = signal<ProgrammingExercise | undefined>(undefined);

    protected readonly exerciseId = computed(() => {
        const raw = this.routeParams()['exerciseId'];
        const parsed = Number(raw);
        return raw !== undefined && Number.isFinite(parsed) ? parsed : undefined;
    });

    protected readonly exercise = computed<ProgrammingExercise | undefined>(
        () => this.refreshedExercise() ?? (this.resolvedExercise()['programmingExercise'] as ProgrammingExercise | undefined),
    );

    /** The course the exercise belongs to; the route segment is the fallback for an exercise served without its course. */
    private readonly routeCourseId = Number(this.route.snapshot.pathFromRoot.map((snapshot) => snapshot.params['courseId']).find((id) => id !== undefined));

    protected readonly courseId = computed(() => getCourseId(this.exercise()) ?? (Number.isFinite(this.routeCourseId) ? this.routeCourseId : undefined));

    protected readonly events = this.facade.events;
    protected readonly fileChanges = this.facade.fileChanges;
    protected readonly running = this.facade.running;
    protected readonly statusLoading = this.facade.statusLoading;
    protected readonly statusLoadFailed = this.facade.statusLoadFailed;
    protected readonly ownedByCaller = this.facade.ownedByCaller;
    protected readonly cancelRequested = this.facade.cancelRequested;
    protected readonly specDocument = this.facade.specDocument;
    protected readonly repairRound = this.facade.repairRound;
    /**
     * What the run has spent, or `undefined` when nothing may honestly be shown.
     *
     * Owner-only, and absent rather than zeroed: an instructor watching someone else's run gets no spend figures at
     * all, because the server withholds them and an empty meter would read as a run that cost nothing.
     */
    protected readonly spend = this.facade.spend;

    protected readonly outcome = computed(() => runOutcome(this.events()));
    protected readonly terminal = computed(() => this.outcome() !== undefined);
    protected readonly stages = computed(() => stageStates(this.events(), this.outcome()));
    /** What the agent is doing, rendered inside the ladder under the stage that is running. */
    protected readonly activityView = computed(() => activityView(this.events(), this.outcome(), this.fileChanges()));
    /** The newest thing the server said, shown under the stage it belongs to. */
    protected readonly liveMessage = computed(() => this.events().findLast((event) => event.message)?.message);

    private readonly terminalEvent = computed(() => latestTerminalEvent(this.events()));

    protected readonly status = computed<RunStatus>(() => {
        const outcome = this.outcome();
        if (outcome) {
            return OUTCOME_STATUS[outcome];
        }
        if (this.cancelRequested()) {
            return 'cancelling';
        }
        if (this.running()) {
            return this.events().length > 0 ? 'running' : 'queued';
        }
        return this.facade.jobId() === undefined ? 'unknown' : 'queued';
    });

    protected readonly statusDotState = computed(() => STATUS_DOT_STATE[this.status()]);
    protected readonly statusLabelKey = computed(() => `artemisApp.hyperion.generation.status.${this.status()}`);

    /** The run's exercise in words: `Java · Maven · Medium`, every part a translation key. */
    protected readonly metaLabelKeys = computed(() => {
        const exercise = this.exercise();
        return [
            exercise?.programmingLanguage ? `artemisApp.ProgrammingLanguage.${exercise.programmingLanguage}` : undefined,
            exercise?.projectType ? `artemisApp.programmingExercise.projectTypes.${exercise.projectType}` : undefined,
            exercise?.difficulty ? `artemisApp.DifficultyLevel.${exercise.difficulty}` : undefined,
        ].filter((key): key is string => key !== undefined);
    });

    protected readonly startedAt = computed(() => this.events().find((event) => event.type === 'STARTED')?.timestamp);
    protected readonly endedAt = computed(() => this.terminalEvent()?.timestamp);

    /** Nothing has ever run for this exercise: no job, and no outstanding or failed status check to explain why. */
    protected readonly notStarted = computed(() => this.facade.jobId() === undefined && !this.statusLoading() && !this.statusLoadFailed());

    protected readonly cancelAvailable = computed(() => !this.terminal() && this.running() && this.ownedByCaller() && this.facade.cancellable());
    protected readonly runAgainAvailable = computed(() => this.terminal() && this.ownedByCaller() && !this.starting());

    protected readonly starting = signal(false);
    protected readonly startFailed = signal(false);

    protected readonly exerciseLink = computed(() => {
        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
        return courseId !== undefined && exerciseId !== undefined ? (['/course-management', courseId, 'programming-exercises', exerciseId] as const) : undefined;
    });

    protected readonly editorLink = computed(() => {
        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
        const participationId = this.exercise()?.templateParticipation?.id;
        return courseId !== undefined && exerciseId !== undefined && participationId !== undefined
            ? (['/course-management', courseId, 'programming-exercises', exerciseId, 'code-editor', RepositoryType.TEMPLATE, participationId] as const)
            : undefined;
    });

    /** Whether the run actually wrote its result into the exercise, which decides where the problem statement comes from. */
    private readonly savedToExercise = computed(() => this.terminalEvent()?.liveExerciseChanged === true);

    protected readonly savedProblemStatement = computed(() => (this.savedToExercise() ? this.exercise()?.problemStatement : undefined));

    protected readonly outcomeView = computed<HyperionRunOutcomeView | undefined>(() => {
        const outcome = this.outcome();
        if (!outcome) {
            return undefined;
        }
        const terminal = this.terminalEvent();
        const verdict = this.facade.verdict();
        const checks: HyperionRunOutcomeCheck[] = verdict
            ? [
                  { labelKey: `artemisApp.hyperion.generation.verdict.${verdict.solutionPassed ? 'solutionPasses' : 'solutionFails'}`, passed: verdict.solutionPassed },
                  { labelKey: `artemisApp.hyperion.generation.verdict.${verdict.templateFailed ? 'templateFails' : 'templatePasses'}`, passed: verdict.templateFailed },
                  {
                      labelKey: `artemisApp.hyperion.generation.verdict.${verdict.mechanicallyVerified ? 'consistencyPassed' : 'consistencyFailed'}`,
                      passed: verdict.mechanicallyVerified,
                  },
              ]
            : [];
        const copy = OUTCOME_COPY[outcome];
        return {
            severity: OUTCOME_SEVERITY[outcome],
            titleKey: `artemisApp.hyperion.generation.outcome.${copy}Title`,
            bodyKey: `artemisApp.hyperion.generation.outcome.${copy}Body`,
            bodyParams: { testCount: verdict?.testCount ?? 0 },
            terminationReasonKey:
                (outcome === 'failed' || outcome === 'cancelled') && terminal?.terminationReason
                    ? `artemisApp.hyperion.generation.terminationReason.${terminal.terminationReason}`
                    : undefined,
            checks,
            testCountKey: verdict ? (verdict.testCount === 1 ? 'artemisApp.hyperion.generation.verdict.oneTest' : 'artemisApp.hyperion.generation.verdict.tests') : undefined,
            testCountParams: verdict ? { count: verdict.testCount } : undefined,
            // Only the server knows whether a candidate survived: a run whose sandbox died before its work was copied
            // out has nothing to inspect, and telling the instructor otherwise sends them looking for files that are gone.
            retained: !this.savedToExercise() && this.facade.artifactsRetained(),
            nothingRetained: !this.savedToExercise() && !this.facade.artifactsRetained(),
            // The server's own prose is English and technical; it belongs behind the disclosure, never in the headline.
            serverMessages: [...(terminal?.message ? [terminal.message] : []), ...(verdict?.reasons ?? [])],
            events: this.events(),
        };
    });

    constructor() {
        this.facade.connect({ exerciseId: this.exerciseId, refreshingEditor: signal(false) });

        // Opening the run page is what "seeing" a finished run means, so the navbar badge clears here.
        effect(() => {
            const jobId = this.facade.jobId();
            if (jobId) {
                untracked(() => this.registry.markSeen(jobId));
            }
        });

        // A run that saved its result changed the exercise this page is describing, so re-read it.
        this.facade.generationCompleted.pipe(takeUntilDestroyed()).subscribe((event) => {
            const exerciseId = this.exerciseId();
            if (event.liveExerciseChanged && exerciseId !== undefined) {
                this.programmingExerciseService
                    .find(exerciseId)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe({
                        next: ({ body }) => this.refreshedExercise.set(body ?? undefined),
                        // A stale title is not worth an error banner; the artifacts panel reports what it could not load.
                        error: () => undefined,
                    });
            }
        });
    }

    protected cancel(): void {
        this.facade.cancel();
    }

    protected retryStatus(): void {
        this.facade.retryStatus();
    }

    /**
     * Starts a fresh run for the same exercise.
     *
     * The original brief is not readable through the API, so the server falls back to the exercise's own problem
     * statement (or, for an empty draft, its generic instruction). Re-running therefore repeats the attempt, it does
     * not replay the brief.
     */
    protected runAgain(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId === undefined || this.starting() || !this.runAgainAvailable()) {
            return;
        }
        const mode = this.facade.mode() ?? 'GENERATE';
        this.starting.set(true);
        this.startFailed.set(false);
        this.generationService
            .generate(exerciseId, { mode })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ jobId }) => {
                    this.starting.set(false);
                    const courseId = this.courseId();
                    if (courseId !== undefined) {
                        this.registry.track({ jobId, exerciseId, courseId, exerciseTitle: this.exercise()?.title ?? '', mode });
                    }
                    this.facade.attachToJob(jobId, mode);
                },
                error: () => {
                    this.starting.set(false);
                    this.startFailed.set(true);
                },
            });
    }
}
