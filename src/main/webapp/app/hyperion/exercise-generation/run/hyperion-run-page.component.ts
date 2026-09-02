import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonComponent,
    TumUiCardActionComponent,
    TumUiCardComponent,
    TumUiCardHeaderComponent,
    TumUiCardTitleComponent,
    TumUiMessageComponent,
    TumUiMessageSeverity,
    TumUiPanelComponent,
    TumUiStatusDotState,
} from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { getCourseId } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationActivityFacade } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { HyperionArtifactsComponent } from 'app/hyperion/exercise-generation/run/hyperion-artifacts.component';
import { HyperionRunHeaderComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-header.component';
import { HyperionRunOutcomeCheck, HyperionRunOutcomeComponent, HyperionRunOutcomeView } from 'app/hyperion/exercise-generation/run/hyperion-run-outcome.component';
import { HyperionRunProgressComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-progress.component';
import { HyperionRunUsageComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-usage.component';
import { HYPERION_STAGE_COUNT, HyperionRunOutcome, runOutcome, stagePosition, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { activityView, formatClockTime, formatElapsed, isStalled } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';
import { HyperionRunAnnouncerService } from 'app/hyperion/exercise-generation/run/hyperion-run-announcer.service';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { latestTerminalEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/** The status word shown next to the dot, and the dot state that goes with it. */
type RunStatus = 'queued' | 'running' | 'cancelling' | 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled' | 'notStarted' | 'unknown';

const STATUS_DOT_STATE: Record<RunStatus, TumUiStatusDotState> = {
    queued: 'queued',
    running: 'running',
    cancelling: 'running',
    saved: 'success',
    needsReview: 'warning',
    partial: 'warning',
    failed: 'danger',
    cancelled: 'neutral',
    notStarted: 'neutral',
    unknown: 'unknown',
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
    needsReview: 'warning',
    partial: 'warning',
    failed: 'danger',
    cancelled: 'warning',
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
 * It answers one question: *what happened to this generation, and what do I do next?* A run creates a real exercise
 * and takes many minutes, so it has a URL: it survives a reload, it can be sent to a colleague, and an instructor can
 * walk away and come back to it. The dialog that starts a run is only for the brief.
 *
 * Four ranked regions rather than five equal cards, because five containers of identical weight rank nothing:
 *
 * 0. **Header** - identity, status, the facts rail (elapsed, step, spend, files) and the actions.
 * 1. **The answer** - the ladder while the run is going, the verdict once it is over with the ladder folded to a strip.
 * 2. **What it produced** - the artifacts, always open, never auto-collapsed at the moment they become the answer.
 * 3. **Run detail** - the spend in full, as a ruled section at lower contrast rather than as a fourth card.
 *
 * Cost is made more prominent by *position and persistence*, not by size: a real figure in the header, visible in
 * every state, while the verdict keeps the top of the hierarchy. The nine states: **empty** (never run - carries the
 * action rather than directions to it), **loading**, **running**, **stalled** (see the activity area), **partial**,
 * **error** and **stale** (the last known ladder stays on screen with a retry, never a blanked page),
 * **unauthorised** (no spend column, no Cancel, and a sentence saying why), **terminal success / failure**.
 */
@Component({
    selector: 'jhi-hyperion-run-page',
    templateUrl: './hyperion-run-page.component.html',
    styleUrl: './hyperion-run-page.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [HyperionGenerationActivityFacade, HyperionRunAnnouncerService],
    imports: [
        ArtemisTranslatePipe,
        TranslateDirective,
        HyperionArtifactsComponent,
        HyperionRunHeaderComponent,
        HyperionRunOutcomeComponent,
        HyperionRunProgressComponent,
        HyperionRunUsageComponent,
        TumUiButtonComponent,
        TumUiCardActionComponent,
        TumUiCardComponent,
        TumUiCardHeaderComponent,
        TumUiCardTitleComponent,
        TumUiMessageComponent,
        TumUiPanelComponent,
    ],
})
export class HyperionRunPageComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly translateService = inject(TranslateService);
    private readonly announcer = inject(HyperionRunAnnouncerService);
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
    /** `Step 2 of 5`, from position in the fixed five stages rather than from completion, so it never walks backwards. */
    protected readonly stepPosition = computed(() => stagePosition(this.stages()));
    protected readonly stepTotal = HYPERION_STAGE_COUNT;
    protected readonly fileCount = computed(() => this.fileChanges().length);
    /**
     * Whether the page can still reach the server.
     *
     * The stall wording promises the run is "still connected", so the promise is withdrawn the moment the status check
     * itself starts failing - at which point the page reports a lost connection instead, in its own words.
     */
    protected readonly connected = computed(() => !this.statusLoadFailed());
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
        if (this.facade.jobId() !== undefined) {
            return 'queued';
        }
        // "Nothing has run" and "we could not find out" are different facts, and only the second is a problem the
        // instructor might act on. Reporting the first as `unknown` made an untouched exercise read as a broken one.
        return this.statusLoadFailed() ? 'unknown' : 'notStarted';
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
    /**
     * An empty state with no action is an apology, so the one that starts a run lives here rather than as a sentence
     * telling the instructor where to find it. Re-uses the same start path as Run again, with the same caveat: the
     * original brief is not readable through the API, so the server falls back to the exercise's own problem statement.
     */
    protected readonly startAvailable = computed(() => this.notStarted() && this.ownedByCaller() && !this.starting());

    /** How long a finished run took, for the folded stage strip. Static: a terminal run has no clock left to tick. */
    protected readonly runDuration = computed(() => {
        const startedAt = this.startedAt();
        const endedAt = this.endedAt();
        if (!startedAt || !endedAt) {
            return undefined;
        }
        const seconds = Math.max(0, Math.floor((Date.parse(endedAt) - Date.parse(startedAt)) / 1000));
        return Number.isFinite(seconds) ? formatElapsed(seconds) : undefined;
    });

    /**
     * The header of the folded stage ladder on a finished run.
     *
     * The ladder collapses to a strip rather than disappearing, because the stages are still real information about
     * what the run did - they are simply no longer the answer.
     */
    protected readonly stageStripHeader = computed(() => {
        const duration = this.runDuration();
        const total = this.stepTotal;
        return duration
            ? this.translateService.instant('artemisApp.hyperion.generation.run.stageStripWithDuration', { total, duration })
            : this.translateService.instant('artemisApp.hyperion.generation.run.stageStrip', { total });
    });

    /** When the page last heard anything at all, so stale data on screen is marked as stale rather than passed off as current. */
    protected readonly lastUpdateTime = computed(() => formatClockTime(this.events().at(-1)?.timestamp));

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
            // Diagnostic rather than a figure: the model name belongs in the log, not on the surface reporting spend.
            models: this.spend()?.models ?? [],
            events: this.events(),
        };
    });

    /** Emits once the run is over, which is what stops the stall watch from ticking for the rest of the session. */
    private readonly runEnded = new Subject<void>();
    private readonly now = serverTimeSignal(this.runEnded);

    /**
     * The one thing worth interrupting a screen-reader user for, or nothing.
     *
     * Three triggers and no others - a stage change, entering the stalled state, a terminal outcome - each with a
     * stable identity so the announcement is made on entering the state rather than repeated while it lasts. The
     * elapsed clock, the counters and the per-file events are deliberately absent: a value that updates once a second
     * is not a status message.
     */
    private readonly announcement = computed<{ id: string; message: string } | undefined>(() => {
        const outcome = this.outcome();
        if (outcome) {
            return { id: `terminal:${outcome}`, message: this.translateService.instant(`artemisApp.hyperion.generation.outcome.${OUTCOME_COPY[outcome]}Title`) };
        }
        const liveness = this.activityView().liveness;
        if (isStalled(liveness, this.now())) {
            const minutes = Math.round(liveness!.stalledAfterMs / 60_000);
            const key = this.connected() ? 'artemisApp.hyperion.generation.run.stalledAnnouncement' : 'artemisApp.hyperion.generation.run.stalledOfflineAnnouncement';
            return { id: 'stalled', message: this.translateService.instant(key, { minutes }) };
        }
        const current = this.stages().find((stage) => stage.state === 'current');
        const position = this.stepPosition();
        if (!current || position === undefined) {
            return undefined;
        }
        return {
            id: `stage:${current.key}`,
            message: this.translateService.instant('artemisApp.hyperion.generation.run.stageAnnouncement', {
                position,
                total: this.stepTotal,
                stage: this.translateService.instant(`artemisApp.hyperion.generation.stage.${current.key}`),
            }),
        };
    });

    constructor() {
        this.facade.connect({ exerciseId: this.exerciseId, refreshingEditor: signal(false) });

        effect(() => {
            if (this.terminal()) {
                this.runEnded.next();
            }
        });

        effect(() => {
            const announcement = this.announcement();
            if (announcement) {
                untracked(() => this.announcer.announce(announcement.id, announcement.message));
            }
        });

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
        if (this.runAgainAvailable()) {
            this.start();
        }
    }

    /** The empty state's own action, which is the same request as Run again on an exercise that has never run. */
    protected startFirstRun(): void {
        if (this.startAvailable()) {
            this.start();
        }
    }

    private start(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId === undefined || this.starting()) {
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
