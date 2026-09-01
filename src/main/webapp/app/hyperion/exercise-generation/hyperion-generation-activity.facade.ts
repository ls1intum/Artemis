import { DestroyRef, Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, defer, finalize, retry, takeUntil, throwError, timeout, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import {
    MAX_RETAINED_EVENTS,
    TERMINAL_EVENT_TYPES,
    fileChangeKey,
    latestTerminalEvent,
    mergeEvents,
    mergeFileChanges,
    newerFileChange,
} from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import {
    ExerciseGenerationFileChange,
    ExerciseGenerationRevertResult,
    HyperionExerciseGenerationState,
    HyperionGenerationCompletionStatus,
    HyperionGenerationEvent,
    HyperionGenerationMessage,
    HyperionGenerationMode,
    HyperionGenerationStatus,
    HyperionGenerationVerdict,
    isFileChange,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const MAX_STATUS_LOAD_ATTEMPTS = 3;
const STATUS_RETRY_BASE_DELAY_MS = 1_000;
const STATUS_REQUEST_TIMEOUT_MS = 5_000;
const STREAM_LOSS_REFRESH_MS = 1_000;
const REVERT_AVAILABILITY_REFRESH_MS = 500;
const CANCELLATION_STATUS_REFRESH_MS = 1_000;
const MAX_CANCELLATION_STATUS_CHECKS = 3;
const ACTIVE_STATUS_REFRESH_MS = 5_000;
const IDLE_STATUS_REFRESH_MS = 15_000;

/**
 * A single delayed callback. Arming an armed slot is ignored, so the earliest deadline wins, and the owning
 * injector being destroyed cancels it - which is why this facade needs no `ngOnDestroy`.
 */
class DelayedCall {
    private subscription?: Subscription;

    constructor(private readonly destroyRef: DestroyRef) {}

    arm(delayMs: number, run: () => void): void {
        if (this.subscription) {
            return;
        }
        this.subscription = timer(delayMs)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.subscription = undefined;
                run();
            });
    }

    cancel(): void {
        this.subscription?.unsubscribe();
        this.subscription = undefined;
    }
}

/** The host component's inputs, handed over once so the facade can derive from them instead of being pushed into. */
export interface HyperionGenerationActivityInputs {
    exerciseId: Signal<number | undefined>;
    refreshingEditor: Signal<boolean>;
}

export interface HyperionGenerationCompletedEvent {
    jobId: string;
    mode?: HyperionGenerationMode;
    verdict?: HyperionGenerationVerdict;
    completionStatus?: HyperionGenerationCompletionStatus;
    liveExerciseChanged?: boolean;
    completedAt?: string;
    savedRepositoryCommits?: { [key: string]: string };
}

@Injectable()
export class HyperionGenerationActivityFacade {
    private readonly service = inject(HyperionExerciseGenerationService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly inputs = signal<HyperionGenerationActivityInputs>({ exerciseId: signal(undefined), refreshingEditor: signal(false) });

    readonly exerciseId = computed(() => this.inputs().exerciseId());
    readonly refreshingEditor = computed(() => this.inputs().refreshingEditor());
    readonly generationReverted = new Subject<string>();
    readonly generationCompleted = new Subject<HyperionGenerationCompletedEvent>();

    readonly jobId = signal<string | undefined>(undefined);
    readonly mode = signal<HyperionGenerationMode | undefined>(undefined);
    readonly running = signal<boolean>(false);
    readonly statusLoading = signal<boolean>(false);
    readonly statusLoadFailed = signal<boolean>(false);
    readonly events = signal<HyperionGenerationEvent[]>([]);
    readonly fileChanges = signal<ExerciseGenerationFileChange[]>([]);
    readonly verdict = signal<HyperionGenerationVerdict | undefined>(undefined);
    /** The design document the agent wrote before touching any code, as retained by the server for this run. */
    readonly specDocument = signal<string | undefined>(undefined);
    readonly completionStatus = signal<HyperionGenerationCompletionStatus | undefined>(undefined);
    readonly liveExerciseChanged = signal<boolean | undefined>(undefined);
    readonly revertAvailable = signal<boolean>(false);
    readonly revertJobId = signal<string | undefined>(undefined);
    readonly revertMode = signal<HyperionGenerationMode | undefined>(undefined);
    readonly ownedByCaller = signal<boolean>(true);
    readonly cancellable = signal<boolean>(false);
    readonly revertedMode = signal<HyperionGenerationMode | undefined>(undefined);

    readonly detailsExpanded = signal<boolean>(true);
    readonly cancelRequested = signal<boolean>(false);
    readonly confirmRevertVisible = signal<boolean>(false);

    readonly reverting = signal<boolean>(false);
    readonly reverted = signal<boolean>(false);
    readonly revertPartialRepositories = signal<string | undefined>(undefined);

    readonly canRevert = computed(() => !this.running() && !this.refreshingEditor() && !this.reverted() && this.revertAvailable());
    readonly effectiveRevertMode = computed(() => this.revertMode() ?? this.revertedMode() ?? this.mode());

    /** Why the run ended, from the newest terminal event; `undefined` while it is still going. */
    readonly terminationReason = computed(() => latestTerminalEvent(this.events())?.terminationReason);

    /** The newest repair-round bookkeeping the server reported, so the review stage can say which round it is on. */
    readonly repairRound = computed(() => this.events().findLast((event) => event.repairRound !== undefined)?.repairRound);

    private streamSubscription?: Subscription;
    private streamJobId?: string;
    private exerciseStateSubscription?: Subscription;
    /** Completes the in-flight status request so its response can no longer be applied. */
    private readonly statusRequestInvalidated = new Subject<void>();
    private statusRequestInFlight = false;
    private pendingStatusLoad?: { exerciseId: number; expectedJobId?: string; background: boolean };
    private readonly streamLossRefresh = new DelayedCall(this.destroyRef);
    private readonly revertAvailabilityRefresh = new DelayedCall(this.destroyRef);
    private readonly cancellationStatusRefresh = new DelayedCall(this.destroyRef);
    /** Shared by the active (5 s) and idle (15 s) reconciliation polls, which are mutually exclusive. */
    private readonly statusPoll = new DelayedCall(this.destroyRef);
    private cancellationStatusChecks = 0;
    private statusLoadAttempts = 0;
    private ownershipResolved = false;
    private readonly emittedTerminalJobs = new Set<string>();

    constructor() {
        // Only the exercise being watched may retrigger this. Everything below writes signals that a synchronous
        // status response also reads, so tracking the body would make the effect retrigger itself indefinitely.
        effect(() => {
            const id = this.exerciseId();
            untracked(() => {
                this.closeExerciseState();
                this.reset();
                if (id !== undefined) {
                    this.openExerciseState(id);
                    this.loadStatus(id);
                }
            });
        });
    }

    connect(inputs: HyperionGenerationActivityInputs): void {
        this.inputs.set(inputs);
    }

    attachToJob(jobId: string, mode: HyperionGenerationMode): void {
        const exerciseId = this.exerciseId();
        if (exerciseId === undefined) {
            return;
        }
        this.reset();
        this.mode.set(mode);
        this.jobId.set(jobId);
        this.running.set(true);
        this.cancellable.set(true);
        this.ownershipResolved = true;
        this.openStream(jobId);
        this.loadStatus(exerciseId, jobId);
    }

    confirmRevert(): void {
        if (!this.canRevert() || this.reverting()) {
            return;
        }
        this.confirmRevertVisible.set(true);
    }

    dismissRevert(): void {
        this.confirmRevertVisible.set(false);
    }

    acceptRevert(): void {
        this.confirmRevertVisible.set(false);
        this.revert();
    }

    toggleDetails(): void {
        this.detailsExpanded.update((expanded) => !expanded);
    }

    private revert(): void {
        const id = this.exerciseId();
        if (this.destroyRef.destroyed || id === undefined || !this.canRevert() || this.reverting()) {
            return;
        }
        this.reverting.set(true);
        this.service
            .revertExerciseGeneration(id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    this.reverting.set(false);
                    this.handleRevertResult(result);
                },
                error: (error: unknown) => {
                    this.reverting.set(false);
                    if (error instanceof HttpErrorResponse && error.status === 409 && this.isRevertResult(error.error)) {
                        this.handleRevertResult(error.error);
                        return;
                    }
                    this.alertService.error(
                        this.effectiveRevertMode() === 'GENERATE'
                            ? 'artemisApp.hyperion.generationActivity.undoGenerationFailed'
                            : 'artemisApp.hyperion.generationActivity.undoAdaptationFailed',
                    );
                },
            });
    }

    cancel(): void {
        const id = this.exerciseId();
        const job = this.jobId();
        if (this.destroyRef.destroyed || id === undefined || job === undefined || !this.ownedByCaller() || !this.cancellable() || this.cancelRequested()) {
            return;
        }
        this.cancelRequested.set(true);
        this.cancellationStatusChecks = 0;
        this.service
            .cancel(id, job)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => this.scheduleCancellationStatusRefresh(id, job),
                error: (error) => {
                    this.cancelRequested.set(false);
                    if (!(error instanceof HttpErrorResponse) || error.status !== 404) {
                        this.alertService.error('artemisApp.hyperion.generationActivity.cancelFailed');
                    }
                    this.loadStatus(id, job);
                },
            });
    }

    retryStatus(): void {
        const id = this.exerciseId();
        if (id === undefined) {
            return;
        }
        this.statusLoadAttempts = 0;
        this.loadStatus(id, this.jobId());
    }

    private loadStatus(exerciseId: number, expectedJobId?: string, background = false): void {
        if (this.destroyRef.destroyed) {
            return;
        }
        if (this.statusRequestInFlight) {
            this.pendingStatusLoad = {
                exerciseId,
                expectedJobId,
                background: (this.pendingStatusLoad?.background ?? true) && background,
            };
            return;
        }
        this.statusRequestInFlight = true;
        if (!background) {
            this.statusLoading.set(true);
        }
        this.requestStatus(exerciseId, background)
            .pipe(
                takeUntil(this.statusRequestInvalidated),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    this.statusRequestInFlight = false;
                    // Re-read rather than capture: invalidating the request also drops whatever it had queued.
                    if (this.pendingStatusLoad) {
                        window.queueMicrotask(() => {
                            const pending = this.pendingStatusLoad;
                            this.pendingStatusLoad = undefined;
                            if (pending && !this.destroyRef.destroyed) {
                                this.loadStatus(pending.exerciseId, pending.expectedJobId, pending.background);
                            }
                        });
                    }
                }),
            )
            .subscribe({
                next: (status) => {
                    this.statusLoading.set(false);
                    this.statusLoadFailed.set(false);
                    this.statusLoadAttempts = 0;
                    this.ownershipResolved = true;
                    if (!status) {
                        if (this.cancelRequested()) {
                            this.reset();
                            this.ownershipResolved = true;
                            return;
                        }
                        if (expectedJobId !== undefined && this.running() && this.ownedByCaller()) {
                            this.scheduleActiveStatusRefresh(exerciseId, expectedJobId);
                            return;
                        }
                        if (expectedJobId === undefined || !this.ownedByCaller()) {
                            this.reset();
                            this.ownershipResolved = true;
                            this.scheduleIdleStatusRefresh(exerciseId);
                        }
                        return;
                    }
                    if (expectedJobId !== undefined && status.jobId !== expectedJobId) {
                        if (!this.cancelRequested() && this.ownedByCaller() && !status.running) {
                            this.scheduleActiveStatusRefresh(exerciseId, expectedJobId);
                            return;
                        }
                        this.cancelRequested.set(false);
                        this.cancellationStatusRefresh.cancel();
                        this.running.set(false);
                    }
                    const sameJob = this.jobId() === status.jobId;
                    const wasActivelyObserved = sameJob && this.running();
                    this.jobId.set(status.jobId);
                    this.mode.set(status.mode ?? this.mode());
                    this.revertAvailable.set(status.revertAvailable);
                    this.revertJobId.set(status.revertJobId);
                    this.revertMode.set(status.revertMode);
                    this.ownedByCaller.set(status.ownedByCaller === true);
                    this.cancellable.set(status.cancellable === true);
                    // A later poll for the same job may omit the design document; keep the one already shown rather than blanking the panel.
                    if (!sameJob || status.specDocument !== undefined) {
                        this.specDocument.set(status.specDocument);
                    }
                    const events = mergeEvents(sameJob ? this.events() : [], status.events ?? []);
                    this.events.set(events);
                    const retainedFileChanges = status.fileChanges ?? [];
                    const mergedFileChanges = mergeFileChanges(sameJob ? this.fileChanges() : [], retainedFileChanges);
                    this.fileChanges.set(mergedFileChanges);
                    const terminalEvent = latestTerminalEvent(events);
                    if (terminalEvent) {
                        this.cancelRequested.set(false);
                        this.cancellationStatusRefresh.cancel();
                        this.statusPoll.cancel();
                        this.closeStream();
                        this.restoreTerminalState(terminalEvent);
                        this.running.set(false);
                        if (!sameJob) {
                            this.detailsExpanded.set(false);
                        }
                        // A retained terminal event for a job this component never actively watched (e.g. the page was opened while
                        // generation was finalizing) must still trigger a refresh when it reports that the live exercise actually
                        // changed - otherwise a newly-opened editor that already fetched the pre-save exercise would never reload.
                        // emitGenerationCompleted() itself dedupes per jobId, so this cannot double-refresh across repeated polls.
                        if (wasActivelyObserved || terminalEvent.liveExerciseChanged) {
                            this.emitGenerationCompleted(status.jobId, terminalEvent);
                        }
                        this.scheduleIdleStatusRefresh(exerciseId);
                        return;
                    }
                    this.running.set(status.running);
                    if (this.cancelRequested() && expectedJobId === status.jobId) {
                        if (status.running) {
                            this.scheduleCancellationStatusRefresh(exerciseId, status.jobId);
                        } else {
                            this.cancelRequested.set(false);
                        }
                    }
                    if (status.running) {
                        if (this.ownedByCaller()) {
                            if (this.openStream(status.jobId)) {
                                this.loadStatus(exerciseId, status.jobId);
                            }
                        } else {
                            this.closeStream();
                        }
                        this.scheduleActiveStatusRefresh(exerciseId, status.jobId);
                    } else {
                        this.statusPoll.cancel();
                        this.scheduleIdleStatusRefresh(exerciseId);
                    }
                },
                error: () => {
                    this.statusLoading.set(false);
                    if (background) {
                        this.statusLoadAttempts++;
                        if (!this.ownershipResolved || this.statusLoadAttempts >= MAX_STATUS_LOAD_ATTEMPTS) {
                            this.statusLoadFailed.set(true);
                        }
                        if (expectedJobId !== undefined && this.running()) {
                            this.scheduleActiveStatusRefresh(exerciseId, expectedJobId);
                        } else {
                            this.scheduleIdleStatusRefresh(exerciseId);
                        }
                        return;
                    }
                    if (!this.ownershipResolved) {
                        this.statusLoadFailed.set(true);
                    }
                },
            });
    }

    /**
     * A foreground load owns its own bounded, exponentially backed-off retry; only the attempts a background
     * poll makes still need counting, because those decide when the panel reports the status as unavailable.
     */
    private requestStatus(exerciseId: number, background: boolean): Observable<HyperionGenerationStatus | null> {
        // `defer` so a retry issues a fresh request instead of re-subscribing the observable built here.
        const request = defer(() => this.service.getStatus(exerciseId)).pipe(timeout(STATUS_REQUEST_TIMEOUT_MS));
        return background
            ? request
            : request.pipe(
                  retry({
                      count: MAX_STATUS_LOAD_ATTEMPTS - 1,
                      delay: (error: unknown, retryCount: number) =>
                          this.isRetryableStatusError(error) ? timer(STATUS_RETRY_BASE_DELAY_MS * 2 ** (retryCount - 1)) : throwError(() => error),
                  }),
              );
    }

    private isRetryableStatusError(error: unknown): boolean {
        return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500;
    }

    private openStream(jobId: string): boolean {
        if (this.destroyRef.destroyed) {
            return false;
        }
        if (this.streamSubscription && this.streamJobId === jobId) {
            return false;
        }
        this.closeStream();
        this.streamJobId = jobId;
        const subscription = this.service
            .subscribeToStream(jobId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (message) => this.handleMessage(message),
                error: () => {
                    this.streamSubscription = undefined;
                    this.streamJobId = undefined;
                    this.refreshStatusAfterStreamLoss();
                },
                complete: () => {
                    this.streamSubscription = undefined;
                    this.streamJobId = undefined;
                    this.refreshStatusAfterStreamLoss();
                },
            });
        this.streamSubscription = subscription.closed ? undefined : subscription;
        if (subscription.closed) {
            this.streamJobId = undefined;
            return false;
        }
        return true;
    }

    private openExerciseState(exerciseId: number): void {
        const subscription = this.service
            .subscribeToExerciseState(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (state) => this.handleExerciseState(state),
                error: () => (this.exerciseStateSubscription = undefined),
                complete: () => (this.exerciseStateSubscription = undefined),
            });
        this.exerciseStateSubscription = subscription.closed ? undefined : subscription;
    }

    private handleExerciseState(state: HyperionExerciseGenerationState): void {
        const exerciseId = this.exerciseId();
        if (this.destroyRef.destroyed || exerciseId === undefined || state.exerciseId !== exerciseId) {
            return;
        }
        this.cancelStatusRequest();
        if (state.running) {
            const sameOwnedJob = this.jobId() === state.jobId && this.ownedByCaller();
            if (this.jobId() !== state.jobId) {
                this.closeStream();
                this.jobId.set(state.jobId);
                this.mode.set(undefined);
                this.events.set([]);
                this.clearFileChanges();
            }
            this.running.set(true);
            if (!sameOwnedJob) {
                this.ownedByCaller.set(false);
                this.cancellable.set(false);
            }
            this.statusPoll.cancel();
            this.loadStatus(exerciseId, state.jobId, true);
            return;
        }
        if (this.jobId() !== state.jobId) {
            return;
        }
        this.running.set(false);
        this.cancellable.set(false);
        this.closeStream();
        this.statusPoll.cancel();
        this.loadStatus(exerciseId, state.jobId, true);
    }

    private handleMessage(message: HyperionGenerationMessage): void {
        this.streamLossRefresh.cancel();
        if (isFileChange(message)) {
            this.upsertFileChange(message);
            return;
        }
        this.events.update((list) => [...list, message].slice(-MAX_RETAINED_EVENTS));
        if (TERMINAL_EVENT_TYPES.has(message.type)) {
            // A terminal event originates from the server-owned job stream and is authoritative for that job's completion. Treat later REST failures as a new outage window
            // instead of carrying retry debt from the pre-terminal reconciliation phase.
            this.statusLoading.set(false);
            this.statusLoadFailed.set(false);
            this.statusLoadAttempts = 0;
            this.ownershipResolved = true;
            this.cancelRequested.set(false);
            this.cancellationStatusRefresh.cancel();
            this.statusPoll.cancel();
            this.running.set(false);
            this.verdict.set(message.verdict);
            this.completionStatus.set(message.completionStatus);
            this.liveExerciseChanged.set(message.liveExerciseChanged);
            this.closeStream();
            this.emitGenerationCompleted(this.jobId(), message);
            const exerciseId = this.exerciseId();
            const jobId = this.jobId();
            if (exerciseId !== undefined && jobId !== undefined) {
                this.loadStatus(exerciseId, jobId, true);
                this.refreshRevertAvailability(exerciseId, jobId);
            }
        }
    }

    private refreshRevertAvailability(exerciseId: number, jobId: string, retry = true): void {
        if (this.destroyRef.destroyed) {
            return;
        }
        this.service
            .getStatus(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (status) => {
                    if (this.exerciseId() === exerciseId && this.jobId() === jobId && status?.jobId === jobId) {
                        this.statusLoadFailed.set(false);
                        this.statusLoadAttempts = 0;
                        this.ownershipResolved = true;
                        const available = status.revertAvailable;
                        this.revertAvailable.set(available);
                        this.revertJobId.set(status.revertJobId);
                        this.revertMode.set(status.revertMode);
                        if (!available && retry) {
                            this.scheduleRevertAvailabilityRefresh(exerciseId, jobId);
                        }
                    }
                },
                error: () => {
                    if (retry) {
                        this.scheduleRevertAvailabilityRefresh(exerciseId, jobId);
                    }
                },
            });
    }

    private scheduleRevertAvailabilityRefresh(exerciseId: number, jobId: string): void {
        this.revertAvailabilityRefresh.cancel();
        this.revertAvailabilityRefresh.arm(REVERT_AVAILABILITY_REFRESH_MS, () => this.refreshRevertAvailability(exerciseId, jobId, false));
    }

    private upsertFileChange(fileChange: ExerciseGenerationFileChange): void {
        this.fileChanges.update((list) => {
            const index = list.findIndex((file) => fileChangeKey(file) === fileChangeKey(fileChange));
            if (index < 0) {
                return [...list, fileChange];
            }
            const updated = list.slice();
            updated[index] = newerFileChange(updated[index], fileChange);
            return updated;
        });
    }

    private restoreTerminalState(event: HyperionGenerationEvent): void {
        this.verdict.set(event.verdict);
        this.completionStatus.set(event.completionStatus);
        this.liveExerciseChanged.set(event.liveExerciseChanged);
    }

    private emitGenerationCompleted(jobId: string | undefined, event: HyperionGenerationEvent): void {
        if (jobId === undefined || this.emittedTerminalJobs.has(jobId)) {
            return;
        }
        this.emittedTerminalJobs.add(jobId);
        const completedEvent: HyperionGenerationCompletedEvent = {
            jobId,
            mode: this.mode(),
            verdict: event.verdict,
            completionStatus: event.completionStatus,
            liveExerciseChanged: event.liveExerciseChanged,
        };
        completedEvent.completedAt = event.timestamp;
        if (event.savedRepositoryCommits !== undefined) {
            completedEvent.savedRepositoryCommits = event.savedRepositoryCommits;
        }
        this.generationCompleted.next(completedEvent);
    }

    private handleRevertResult(result: ExerciseGenerationRevertResult): void {
        if (!result.fullyReverted) {
            const repositories = result.revertedRepositories.join(', ') || '-';
            this.revertPartialRepositories.set(repositories);
            this.verdict.set(undefined);
            this.completionStatus.set(undefined);
            this.alertService.error('artemisApp.hyperion.generationActivity.revertPartialFailed', { repositories });
            // Even a partial revert may have reset one or more repositories (or the problem statement) on the server. The editor must not keep
            // showing pre-revert content for those, so trigger the same conservative refresh a full revert would, in addition to the error alert.
            if (result.revertedRepositories.length > 0) {
                this.generationReverted.next(result.completedAt);
            }
            return;
        }
        this.revertPartialRepositories.set(undefined);
        this.revertedMode.set(this.effectiveRevertMode());
        this.reverted.set(true);
        this.revertAvailable.set(false);
        this.revertJobId.set(undefined);
        this.revertMode.set(undefined);
        this.ownedByCaller.set(true);
        this.jobId.set(undefined);
        this.verdict.set(undefined);
        this.completionStatus.set(undefined);
        this.liveExerciseChanged.set(undefined);
        this.events.set([]);
        this.specDocument.set(undefined);
        this.clearFileChanges();
        this.generationReverted.next(result.completedAt);
        this.alertService.success(
            this.effectiveRevertMode() === 'GENERATE'
                ? 'artemisApp.hyperion.generationActivity.undoGenerationSuccess'
                : 'artemisApp.hyperion.generationActivity.undoAdaptationSuccess',
        );
    }

    private isRevertResult(value: unknown): value is ExerciseGenerationRevertResult {
        return (
            typeof value === 'object' &&
            value !== null &&
            typeof (value as ExerciseGenerationRevertResult).fullyReverted === 'boolean' &&
            Array.isArray((value as ExerciseGenerationRevertResult).revertedRepositories) &&
            typeof (value as ExerciseGenerationRevertResult).completedAt === 'string'
        );
    }

    private refreshStatusAfterStreamLoss(): void {
        this.streamLossRefresh.arm(STREAM_LOSS_REFRESH_MS, () => {
            const id = this.exerciseId();
            if (id !== undefined && (this.running() || this.statusLoading())) {
                this.loadStatus(id);
            }
        });
    }

    private scheduleCancellationStatusRefresh(exerciseId: number, jobId: string): void {
        if (this.cancellationStatusChecks >= MAX_CANCELLATION_STATUS_CHECKS) {
            return;
        }
        this.cancellationStatusRefresh.arm(CANCELLATION_STATUS_REFRESH_MS, () => {
            this.cancellationStatusChecks++;
            this.loadStatus(exerciseId, jobId);
        });
    }

    private scheduleActiveStatusRefresh(exerciseId: number, jobId: string): void {
        this.statusPoll.arm(ACTIVE_STATUS_REFRESH_MS, () => {
            if (this.exerciseId() === exerciseId && this.jobId() === jobId && this.running()) {
                this.loadStatus(exerciseId, jobId, true);
            }
        });
    }

    private scheduleIdleStatusRefresh(exerciseId: number): void {
        this.statusPoll.arm(IDLE_STATUS_REFRESH_MS, () => {
            if (this.exerciseId() === exerciseId && !this.running()) {
                this.loadStatus(exerciseId, undefined, true);
            }
        });
    }

    private closeStream(): void {
        this.streamSubscription?.unsubscribe();
        this.streamSubscription = undefined;
        this.streamJobId = undefined;
    }

    private closeExerciseState(): void {
        this.exerciseStateSubscription?.unsubscribe();
        this.exerciseStateSubscription = undefined;
    }

    private cancelStatusRequest(): void {
        this.statusRequestInvalidated.next();
        this.statusRequestInFlight = false;
        this.pendingStatusLoad = undefined;
    }

    private reset(): void {
        this.cancelStatusRequest();
        this.closeStream();
        this.streamLossRefresh.cancel();
        this.revertAvailabilityRefresh.cancel();
        this.cancellationStatusRefresh.cancel();
        this.statusPoll.cancel();
        this.jobId.set(undefined);
        this.mode.set(undefined);
        this.running.set(false);
        this.statusLoading.set(false);
        this.statusLoadFailed.set(false);
        this.statusLoadAttempts = 0;
        this.ownershipResolved = false;
        this.reverting.set(false);
        this.reverted.set(false);
        this.revertedMode.set(undefined);
        this.revertPartialRepositories.set(undefined);
        this.detailsExpanded.set(true);
        this.emittedTerminalJobs.clear();
        this.events.set([]);
        this.verdict.set(undefined);
        this.specDocument.set(undefined);
        this.completionStatus.set(undefined);
        this.liveExerciseChanged.set(undefined);
        this.revertAvailable.set(false);
        this.revertJobId.set(undefined);
        this.revertMode.set(undefined);
        this.ownedByCaller.set(true);
        this.cancellable.set(false);
        this.cancelRequested.set(false);
        this.cancellationStatusChecks = 0;
        // The undo confirmation belongs to the run being discarded here, so it must not survive into the next one.
        this.confirmRevertVisible.set(false);
        this.clearFileChanges();
    }

    private clearFileChanges(): void {
        this.fileChanges.set([]);
    }
}
