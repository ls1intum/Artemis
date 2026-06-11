import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, afterRenderEffect, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { ProgressBarModule } from 'primeng/progressbar';
import { CardModule } from 'primeng/card';
import { Popover, PopoverModule } from 'primeng/popover';
import { TextareaModule } from 'primeng/textarea';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subscription, forkJoin } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    IconDefinition,
    faBan,
    faCircle,
    faCircleCheck,
    faCircleInfo,
    faCircleXmark,
    faFileCirclePlus,
    faFilePen,
    faFlagCheckered,
    faMagnifyingGlass,
    faSpinner,
    faTerminal,
    faTriangleExclamation,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';
import {
    GENERATION_PHASE_ORDER,
    GenerationFileChange,
    GenerationRepo,
    TranscriptEntry,
    parseGenerationProgress,
    parseTranscript,
} from 'app/hyperion/exercise-generation/generation-progress.model';
import {
    ExerciseGenerationEvent,
    ExerciseGenerationVerdict,
    HyperionExerciseGenerationWebsocketService,
} from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

/** The order repositories are shown in the "files changed" summary. */
const REPO_ORDER: GenerationRepo[] = ['solution', 'template', 'tests', 'other'];

/**
 * Artemis Intelligence status/result surface for an agentic whole-exercise generation run. A run is started elsewhere
 * (the create page's auto-start, or an adapt request); this card streams live progress, allows cancellation, surfaces
 * the verified outcome, and self-hides when there is no active or recent run.
 */
@Component({
    selector: 'jhi-hyperion-exercise-generation',
    templateUrl: './hyperion-exercise-generation.component.html',
    styleUrl: './hyperion-exercise-generation.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    // In compact mode the host box dissolves (display: contents in scss) so the chip participates directly in the editor toolbar's flex row instead of forcing a full-width block.
    host: { '[class.hyperion-compact]': 'compact()' },
    imports: [
        ButtonModule,
        TooltipModule,
        TagModule,
        ProgressBarModule,
        CardModule,
        PopoverModule,
        TextareaModule,
        FormsModule,
        NgTemplateOutlet,
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class HyperionExerciseGenerationComponent implements OnInit, OnDestroy {
    private generationService = inject(HyperionExerciseGenerationService);
    private websocketService = inject(HyperionExerciseGenerationWebsocketService);
    private alertService = inject(AlertService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private dialogService = inject(DialogService);
    private destroyRef = inject(DestroyRef);

    protected readonly facArtemisIntelligence = facArtemisIntelligence;
    protected readonly faBan = faBan;
    protected readonly faXmark = faXmark;
    protected readonly faCircle = faCircle;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faSpinner = faSpinner;
    /** The working phases, exposed so the compact phase list can render one row per phase. */
    protected readonly phaseOrder = GENERATION_PHASE_ORDER;

    readonly exerciseId = input.required<number>();

    /** When {@code true}, automatically starts a generation run once on load (used by the create flow's auto-start). */
    readonly autoStart = input<boolean>(false);

    /**
     * When {@code true} the card renders as a compact toolbar status chip that opens a popover with the full overview (used in the instructor code editor, which has a toolbar to
     * anchor to). When {@code false} (default) it renders inline as a full-width card (used on the read-only detail page, which has no toolbar).
     */
    readonly compact = input<boolean>(false);

    /** The instructor's "Your Requirements" brief from the create flow, used as the prompt for the auto-started from-scratch run (undefined for adapt / editor-started runs). */
    readonly autoStartPrompt = input<string | undefined>(undefined);

    /**
     * Optional router link to the code editor where a recovered draft's review comments live. When set on a NEEDS_REVIEW
     * outcome, the card offers an "open in editor" button. The editor host itself leaves this undefined (the comments are
     * already on screen), so the button is correctly absent there; the detail-page host points the instructor to the editor.
     */
    readonly reviewCommentsLink = input<unknown[] | undefined>(undefined);

    /** Emitted with {@code true} once an exercise has been generated and saved, so the parent can refresh. */
    readonly generationCompleted = output<boolean>();

    readonly running = signal<boolean>(false);
    readonly cancelling = signal<boolean>(false);
    readonly jobId = signal<string | undefined>(undefined);
    readonly progressEvents = signal<ExerciseGenerationEvent[]>([]);
    readonly finalEvent = signal<ExerciseGenerationEvent | undefined>(undefined);
    readonly showLog = signal<boolean>(false);
    readonly elapsedSeconds = signal<number>(0);

    readonly diffLoading = signal<boolean>(false);

    /** Optional free-text guidance the instructor can add before retrying, threaded into the next run as its prompt. */
    readonly retryGuidance = signal<string>('');

    readonly canCancel = computed(() => this.running() && !!this.jobId() && !this.cancelling());
    readonly succeeded = computed(() => this.finalEvent()?.type === 'DONE' && this.finalEvent()?.completionStatus === 'SUCCESS');
    /** A near-miss that was recovered: a best-effort draft was saved with review comments to resolve (distinct from a clean, verified success). */
    readonly needsReview = computed(() => this.finalEvent()?.type === 'DONE' && this.finalEvent()?.completionStatus === 'NEEDS_REVIEW');
    readonly canRetry = computed(() => !this.running() && !!this.finalEvent());
    readonly verdict = computed<ExerciseGenerationVerdict | undefined>(() => this.finalEvent()?.verdict);
    readonly hasActiveOrRecentRun = computed(() => this.running() || !!this.finalEvent());

    readonly progress = computed(() => parseGenerationProgress(this.progressEvents(), !!this.finalEvent()));
    readonly phaseKey = computed(() => this.progress().phase);

    /** The structured, renderable transcript derived from the raw progress lines (turn badge, tool chip, monospace target). */
    readonly transcriptEntries = computed<TranscriptEntry[]>(() => parseTranscript(this.progressEvents()));

    /** Index of the current phase within {@link GENERATION_PHASE_ORDER}; {@code done} keeps the last phase active so the list reads as complete. */
    readonly phaseIndex = computed(() => {
        const index = GENERATION_PHASE_ORDER.indexOf(this.phaseKey());
        return index === -1 ? GENERATION_PHASE_ORDER.length - 1 : index;
    });
    /** The chip's one-line status while running: "Generating… 2/4". The terminal label is resolved in the template from the verdict. */
    readonly chipStepParams = computed(() => ({ step: this.phaseIndex() + 1, total: GENERATION_PHASE_ORDER.length }));

    /**
     * The visual state of a phase row in the compact phase list: phases before the current one are {@code done}, the current one is {@code active} while running, the rest are
     * {@code pending}. On a terminal outcome (not running) every phase reads as done.
     *
     * @param index the phase's position in {@link GENERATION_PHASE_ORDER}
     */
    protected phaseState(index: number): 'done' | 'active' | 'pending' {
        if (!this.running()) {
            return 'done';
        }
        if (index < this.phaseIndex()) {
            return 'done';
        }
        return index === this.phaseIndex() ? 'active' : 'pending';
    }

    /** The icon for a phase row: a check when done, a spinner while active, an empty circle while pending. */
    protected phaseIcon(index: number): IconDefinition {
        const state = this.phaseState(index);
        return state === 'done' ? faCircleCheck : state === 'active' ? faSpinner : faCircle;
    }

    /** The chip icon for a terminal run: success/needs-review/error/cancelled; while running the template shows a spinner instead. */
    readonly chipIcon = computed<IconDefinition>(() => {
        if (this.succeeded()) {
            return faCircleCheck;
        }
        if (this.needsReview()) {
            return faTriangleExclamation;
        }
        if (this.finalEvent()?.type === 'ERROR') {
            return faCircleXmark;
        }
        return facArtemisIntelligence;
    });
    /** The chip's PrimeNG severity, mirroring the verdict. */
    readonly chipSeverity = computed<'success' | 'warn' | 'danger' | 'secondary'>(() => {
        if (this.running()) {
            return 'secondary';
        }
        if (this.succeeded()) {
            return 'success';
        }
        if (this.needsReview()) {
            return 'warn';
        }
        return this.finalEvent()?.type === 'ERROR' ? 'danger' : 'secondary';
    });
    /** The i18n key suffix for the chip's terminal label. */
    readonly chipResultKey = computed<string>(() => {
        if (this.succeeded()) {
            return 'chipDone';
        }
        if (this.needsReview()) {
            return 'chipNeedsReview';
        }
        if (this.finalEvent()?.type === 'ERROR') {
            return 'chipFailed';
        }
        return this.finalEvent()?.type === 'CANCELLED' ? 'chipCancelled' : 'chipPartial';
    });
    // The live caption is derived from the coarse phase, not the raw server line, so an instructor sees plain language ("Writing the solution and tests", "Checking it builds and
    // grades") instead of build-agent internals. The opt-in detail log keeps the specifics. The terminal "done" phase shows no caption.
    readonly currentStepKey = computed<string | undefined>(() => {
        switch (this.progress().phase) {
            case 'preparing':
                return 'preparing';
            case 'authoring':
                return 'authoring';
            case 'verifying':
                return 'checking';
            case 'saving':
                return 'saving';
            default:
                return undefined;
        }
    });
    readonly currentStepParams = computed<Record<string, unknown>>(() =>
        this.progress().phase === 'verifying' ? { attempt: this.progress().attempt ?? 1, total: this.progress().attemptTotal ?? 1 } : {},
    );
    readonly attempt = computed(() => this.progress().attempt);
    readonly attemptTotal = computed(() => this.progress().attemptTotal);
    readonly showAttempt = computed(() => (this.progress().attemptTotal ?? 0) > 1);
    readonly filesByRepo = computed<{ repo: GenerationRepo; files: GenerationFileChange[] }[]>(() => {
        const files = this.progress().files;
        return REPO_ORDER.map((repo) => ({ repo, files: files.filter((file) => file.repo === repo) })).filter((group) => group.files.length > 0);
    });
    readonly fileChangeCount = computed(() => this.progress().files.length);
    readonly elapsedDisplay = computed(() => {
        const total = this.elapsedSeconds();
        return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
    });

    private readonly transcriptContainer = viewChild<{ nativeElement: HTMLElement }>('transcript');
    private readonly reviewButton = viewChild<{ nativeElement: HTMLElement }>('reviewButton');
    private readonly actionButtons = viewChild<{ nativeElement: HTMLElement }>('actionButtons');
    /** The compact-mode popover and its anchor chip; used to auto-open the overview once when a run becomes active. */
    private readonly genPopover = viewChild<Popover>('genPopover');
    private readonly genChip = viewChild<{ nativeElement: HTMLElement }>('genChip');
    private autoOpenHandled = false;

    private jobSubscription?: Subscription;
    private dialogRef?: DynamicDialogRef;
    private autoStartHandled = false;
    private elapsedTimer?: ReturnType<typeof setInterval>;
    private terminalFocusHandled = false;

    constructor() {
        // Keep the transcript pinned to the latest line as events arrive, unless the user has scrolled up to read earlier output.
        afterRenderEffect(() => {
            this.progressEvents();
            const container = this.transcriptContainer()?.nativeElement;
            if (container) {
                const nearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 40;
                if (nearBottom) {
                    container.scrollTop = container.scrollHeight;
                }
            }
        });

        // On a terminal outcome, move focus once to the next action (the now-removed Cancel button would otherwise drop focus to <body>, stranding keyboard/SR users).
        afterRenderEffect(() => {
            if (!this.finalEvent() || this.terminalFocusHandled) {
                return;
            }
            this.terminalFocusHandled = true;
            const host = this.succeeded() ? this.reviewButton() : this.actionButtons();
            host?.nativeElement?.querySelector('button')?.focus();
        });

        // Compact mode: auto-open the popover once when a run first appears (active or recently terminal), so the instructor sees the progress without having to click the chip. A
        // one-shot flag (mirroring terminalFocusHandled) means a manual dismiss is respected and re-renders never reopen it.
        afterRenderEffect(() => {
            if (!this.compact() || this.autoOpenHandled || !this.hasActiveOrRecentRun()) {
                return;
            }
            const chip = this.genChip()?.nativeElement;
            const popover = this.genPopover();
            if (chip && popover) {
                this.autoOpenHandled = true;
                // Pass NO event: PrimeNG's Popover.show(event, target) calls event.stopPropagation() whenever both are truthy, so a synthetic event object (which has no such method)
                // throws. With an undefined event the guard short-circuits and the overlay still anchors to the target (this.target = target || ...).
                popover.show(undefined as unknown as Event, chip);
            }
        });
    }

    ngOnInit(): void {
        this.reattach();
    }

    /**
     * Probes {@code /status} and (re)binds this card to the latest run for the exercise, replaying the server-retained transcript.
     * This is how the card reconnects after navigation and how it picks up a run it did not itself start (an adaptation triggered from
     * a review thread or the free-adapt menu). Idempotent and safe to call mid-stream; callers must invoke it only AFTER the start
     * request has been acknowledged server-side, so the status probe sees the job.
     */
    reattach(): void {
        // Drop any prior live subscription/timer so a re-probe never leaves a stale stream attached to an old job.
        this.teardownSubscription();
        this.stopTimer();
        this.generationService
            .getStatus(this.exerciseId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (status) => {
                    if (status) {
                        this.jobId.set(status.jobId);
                        this.progressEvents.set(status.events);
                        const terminal = status.events.findLast((event) => this.isTerminal(event));
                        if (status.running) {
                            // A new run resets any earlier terminal outcome and the one-shot focus/auto-open guards so this card shows live progress again and the popover re-opens.
                            this.finalEvent.set(undefined);
                            this.terminalFocusHandled = false;
                            this.autoOpenHandled = false;
                            this.running.set(true);
                            this.startTimer();
                            this.subscribeToJob(status.jobId);
                        } else if (terminal) {
                            this.running.set(false);
                            this.finalEvent.set(terminal);
                        }
                    }
                    this.maybeAutoStart();
                },
                // A failed status probe must never block starting a new run; just start clean.
                error: () => this.maybeAutoStart(),
            });
    }

    private maybeAutoStart(): void {
        if (this.autoStartHandled || !this.autoStart()) {
            return;
        }
        this.autoStartHandled = true;
        if (this.running() || this.finalEvent()) {
            return;
        }
        // Author from the instructor's "Your Requirements" brief when the create flow provided one; otherwise the run starts from scratch with the generic from-scratch instruction.
        this.generate(this.autoStartPrompt());
    }

    generate(guidance?: string): void {
        if (this.running()) {
            return;
        }
        this.reset();
        this.running.set(true);
        this.startTimer();
        // A retry reuses the prior run's context (the persisted draft / problem statement); optional instructor guidance is threaded in as the run's prompt.
        const prompt = guidance?.trim() || undefined;
        this.generationService
            .generateExercise(this.exerciseId(), prompt)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (start) => {
                    this.jobId.set(start.jobId);
                    this.subscribeToJob(start.jobId);
                },
                error: (error: HttpErrorResponse) => {
                    this.running.set(false);
                    this.stopTimer();
                    // A 409 means a run is already in progress for this exercise; everything else is a generic start failure.
                    this.alertService.error(
                        error.status === 409 ? 'artemisApp.programmingExercise.generateExercise.alreadyRunning' : 'artemisApp.programmingExercise.generateExercise.startError',
                    );
                },
            });
    }

    /** Clears a finished run from view; the docked panel self-hides once there is no active or recent run. Only reachable on a terminal run (the header button is hidden while running). */
    dismiss(): void {
        this.reset();
    }

    cancel(): void {
        const id = this.jobId();
        if (!id || this.cancelling()) {
            return;
        }
        this.cancelling.set(true);
        this.generationService
            .cancel(this.exerciseId(), id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                error: () => {
                    this.cancelling.set(false);
                    this.alertService.error('artemisApp.programmingExercise.generateExercise.cancelError');
                },
            });
    }

    /** After a verified-success run, fetch the new template-vs-solution file contents and open the shared diff modal for review. */
    reviewChanges(): void {
        if (this.diffLoading()) {
            return;
        }
        this.diffLoading.set(true);
        forkJoin({
            templateFiles: this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.exerciseId()),
            solutionFiles: this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.exerciseId()),
        })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: async ({ templateFiles, solutionFiles }) => {
                    try {
                        const repositoryDiffInformation = await processRepositoryDiff(templateFiles ?? new Map(), solutionFiles ?? new Map());
                        this.dialogRef =
                            this.dialogService.open(GitDiffReportModalComponent, {
                                modal: true,
                                closable: false,
                                dismissableMask: false,
                                width: '90vw',
                                styleClass: GitDiffReportModalComponent.WINDOW_CLASS,
                                data: { repositoryDiffInformation, diffForTemplateAndSolution: true },
                            }) ?? undefined;
                    } catch {
                        this.alertService.error('artemisApp.programmingExercise.generateExercise.diffError');
                    } finally {
                        this.diffLoading.set(false);
                    }
                },
                error: () => {
                    this.diffLoading.set(false);
                    this.alertService.error('artemisApp.programmingExercise.generateExercise.diffError');
                },
            });
    }

    /** The icon for a transcript entry: by tool for tool calls, by category otherwise. */
    protected iconForEntry(entry: TranscriptEntry): IconDefinition {
        if (entry.kind === 'error') {
            return faTriangleExclamation;
        }
        if (entry.kind === 'verify') {
            return faCircleCheck;
        }
        if (entry.kind === 'milestone') {
            return faFlagCheckered;
        }
        switch (entry.tool) {
            case 'write_file':
                return faFileCirclePlus;
            case 'edit_file':
                return faFilePen;
            case 'read_file':
                return faMagnifyingGlass;
            case 'bash':
                return faTerminal;
            case 'submit':
                return faFlagCheckered;
            default:
                return faCircleInfo;
        }
    }

    /** Maps an agent tool name to the i18n key suffix for its short, human-readable action label (e.g. {@code write_file} → {@code create}). */
    protected toolKey(tool: string | undefined): string {
        switch (tool) {
            case 'write_file':
                return 'create';
            case 'edit_file':
                return 'edit';
            case 'read_file':
                return 'read';
            case 'bash':
                return 'run';
            case 'verify':
                return 'verify';
            case 'submit':
                return 'submit';
            default:
                return 'tool';
        }
    }

    private subscribeToJob(id: string): void {
        this.jobSubscription = this.websocketService.subscribeToJob(id).subscribe({
            next: (event) => this.handleEvent(event),
            error: () => {
                this.running.set(false);
                this.cancelling.set(false);
                this.stopTimer();
                this.alertService.error('artemisApp.programmingExercise.generateExercise.streamError');
            },
        });
    }

    private handleEvent(event: ExerciseGenerationEvent): void {
        this.progressEvents.update((events) => [...events, event]);
        if (this.isTerminal(event)) {
            this.finalEvent.set(event);
            this.running.set(false);
            this.cancelling.set(false);
            this.stopTimer();
            this.teardownSubscription();
            if (this.succeeded()) {
                this.alertService.success('artemisApp.programmingExercise.generateExercise.success');
            } else if (this.needsReview()) {
                this.alertService.info('artemisApp.programmingExercise.generateExercise.needsReview');
            }
            // A recovered draft also changed the exercise, so signal completion (review too) to let the editor reload the persisted draft and its review comments.
            this.generationCompleted.emit(this.succeeded() || this.needsReview());
        }
    }

    private isTerminal(event: ExerciseGenerationEvent): boolean {
        return event.type === 'DONE' || event.type === 'ERROR' || event.type === 'CANCELLED';
    }

    private reset(): void {
        this.teardownSubscription();
        this.stopTimer();
        this.jobId.set(undefined);
        this.cancelling.set(false);
        this.progressEvents.set([]);
        this.finalEvent.set(undefined);
        this.showLog.set(false);
        this.retryGuidance.set('');
        this.terminalFocusHandled = false;
        // Re-arm the one-shot auto-open so the popover opens again for this fresh run (a retry or a second run in the same mounted card).
        this.autoOpenHandled = false;
    }

    private startTimer(): void {
        this.stopTimer();
        // Seed from the first event's timestamp so a reconnect to a run already in flight shows the true elapsed time, not a counter restarting at zero. A fresh run has no events yet.
        this.elapsedSeconds.set(this.initialElapsedSeconds());
        this.elapsedTimer = setInterval(() => this.elapsedSeconds.update((seconds) => seconds + 1), 1000);
    }

    private initialElapsedSeconds(): number {
        const firstTimestamp = this.progressEvents()[0]?.timestamp;
        if (!firstTimestamp) {
            return 0;
        }
        const startedAt = Date.parse(firstTimestamp);
        return Number.isNaN(startedAt) ? 0 : Math.max(0, Math.floor((Date.now() - startedAt) / 1000));
    }

    private stopTimer(): void {
        if (this.elapsedTimer) {
            clearInterval(this.elapsedTimer);
            this.elapsedTimer = undefined;
        }
    }

    private teardownSubscription(): void {
        const id = this.jobId();
        if (id) {
            this.websocketService.unsubscribeFromJob(id);
        }
        this.jobSubscription?.unsubscribe();
        this.jobSubscription = undefined;
    }

    ngOnDestroy(): void {
        this.teardownSubscription();
        this.stopTimer();
        this.dialogRef?.close();
    }
}
