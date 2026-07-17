import { ChangeDetectionStrategy, Component, DestroyRef, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, finalize, timeout } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faCircleCheck, faCircleXmark, faRotateLeft, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import {
    ExerciseGenerationFileSnapshot,
    ExerciseGenerationRevertResult,
    HyperionGenerationCompletionStatus,
    HyperionGenerationEvent,
    HyperionGenerationMessage,
    HyperionGenerationMode,
    HyperionGenerationVerdict,
    HyperionSnapshotRepo,
    isFileSnapshot,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const REPO_ORDER: HyperionSnapshotRepo[] = ['solution', 'template', 'tests', 'other'];
const TERMINAL_EVENT_TYPES = new Set<HyperionGenerationEvent['type']>(['DONE', 'CANCELLED', 'ERROR']);
const MAX_RETAINED_EVENTS = 50;
const MAX_STATUS_LOAD_ATTEMPTS = 3;
const STATUS_REQUEST_TIMEOUT_MS = 5_000;
const MAX_CANCELLATION_STATUS_CHECKS = 3;
const ACTIVE_STATUS_REFRESH_MS = 5_000;
const IDLE_STATUS_REFRESH_MS = 15_000;

interface RepoFileGroup {
    repo: HyperionSnapshotRepo;
    files: ExerciseGenerationFileSnapshot[];
}

interface ActivityLiveStatus {
    message?: string;
    labelKey: string;
    busy: boolean;
}

export type HyperionReviewTarget = 'problem-statement' | 'solution' | 'template' | 'tests';

export interface HyperionReviewRequestedEvent {
    target: HyperionReviewTarget;
    jobId: string;
}

export interface HyperionGenerationCompletedEvent {
    mode?: HyperionGenerationMode;
    verdict?: HyperionGenerationVerdict;
    completionStatus?: HyperionGenerationCompletionStatus;
    liveExerciseChanged?: boolean;
    completedAt?: string;
}

@Component({
    selector: 'jhi-hyperion-generation-activity',
    templateUrl: './hyperion-generation-activity.component.html',
    styleUrl: './hyperion-generation-activity.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ButtonModule, ConfirmDialogModule, MessageModule, TagModule, TooltipModule],
    providers: [ConfirmationService],
})
export class HyperionGenerationActivityComponent implements OnDestroy {
    private readonly service = inject(HyperionExerciseGenerationService);
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(ConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exerciseId = input<number | undefined>();
    readonly startAllowed = input(true);
    readonly refreshingEditor = input(false);
    readonly editorRefreshFailed = input(false);
    readonly editorRefreshRequested = output<void>();
    readonly generationReverted = output<string>();
    readonly generationCompleted = output<HyperionGenerationCompletedEvent>();
    readonly snapshotSelected = output<ExerciseGenerationFileSnapshot>();
    readonly reviewRequested = output<HyperionReviewRequestedEvent>();
    readonly startRequested = output<HyperionGenerationMode | undefined>();

    readonly jobId = signal<string | undefined>(undefined);
    readonly mode = signal<HyperionGenerationMode | undefined>(undefined);
    readonly running = signal<boolean>(false);
    readonly statusLoading = signal<boolean>(false);
    readonly statusLoadFailed = signal<boolean>(false);
    readonly events = signal<HyperionGenerationEvent[]>([]);
    readonly snapshots = signal<ExerciseGenerationFileSnapshot[]>([]);
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

    readonly reverting = signal<boolean>(false);
    readonly reverted = signal<boolean>(false);
    readonly revertPartialRepositories = signal<string | undefined>(undefined);

    readonly visible = computed(() => this.exerciseId() !== undefined);
    readonly idle = computed(() => this.jobId() === undefined && !this.statusLoading() && !this.statusLoadFailed() && !this.editorRefreshFailed() && !this.reverted());

    readonly titleLabelKey = computed(() => (this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adaptationTitle' : 'artemisApp.hyperion.generationActivity.title'));
    readonly runningLabelKey = computed(() => {
        if (this.running() && !this.cancellable()) {
            return 'artemisApp.hyperion.generationActivity.finalizing';
        }
        return this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adapting' : 'artemisApp.hyperion.generationActivity.running';
    });
    readonly canRevert = computed(() => !this.running() && !this.refreshingEditor() && !this.reverted() && this.revertAvailable());
    readonly canRunAgain = computed(() => {
        if (!this.startAllowed() || this.running() || this.statusLoading()) {
            return false;
        }
        const terminal = this.latestTerminalEvent(this.events());
        return terminal?.type === 'ERROR' || terminal?.type === 'CANCELLED' || (terminal?.type === 'DONE' && terminal.completionStatus === 'PARTIAL');
    });
    readonly effectiveRevertMode = computed(() => this.revertMode() ?? this.revertedMode() ?? this.mode());
    readonly undoLabelKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.undoGeneration' : 'artemisApp.hyperion.generationActivity.undoAdaptation',
    );
    readonly undoTooltipKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.undoGenerationTooltip' : 'artemisApp.hyperion.generationActivity.undoAdaptationTooltip',
    );
    readonly undoneLabelKey = computed(() =>
        this.effectiveRevertMode() === 'GENERATE' ? 'artemisApp.hyperion.generationActivity.generationUndone' : 'artemisApp.hyperion.generationActivity.adaptationUndone',
    );

    readonly hasDetails = computed(() => this.snapshots().length > 0 || this.previousProgress().length > 0);
    readonly detailsLabelKey = computed(() => {
        if (this.snapshots().length) {
            return this.detailsExpanded() ? 'artemisApp.hyperion.generationActivity.hideChangedFiles' : 'artemisApp.hyperion.generationActivity.showChangedFiles';
        }
        return this.detailsExpanded() ? 'artemisApp.hyperion.generationActivity.hideDetails' : 'artemisApp.hyperion.generationActivity.showDetails';
    });

    readonly filesByRepo = computed<RepoFileGroup[]>(() => {
        const files = this.snapshots();
        return REPO_ORDER.map((repo) => ({
            repo,
            files: files.filter((file) => file.repo === repo).sort((first, second) => this.displayPath(first).localeCompare(this.displayPath(second))),
        })).filter((group) => group.files.length > 0);
    });

    readonly recentEvents = computed(() =>
        this.events()
            .filter((event) => event.message)
            .slice(-8)
            .reverse(),
    );
    readonly currentProgress = computed(() => this.recentEvents()[0]);
    readonly previousProgress = computed(() => this.recentEvents().slice(1));
    readonly liveStatus = computed<ActivityLiveStatus | undefined>(() => {
        if (this.refreshingEditor()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.refreshingEditor', busy: true };
        }
        if (this.editorRefreshFailed()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.editorRefreshFailed', busy: false };
        }
        const terminal = this.latestTerminalEvent(this.events());
        if (terminal) {
            return { labelKey: `artemisApp.hyperion.generationActivity.terminalStatus.${terminal.type}`, busy: false };
        }
        if (this.statusLoading() && !this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.checkingStatus', busy: true };
        }
        if (this.running()) {
            return { labelKey: this.ownedByCaller() ? this.runningLabelKey() : 'artemisApp.hyperion.generationActivity.runningByAnotherInstructor', busy: true };
        }
        return undefined;
    });
    readonly terminalStatus = computed(() => {
        const type = this.latestTerminalEvent(this.events())?.type;
        if (type === 'CANCELLED') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.CANCELLED', severity: 'secondary' as const };
        }
        if (type === 'ERROR') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.terminalStatus.ERROR', severity: 'danger' as const };
        }
        return undefined;
    });
    readonly terminalMessage = computed(() => this.latestTerminalEvent(this.events())?.message);
    readonly persistenceState = computed(() => {
        if (this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.workingCopy', severity: 'warn' as const };
        }
        const terminal = this.latestTerminalEvent(this.events());
        if (terminal?.type === 'DONE') {
            if (terminal.completionStatus === 'PARTIAL') {
                return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.partial', severity: 'danger' as const };
            }
            if (terminal.liveExerciseChanged) {
                return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.saved', severity: 'success' as const };
            }
            return terminal.completionStatus === 'NEEDS_REVIEW'
                ? { labelKey: 'artemisApp.hyperion.generationActivity.persistence.draft', severity: 'warn' as const }
                : { labelKey: 'artemisApp.hyperion.generationActivity.persistence.notSaved', severity: 'danger' as const };
        }
        if (terminal?.type === 'CANCELLED') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.cancelled', severity: 'secondary' as const };
        }
        if (terminal?.type === 'ERROR') {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.failed', severity: 'danger' as const };
        }
        return undefined;
    });
    readonly canNavigateSnapshots = computed(() => {
        const terminalEvent = this.latestTerminalEvent(this.events());
        return terminalEvent?.type === 'DONE' && terminalEvent.liveExerciseChanged === true;
    });
    readonly reviewTargets = computed<HyperionReviewTarget[]>(() => {
        if (!this.reviewJobId()) {
            return [];
        }
        const snapshots = this.snapshots();
        if (!snapshots.length) {
            return !this.running() && this.revertAvailable() ? ['problem-statement', 'solution', 'template', 'tests'] : [];
        }
        if (!this.canNavigateSnapshots()) {
            return [];
        }
        return (['problem-statement', 'solution', 'template', 'tests'] as const).filter((target) =>
            target === 'problem-statement'
                ? snapshots.some((file) => file.repo === 'other' && file.path === 'problem-statement.md')
                : snapshots.some((file) => file.repo === target),
        );
    });
    readonly reviewJobId = computed(() => (this.canNavigateSnapshots() ? this.jobId() : this.revertAvailable() ? this.revertJobId() : undefined));

    canNavigateSnapshot(snapshot: ExerciseGenerationFileSnapshot): boolean {
        return this.canNavigateSnapshots() && (snapshot.repo !== 'other' || snapshot.path === 'problem-statement.md');
    }

    protected readonly faSpinner = faSpinner;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faRotateLeft = faRotateLeft;

    private streamSubscription?: Subscription;
    private streamJobId?: string;
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
            this.reset();
            if (id !== undefined) {
                this.loadStatus(id);
            }
        });
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.loadSequence++;
        this.closeStream();
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
        this.confirmationService.confirm({
            header: this.translateService.instant(
                this.effectiveRevertMode() === 'GENERATE'
                    ? 'artemisApp.hyperion.generationActivity.undoGenerationConfirmHeader'
                    : 'artemisApp.hyperion.generationActivity.undoAdaptationConfirmHeader',
            ),
            message: this.translateService.instant(
                this.effectiveRevertMode() === 'GENERATE'
                    ? 'artemisApp.hyperion.generationActivity.undoGenerationConfirmMessage'
                    : 'artemisApp.hyperion.generationActivity.undoAdaptationConfirmMessage',
            ),
            rejectButtonProps: {
                label: this.translateService.instant('entity.action.cancel'),
                severity: 'secondary',
            },
            acceptButtonProps: {
                label: this.translateService.instant(this.undoLabelKey()),
                severity: 'danger',
            },
            defaultFocus: 'reject',
            accept: () => this.revert(),
        });
    }

    toggleDetails(): void {
        this.detailsExpanded.update((expanded) => !expanded);
    }

    protected displayPath(snapshot: ExerciseGenerationFileSnapshot): string {
        const prefix = `${snapshot.repo}/`;
        return snapshot.path.startsWith(prefix) ? snapshot.path.slice(prefix.length) : snapshot.path;
    }

    protected snapshotAccessibleLabel(snapshot: ExerciseGenerationFileSnapshot): string {
        const repository = this.translateService.instant(`artemisApp.hyperion.generationActivity.repo.${snapshot.repo}`);
        return `${repository}: ${this.displayPath(snapshot)}`;
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

    selectFile(snapshot: ExerciseGenerationFileSnapshot): void {
        if (!this.canNavigateSnapshot(snapshot)) {
            return;
        }
        this.snapshotSelected.emit(snapshot);
    }

    requestReview(target: HyperionReviewTarget): void {
        const currentJobId = this.reviewJobId();
        if (currentJobId && this.reviewTargets().includes(target)) {
            this.reviewRequested.emit({ target, jobId: currentJobId });
        }
    }

    requestStart(): void {
        if (this.startAllowed()) {
            this.startRequested.emit(undefined);
        }
    }

    runAgain(): void {
        if (this.canRunAgain()) {
            this.startRequested.emit(this.mode());
        }
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
                next: (response) => {
                    if (sequence !== this.loadSequence) {
                        return;
                    }
                    this.clearStatusRetry();
                    this.statusLoading.set(false);
                    this.statusLoadFailed.set(false);
                    this.statusLoadAttempts = 0;
                    this.ownershipResolved = true;
                    const status = response.body ?? undefined;
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
                    const events = this.mergeEvents(sameJob ? this.events() : [], status.events ?? []);
                    this.events.set(events);
                    const fileSnapshots = status.fileSnapshots ?? [];
                    const snapshots = this.mergeSnapshots(sameJob ? this.snapshots() : [], fileSnapshots);
                    this.snapshots.set(snapshots);
                    const terminalEvent = this.latestTerminalEvent(events);
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
                        if (wasActivelyObserved) {
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

    private handleMessage(message: HyperionGenerationMessage): void {
        this.clearStreamLossRefresh();
        if (isFileSnapshot(message)) {
            this.upsertSnapshot(message);
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
                next: (response) => {
                    const status = response.body ?? undefined;
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

    private upsertSnapshot(snapshot: ExerciseGenerationFileSnapshot): void {
        this.snapshots.update((list) => {
            const index = list.findIndex((file) => this.snapshotKey(file) === this.snapshotKey(snapshot));
            if (index < 0) {
                return [...list, snapshot];
            }
            const updated = list.slice();
            updated[index] = this.newerSnapshot(updated[index], snapshot);
            return updated;
        });
    }

    private latestTerminalEvent(events: HyperionGenerationEvent[]): HyperionGenerationEvent | undefined {
        for (let index = events.length - 1; index >= 0; index--) {
            const event = events[index];
            if (TERMINAL_EVENT_TYPES.has(event.type)) {
                return event;
            }
        }
        return undefined;
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
            mode: this.mode(),
            verdict: event.verdict,
            completionStatus: event.completionStatus,
            liveExerciseChanged: event.liveExerciseChanged,
        };
        completedEvent.completedAt = event.timestamp;
        this.generationCompleted.emit(completedEvent);
    }

    private mergeEvents(current: HyperionGenerationEvent[], retained: HyperionGenerationEvent[]): HyperionGenerationEvent[] {
        const byKey = new Map<string, HyperionGenerationEvent>();
        for (const event of [...retained, ...current]) {
            byKey.set(`${event.type}|${event.timestamp}|${event.completionStatus ?? ''}|${event.message ?? ''}`, event);
        }
        return [...byKey.values()].slice(-MAX_RETAINED_EVENTS);
    }

    private mergeSnapshots(current: ExerciseGenerationFileSnapshot[], retained: ExerciseGenerationFileSnapshot[]): ExerciseGenerationFileSnapshot[] {
        const byPath = new Map<string, ExerciseGenerationFileSnapshot>();
        for (const snapshot of [...retained, ...current]) {
            const key = this.snapshotKey(snapshot)!;
            const previous = byPath.get(key);
            byPath.set(key, previous ? this.newerSnapshot(previous, snapshot) : snapshot);
        }
        return [...byPath.values()];
    }

    private newerSnapshot(first: ExerciseGenerationFileSnapshot, second: ExerciseGenerationFileSnapshot): ExerciseGenerationFileSnapshot {
        if (second.turn !== first.turn) {
            return second.turn > first.turn ? second : first;
        }
        const firstTime = first.timestamp ? Date.parse(first.timestamp) : Number.NaN;
        const secondTime = second.timestamp ? Date.parse(second.timestamp) : Number.NaN;
        if (Number.isFinite(firstTime) && Number.isFinite(secondTime) && secondTime !== firstTime) {
            return secondTime > firstTime ? second : first;
        }
        return second;
    }

    private handleRevertResult(result: ExerciseGenerationRevertResult): void {
        if (!result.fullyReverted) {
            const repositories = result.revertedRepositories.join(', ') || '-';
            this.revertPartialRepositories.set(repositories);
            this.verdict.set(undefined);
            this.completionStatus.set(undefined);
            this.events.set([]);
            this.clearSnapshots();
            this.alertService.error('artemisApp.hyperion.generationActivity.revertPartialFailed', { repositories });
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
        this.clearSnapshots();
        this.generationReverted.emit(result.completedAt);
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

    private reset(): void {
        this.loadSequence++;
        this.closeStream();
        this.statusSubscription?.unsubscribe();
        this.statusSubscription = undefined;
        this.statusRequestInFlight = false;
        this.pendingStatusLoad = undefined;
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
        this.clearSnapshots();
    }

    private clearSnapshots(): void {
        this.snapshots.set([]);
    }

    private snapshotKey(snapshot: Pick<ExerciseGenerationFileSnapshot, 'repo' | 'path'> | undefined): string | undefined {
        if (!snapshot) {
            return undefined;
        }
        return `${snapshot.repo}\0${snapshot.path}`;
    }
}
