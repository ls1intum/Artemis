import { DestroyRef, Injectable, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, Subscription, finalize, timeout } from 'rxjs';
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
    HyperionGenerationVerdict,
    isFileChange,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const MAX_STATUS_LOAD_ATTEMPTS = 3;
const STATUS_REQUEST_TIMEOUT_MS = 5_000;
const MAX_CANCELLATION_STATUS_CHECKS = 3;
const ACTIVE_STATUS_REFRESH_MS = 5_000;
const IDLE_STATUS_REFRESH_MS = 15_000;

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
export class HyperionGenerationActivityFacade implements OnDestroy {
    private readonly service = inject(HyperionExerciseGenerationService);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = signal<number | undefined>(undefined);
    readonly refreshingEditor = signal(false);
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
    private streamSubscription?: Subscription;
    private streamJobId?: string;
    private exerciseStateSubscription?: Subscription;
    private statusSubscription?: Subscription;
    private statusRequestInFlight = false;
    private pendingStatusLoad?: { exerciseId: number; expectedJobId?: string; background: boolean };
    private streamLossRefreshTimeout?: ReturnType<typeof setTimeout>;
    private statusRetryTimeout?: ReturnType<typeof setTimeout>;
    private revertAvailabilityRefreshTimeout?: ReturnType<typeof setTimeout>;
    private cancellationStatusTimeout?: ReturnType<typeof setTimeout>;
    private activeStatusTimeout?: ReturnType<typeof setTimeout>;
    private cancellationStatusChecks = 0;
    private statusLoadAttempts = 0;
    private loadedExerciseId?: number;
    private loadSequence = 0;
    private ownershipResolved = false;
    private destroyed = false;
    private readonly emittedTerminalJobs = new Set<string>();

    constructor() {
        effect(() => {
            const id = this.exerciseId();
            if (id === this.loadedExerciseId) {
                return;
            }
            this.loadedExerciseId = id;
            this.closeExerciseState();
            this.reset();
            if (id !== undefined) {
                this.openExerciseState(id);
                this.loadStatus(id);
            }
        });
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.loadSequence++;
        this.closeStream();
        this.closeExerciseState();
        this.statusSubscription?.unsubscribe();
        this.clearStreamLossRefresh();
        this.clearStatusRetry();
        this.clearRevertAvailabilityRefresh();
        this.clearCancellationStatusRefresh();
        this.clearActiveStatusRefresh();
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
        if (this.destroyed || id === undefined || !this.canRevert() || this.reverting()) {
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
        if (this.destroyed || id === undefined || job === undefined || !this.ownedByCaller() || !this.cancellable() || this.cancelRequested()) {
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
        if (this.destroyed) {
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
        const sequence = ++this.loadSequence;
        this.statusRequestInFlight = true;
        if (!background) {
            this.statusLoading.set(true);
        }
        this.statusSubscription = this.service
            .getStatus(exerciseId)
            .pipe(
                timeout(STATUS_REQUEST_TIMEOUT_MS),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => {
                    this.statusRequestInFlight = false;
                    const pending = this.pendingStatusLoad;
                    this.pendingStatusLoad = undefined;
                    if (pending && !this.destroyed && sequence === this.loadSequence) {
                        window.queueMicrotask(() => {
                            if (!this.destroyed && sequence === this.loadSequence) {
                                this.loadStatus(pending.exerciseId, pending.expectedJobId, pending.background);
                            }
                        });
                    }
                }),
            )
            .subscribe({
                next: (status) => {
                    if (sequence !== this.loadSequence) {
                        return;
                    }
                    this.clearStatusRetry();
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
                        this.clearCancellationStatusRefresh();
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
                    const events = mergeEvents(sameJob ? this.events() : [], status.events ?? []);
                    this.events.set(events);
                    const retainedFileChanges = status.fileChanges ?? [];
                    const mergedFileChanges = mergeFileChanges(sameJob ? this.fileChanges() : [], retainedFileChanges);
                    this.fileChanges.set(mergedFileChanges);
                    const terminalEvent = latestTerminalEvent(events);
                    if (terminalEvent) {
                        this.cancelRequested.set(false);
                        this.clearCancellationStatusRefresh();
                        this.clearActiveStatusRefresh();
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
                        this.clearActiveStatusRefresh();
                        this.scheduleIdleStatusRefresh(exerciseId);
                    }
                },
                error: (error: unknown) => {
                    if (sequence === this.loadSequence) {
                        if (background) {
                            this.statusLoading.set(false);
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
                        this.statusLoadAttempts++;
                        if (this.isRetryableStatusError(error) && this.statusLoadAttempts < MAX_STATUS_LOAD_ATTEMPTS) {
                            this.scheduleStatusRetry(exerciseId, expectedJobId, 1_000 * 2 ** (this.statusLoadAttempts - 1));
                        } else {
                            this.statusLoading.set(false);
                            if (!this.ownershipResolved) {
                                this.statusLoadFailed.set(true);
                            }
                        }
                    }
                },
            });
    }

    private isRetryableStatusError(error: unknown): boolean {
        return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500;
    }

    private openStream(jobId: string): boolean {
        if (this.destroyed) {
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
        if (this.destroyed || exerciseId === undefined || state.exerciseId !== exerciseId) {
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
            this.clearActiveStatusRefresh();
            this.loadStatus(exerciseId, state.jobId, true);
            return;
        }
        if (this.jobId() !== state.jobId) {
            return;
        }
        this.running.set(false);
        this.cancellable.set(false);
        this.closeStream();
        this.clearActiveStatusRefresh();
        this.loadStatus(exerciseId, state.jobId, true);
    }

    private handleMessage(message: HyperionGenerationMessage): void {
        this.clearStreamLossRefresh();
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
            this.clearCancellationStatusRefresh();
            this.clearActiveStatusRefresh();
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
        if (this.destroyed) {
            return;
        }
        this.service
            .getStatus(exerciseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (status) => {
                    if (this.exerciseId() === exerciseId && this.jobId() === jobId && status?.jobId === jobId) {
                        this.clearStatusRetry();
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
        if (this.destroyed) {
            return;
        }
        this.clearRevertAvailabilityRefresh();
        this.revertAvailabilityRefreshTimeout = setTimeout(() => {
            this.revertAvailabilityRefreshTimeout = undefined;
            if (this.destroyed) {
                return;
            }
            this.refreshRevertAvailability(exerciseId, jobId, false);
        }, 500);
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

    private refreshStatusAfterStreamLoss(delay = 1_000): void {
        if (this.destroyed || this.streamLossRefreshTimeout !== undefined) {
            return;
        }
        this.streamLossRefreshTimeout = setTimeout(() => {
            this.streamLossRefreshTimeout = undefined;
            if (this.destroyed) {
                return;
            }
            const id = this.exerciseId();
            if (id !== undefined && (this.running() || this.statusLoading())) {
                this.loadStatus(id);
            }
        }, delay);
    }

    private clearStreamLossRefresh(): void {
        if (this.streamLossRefreshTimeout !== undefined) {
            clearTimeout(this.streamLossRefreshTimeout);
            this.streamLossRefreshTimeout = undefined;
        }
    }

    private scheduleStatusRetry(exerciseId: number, expectedJobId: string | undefined, delay: number): void {
        if (this.destroyed || this.statusRetryTimeout !== undefined) {
            return;
        }
        this.statusRetryTimeout = setTimeout(() => {
            this.statusRetryTimeout = undefined;
            if (!this.destroyed && this.exerciseId() === exerciseId) {
                this.loadStatus(exerciseId, expectedJobId);
            }
        }, delay);
    }

    private clearStatusRetry(): void {
        if (this.statusRetryTimeout !== undefined) {
            clearTimeout(this.statusRetryTimeout);
            this.statusRetryTimeout = undefined;
        }
    }

    private clearRevertAvailabilityRefresh(): void {
        if (this.revertAvailabilityRefreshTimeout !== undefined) {
            clearTimeout(this.revertAvailabilityRefreshTimeout);
            this.revertAvailabilityRefreshTimeout = undefined;
        }
    }

    private scheduleCancellationStatusRefresh(exerciseId: number, jobId: string): void {
        if (this.destroyed || this.cancellationStatusTimeout !== undefined || this.cancellationStatusChecks >= MAX_CANCELLATION_STATUS_CHECKS) {
            return;
        }
        this.cancellationStatusTimeout = setTimeout(() => {
            this.cancellationStatusTimeout = undefined;
            if (this.destroyed) {
                return;
            }
            this.cancellationStatusChecks++;
            this.loadStatus(exerciseId, jobId);
        }, 1_000);
    }

    private clearCancellationStatusRefresh(): void {
        if (this.cancellationStatusTimeout !== undefined) {
            clearTimeout(this.cancellationStatusTimeout);
            this.cancellationStatusTimeout = undefined;
        }
    }

    private scheduleActiveStatusRefresh(exerciseId: number, jobId: string): void {
        if (this.destroyed || this.activeStatusTimeout !== undefined) {
            return;
        }
        this.activeStatusTimeout = setTimeout(() => {
            this.activeStatusTimeout = undefined;
            if (!this.destroyed && this.exerciseId() === exerciseId && this.jobId() === jobId && this.running()) {
                this.loadStatus(exerciseId, jobId, true);
            }
        }, ACTIVE_STATUS_REFRESH_MS);
    }

    private scheduleIdleStatusRefresh(exerciseId: number): void {
        if (this.destroyed || this.activeStatusTimeout !== undefined) {
            return;
        }
        this.activeStatusTimeout = setTimeout(() => {
            this.activeStatusTimeout = undefined;
            if (!this.destroyed && this.exerciseId() === exerciseId && !this.running()) {
                this.loadStatus(exerciseId, undefined, true);
            }
        }, IDLE_STATUS_REFRESH_MS);
    }

    private clearActiveStatusRefresh(): void {
        if (this.activeStatusTimeout !== undefined) {
            clearTimeout(this.activeStatusTimeout);
            this.activeStatusTimeout = undefined;
        }
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
        this.loadSequence++;
        this.statusSubscription?.unsubscribe();
        this.statusSubscription = undefined;
        this.statusRequestInFlight = false;
        this.pendingStatusLoad = undefined;
    }

    private reset(): void {
        this.cancelStatusRequest();
        this.closeStream();
        this.clearStreamLossRefresh();
        this.clearStatusRetry();
        this.clearRevertAvailabilityRefresh();
        this.clearCancellationStatusRefresh();
        this.clearActiveStatusRefresh();
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
