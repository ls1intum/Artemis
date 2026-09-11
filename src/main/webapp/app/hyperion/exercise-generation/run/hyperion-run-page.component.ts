import { HyperionRunInputComponent } from './hyperion-run-input.component';
import { filter, merge } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { HYPERION_GENERATION_BLOCKER_KEY, hyperionGenerationBlocker } from 'app/hyperion/exercise-generation/hyperion-generation-support';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, linkedSignal, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonComponent,
    TumUiCardComponent,
    TumUiDialogComponent,
    TumUiInputDirective,
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
import { serverTimeSignal } from 'app/hyperion/exercise-generation/hyperion-server-time.util';
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

/** Displays generation progress, retained artifacts, and the saved result. */
@Component({
    selector: 'jhi-hyperion-run-page',
    templateUrl: './hyperion-run-page.component.html',
    styleUrl: './hyperion-run-page.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [HyperionGenerationActivityFacade, HyperionRunAnnouncerService],
    imports: [
        FormsModule,
        TumUiDialogComponent,
        TumUiInputDirective,
        ArtemisTranslatePipe,
        TranslateDirective,
        HyperionArtifactsComponent,
        HyperionRunInputComponent,
        HyperionRunHeaderComponent,
        HyperionRunOutcomeComponent,
        HyperionRunProgressComponent,
        HyperionRunUsageComponent,
        TumUiButtonComponent,
        TumUiCardComponent,
        TumUiMessageComponent,
        TumUiPanelComponent,
    ],
})
export class HyperionRunPageComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);
    private readonly translateService = inject(TranslateService);
    private readonly announcer = inject(HyperionRunAnnouncerService);
    private readonly facade = inject(HyperionGenerationActivityFacade);
    protected readonly jobId = this.facade.jobId;
    protected readonly canRevert = this.facade.canRevert;
    protected readonly reverting = this.facade.reverting;
    protected readonly reverted = this.facade.reverted;
    protected readonly confirmRevertVisible = this.facade.confirmRevertVisible;
    protected readonly revertPartialRepositories = this.facade.revertPartialRepositories;
    protected readonly undoKey = computed(() => (this.facade.effectiveRevertMode() === 'ADAPT' ? 'undoAdaptation' : 'undoGeneration'));
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

    protected readonly exercise = computed<ProgrammingExercise | undefined>(() => {
        const refreshed = this.refreshedExercise();
        return refreshed?.id === this.exerciseId() ? refreshed : (this.resolvedExercise()['programmingExercise'] as ProgrammingExercise | undefined);
    });

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
    protected readonly runInput = this.facade.input;
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

    /** The run's exercise in words: `Java · Gradle · Medium`, every part a translation key. */
    protected readonly metaLabelKeys = computed(() => {
        const exercise = this.exercise();
        return [
            exercise?.programmingLanguage ? `artemisApp.ProgrammingLanguage.${exercise.programmingLanguage}` : undefined,
            exercise?.projectType ? `artemisApp.programmingExercise.projectTypes.${exercise.projectType}` : undefined,
            exercise?.difficulty ? `artemisApp.exercise.${exercise.difficulty.toLowerCase()}` : undefined,
        ].filter((key): key is string => key !== undefined);
    });

    protected readonly startedAt = computed(() => this.events().find((event) => event.type === 'STARTED')?.timestamp);
    protected readonly endedAt = computed(() => this.terminalEvent()?.timestamp);

    /** Nothing has ever run for this exercise: no job, and no outstanding or failed status check to explain why. */
    protected readonly notStarted = computed(() => this.facade.jobId() === undefined && !this.statusLoading() && !this.statusLoadFailed());

    protected readonly cancelAvailable = computed(() => !this.terminal() && this.running() && this.ownedByCaller() && this.facade.cancellable());
    /** Whether starting a run is this deployment's and this instructor's to do at all; why the exercise may still refuse one is {@link startBlockedReason}. */
    protected readonly generationOffered = computed(() => this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION) && this.ownedByCaller());
    /** Translation key for what about the exercise prevents a run, so the start button can say it instead of vanishing. */
    protected readonly startBlockedReason = computed(() => {
        const exercise = this.exercise();
        const blocker = exercise ? hyperionGenerationBlocker(exercise, this.now()) : undefined;
        return blocker ? HYPERION_GENERATION_BLOCKER_KEY + blocker : undefined;
    });
    protected readonly runAgainAvailable = computed(() => this.generationOffered() && this.terminal() && !this.starting());
    protected readonly startAvailable = computed(() => this.generationOffered() && this.notStarted() && !this.starting());
    private readonly canStart = computed(() => (this.runAgainAvailable() || this.startAvailable()) && this.startBlockedReason() === undefined);

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

    protected readonly starting = linkedSignal({ source: this.exerciseId, computation: () => false });
    protected readonly startFailed = linkedSignal({ source: this.exerciseId, computation: () => false });
    protected readonly startDialogVisible = linkedSignal({ source: this.exerciseId, computation: () => false });
    protected readonly startPrompt = linkedSignal({ source: this.exerciseId, computation: () => '' });
    protected readonly maximumPromptLength = 8000;
    protected readonly startPromptValid = computed(() => this.startPrompt().trim().length > 0 && this.startPrompt().length <= this.maximumPromptLength);
    protected readonly adapting = computed(() => this.facade.mode() === 'ADAPT');

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

    /** Whether the run completed a save, rather than only producing working files. */
    protected readonly savedToExercise = computed(() => this.terminalEvent()?.liveExerciseChanged === true);

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

    private readonly now = serverTimeSignal();

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

        // A save or undo changes the exercise this page is describing, so re-read it.
        merge(this.facade.generationCompleted.pipe(filter((event) => event.liveExerciseChanged === true)), this.facade.generationReverted)
            .pipe(takeUntilDestroyed())
            .subscribe(() => {
                const exerciseId = this.exerciseId();
                if (exerciseId !== undefined) {
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

    protected confirmRevert(): void {
        this.facade.confirmRevert();
    }

    protected acceptRevert(): void {
        this.facade.acceptRevert();
    }

    protected dismissRevert(): void {
        this.facade.dismissRevert();
    }

    protected cancel(): void {
        this.facade.cancel();
    }

    protected retryStatus(): void {
        this.facade.retryStatus();
    }

    protected openStartDialog(): void {
        if (this.canStart()) {
            this.startDialogVisible.set(true);
        }
    }

    protected start(): void {
        const exerciseId = this.exerciseId();
        if (exerciseId === undefined || !this.startDialogVisible() || !this.startPromptValid() || !this.canStart()) {
            return;
        }
        const mode = this.facade.mode() ?? 'GENERATE';
        const courseId = this.courseId();
        const exerciseTitle = this.exercise()?.title ?? '';
        this.starting.set(true);
        this.startFailed.set(false);
        this.generationService
            .generate(exerciseId, { mode, prompt: this.startPrompt().trim() })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: ({ jobId }) => {
                    if (courseId !== undefined) {
                        this.registry.track({ jobId, exerciseId, courseId, exerciseTitle, mode });
                    }
                    if (this.exerciseId() !== exerciseId) {
                        return;
                    }
                    this.startDialogVisible.set(false);
                    this.startPrompt.set('');
                    this.starting.set(false);
                    this.facade.attachToJob(jobId, mode);
                },
                error: () => {
                    if (this.exerciseId() !== exerciseId) {
                        return;
                    }
                    this.starting.set(false);
                    this.startFailed.set(true);
                },
            });
    }
}
