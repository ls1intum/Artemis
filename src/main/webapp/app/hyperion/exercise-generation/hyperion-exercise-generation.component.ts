import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, OnInit, afterRenderEffect, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ProgressBarModule } from 'primeng/progressbar';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subscription, forkJoin } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';
import { GenerationFileChange, GenerationRepo, parseGenerationProgress } from 'app/hyperion/exercise-generation/generation-progress.model';
import {
    ExerciseGenerationEvent,
    ExerciseGenerationVerdict,
    HyperionExerciseGenerationWebsocketService,
} from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

/** A short, realistic example brief offered as a one-click starting point for the instructions field. */
interface GenerationExample {
    labelKey: string;
    promptKey: string;
}

/** The order repositories are shown in the "files changed" summary. */
const REPO_ORDER: GenerationRepo[] = ['solution', 'template', 'tests', 'other'];

/** Instructor-facing control for agentic whole-exercise generation and adaptation: starts a run, streams live progress, allows cancellation, and reports the outcome. */
@Component({
    selector: 'jhi-hyperion-exercise-generation',
    templateUrl: './hyperion-exercise-generation.component.html',
    styleUrl: './hyperion-exercise-generation.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ButtonModule, TagModule, TextareaModule, ProgressBarModule, TranslateDirective, ArtemisTranslatePipe],
})
export class HyperionExerciseGenerationComponent implements OnInit, OnDestroy {
    private generationService = inject(HyperionExerciseGenerationService);
    private websocketService = inject(HyperionExerciseGenerationWebsocketService);
    private alertService = inject(AlertService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private dialogService = inject(DialogService);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    /** The exercise to generate or adapt. */
    readonly exerciseId = input.required<number>();

    /** When {@code true}, automatically starts a generation run once on load (used by the create flow's "Generate with AI"). */
    readonly autoStart = input<boolean>(false);

    /** Emitted with {@code true} once an exercise has been generated and saved, so the parent can refresh. */
    readonly generationCompleted = output<boolean>();

    readonly prompt = signal<string>('');
    readonly running = signal<boolean>(false);
    readonly cancelling = signal<boolean>(false);
    readonly jobId = signal<string | undefined>(undefined);
    readonly progressEvents = signal<ExerciseGenerationEvent[]>([]);
    readonly finalEvent = signal<ExerciseGenerationEvent | undefined>(undefined);
    /** Whether the raw agent log (the verbatim transcript) is expanded; the structured progress view is shown by default. */
    readonly showLog = signal<boolean>(false);
    /** Seconds elapsed since the current run started streaming, for the live "elapsed" caption. */
    readonly elapsedSeconds = signal<number>(0);

    readonly diffLoading = signal<boolean>(false);

    readonly canCancel = computed(() => this.running() && !!this.jobId() && !this.cancelling());
    readonly succeeded = computed(() => this.finalEvent()?.type === 'DONE' && this.finalEvent()?.completionStatus === 'SUCCESS');
    /** A near-miss that was recovered: a best-effort draft was saved with review comments to resolve (distinct from a clean, verified success). */
    readonly needsReview = computed(() => this.finalEvent()?.type === 'DONE' && this.finalEvent()?.completionStatus === 'NEEDS_REVIEW');
    /** Once a run has finished (whatever the outcome), the Generate button doubles as "try again". */
    readonly canRetry = computed(() => !this.running() && !!this.finalEvent());
    /** The structured verification verdict on the terminal event, if any, for the scannable result chips. */
    readonly verdict = computed<ExerciseGenerationVerdict | undefined>(() => this.finalEvent()?.verdict);

    /** The structured, human-friendly view of the run derived from the raw transcript (current phase, attempt, files changed). */
    readonly progress = computed(() => parseGenerationProgress(this.progressEvents(), !!this.finalEvent()));
    readonly phaseKey = computed(() => this.progress().phase);
    readonly currentStep = computed(() => this.progress().currentStep);
    readonly attempt = computed(() => this.progress().attempt);
    readonly attemptTotal = computed(() => this.progress().attemptTotal);
    /** Whether to show the "Attempt N of M" chip (only once more than one attempt is in play). */
    readonly showAttempt = computed(() => (this.progress().attemptTotal ?? 0) > 1);
    /** The files the agent created or edited, grouped by repository in a stable order, for the "files changed" summary. */
    readonly filesByRepo = computed<{ repo: GenerationRepo; files: GenerationFileChange[] }[]>(() => {
        const files = this.progress().files;
        return REPO_ORDER.map((repo) => ({ repo, files: files.filter((file) => file.repo === repo) })).filter((group) => group.files.length > 0);
    });
    readonly fileChangeCount = computed(() => this.progress().files.length);
    /** Elapsed time formatted as m:ss for display. */
    readonly elapsedDisplay = computed(() => {
        const total = this.elapsedSeconds();
        return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
    });

    /** Realistic, one-click example briefs to fill the instructions field. */
    readonly examples: GenerationExample[] = [
        { labelKey: 'example1Label', promptKey: 'example1Prompt' },
        { labelKey: 'example2Label', promptKey: 'example2Prompt' },
        { labelKey: 'example3Label', promptKey: 'example3Prompt' },
    ];

    private readonly transcriptContainer = viewChild<{ nativeElement: HTMLElement }>('transcript');
    private readonly reviewButton = viewChild<{ nativeElement: HTMLElement }>('reviewButton');
    private readonly actionButtons = viewChild<{ nativeElement: HTMLElement }>('actionButtons');

    private jobSubscription?: Subscription;
    private dialogRef?: DynamicDialogRef | null;
    /** Guards the one-shot auto-start so it can never refire (e.g. on a later status reprobe). */
    private autoStartHandled = false;
    /** Ticks the elapsed-time counter once per second while a run is in flight. */
    private elapsedTimer?: ReturnType<typeof setInterval>;
    /** Guards the one-shot focus move on a terminal outcome so it fires exactly once per run, never stealing focus on later renders. */
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
    }

    /** On load, reattach to a run still in progress for this exercise, or show the last outcome, by replaying the server-retained transcript. */
    ngOnInit(): void {
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
                            this.running.set(true);
                            this.startTimer();
                            this.subscribeToJob(status.jobId);
                        } else if (terminal) {
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
        this.generate();
    }

    generate(): void {
        if (this.running()) {
            return;
        }
        this.reset();
        this.running.set(true);
        this.startTimer();
        const trimmed = this.prompt().trim();
        this.generationService
            .generateExercise(this.exerciseId(), trimmed.length > 0 ? trimmed : undefined)
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
                        this.dialogRef = this.dialogService.open(GitDiffReportModalComponent, {
                            modal: true,
                            closable: false,
                            dismissableMask: false,
                            width: '90vw',
                            styleClass: GitDiffReportModalComponent.WINDOW_CLASS,
                            data: { repositoryDiffInformation, diffForTemplateAndSolution: true },
                        });
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

    /** Fills the instructions field with the translated text of a one-click example. */
    useExample(example: GenerationExample): void {
        this.prompt.set(this.translateService.instant(`artemisApp.programmingExercise.generateExercise.${example.promptKey}`));
    }

    private reset(): void {
        this.teardownSubscription();
        this.stopTimer();
        this.jobId.set(undefined);
        this.cancelling.set(false);
        this.progressEvents.set([]);
        this.finalEvent.set(undefined);
        this.showLog.set(false);
        this.terminalFocusHandled = false;
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
