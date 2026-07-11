import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
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
    ExerciseAdaptationRevertResult,
    ExerciseGenerationFileSnapshot,
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
const LEGACY_TOOL_PROGRESS_PATTERN = /^Turn \d+:/;
const MAX_RETAINED_EVENTS = 50;
const MAX_STATUS_LOAD_ATTEMPTS = 3;

interface RepoFileGroup {
    repo: HyperionSnapshotRepo;
    files: ExerciseGenerationFileSnapshot[];
}

interface ActivityLiveStatus {
    message?: string;
    labelKey: string;
    busy: boolean;
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

    readonly exerciseId = input<number | undefined>();
    readonly refreshingEditor = input(false);
    readonly editorRefreshFailed = input(false);
    readonly editorRefreshRequested = output<void>();
    readonly adaptationReverted = output<string>();
    readonly generationCompleted = output<HyperionGenerationCompletedEvent>();
    readonly snapshotSelected = output<ExerciseGenerationFileSnapshot>();
    readonly startRequested = output<void>();

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

    readonly detailsExpanded = signal<boolean>(true);
    readonly cancelRequested = signal<boolean>(false);

    readonly reverting = signal<boolean>(false);
    readonly reverted = signal<boolean>(false);
    readonly revertPartialRepositories = signal<string | undefined>(undefined);

    readonly visible = computed(() => this.exerciseId() !== undefined);
    readonly idle = computed(() => this.jobId() === undefined && !this.statusLoading() && !this.statusLoadFailed() && !this.reverted());

    readonly titleLabelKey = computed(() => (this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adaptationTitle' : 'artemisApp.hyperion.generationActivity.title'));
    readonly runningLabelKey = computed(() => (this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adapting' : 'artemisApp.hyperion.generationActivity.running'));
    readonly canRevert = computed(() => !this.running() && !this.reverted() && this.revertAvailable());

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
            .filter((event) => event.message && !LEGACY_TOOL_PROGRESS_PATTERN.test(event.message))
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
            return { message: terminal.message, labelKey: `artemisApp.hyperion.generationActivity.terminalStatus.${terminal.type}`, busy: false };
        }
        if (this.statusLoading() && !this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.checkingStatus', busy: true };
        }
        if (this.running()) {
            return { message: this.currentProgress()?.message, labelKey: this.runningLabelKey(), busy: true };
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
    readonly persistenceState = computed(() => {
        if (this.running()) {
            return { labelKey: 'artemisApp.hyperion.generationActivity.persistence.workingCopy', severity: 'warn' as const };
        }
        const terminal = this.latestTerminalEvent(this.events());
        if (terminal?.type === 'DONE') {
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
    private streamLossRefreshTimeout?: ReturnType<typeof setTimeout>;
    private revertAvailabilityRefreshTimeout?: ReturnType<typeof setTimeout>;
    private statusLoadAttempts = 0;
    private loadedExerciseId?: number;
    private loadToken = 0;
    private liveMessageVersion = 0;
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
        this.streamSubscription?.unsubscribe();
        this.clearStreamLossRefresh();
        this.clearRevertAvailabilityRefresh();
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
        this.openStream(jobId);
        this.loadStatus(exerciseId, jobId);
    }

    confirmRevert(): void {
        if (!this.canRevert() || this.reverting()) {
            return;
        }
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.hyperion.generationActivity.revertConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.generationActivity.revertConfirmMessage'),
            rejectButtonProps: {
                label: this.translateService.instant('entity.action.cancel'),
                severity: 'secondary',
            },
            acceptButtonProps: {
                label: this.translateService.instant('artemisApp.hyperion.generationActivity.revert'),
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
        if (id === undefined || !this.canRevert() || this.reverting()) {
            return;
        }
        this.reverting.set(true);
        this.service.revertAdaptation(id).subscribe({
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
                this.alertService.error('artemisApp.hyperion.generationActivity.revertFailed');
            },
        });
    }

    selectFile(snapshot: ExerciseGenerationFileSnapshot): void {
        if (!this.canNavigateSnapshot(snapshot)) {
            return;
        }
        this.snapshotSelected.emit(snapshot);
    }

    requestStart(): void {
        this.startRequested.emit();
    }

    cancel(): void {
        const id = this.exerciseId();
        const job = this.jobId();
        if (id === undefined || job === undefined || this.cancelRequested()) {
            return;
        }
        this.cancelRequested.set(true);
        this.service.cancel(id, job).subscribe({
            error: () => {
                this.cancelRequested.set(false);
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
        this.statusLoadFailed.set(false);
        this.loadStatus(id, this.jobId());
    }

    private loadStatus(exerciseId: number, expectedJobId?: string): void {
        const token = ++this.loadToken;
        const liveMessageVersion = this.liveMessageVersion;
        this.statusLoading.set(true);
        this.service.getStatus(exerciseId).subscribe({
            next: (response) => {
                if (token !== this.loadToken) {
                    return;
                }
                this.statusLoading.set(false);
                this.statusLoadFailed.set(false);
                this.statusLoadAttempts = 0;
                const status = response.body ?? undefined;
                if (!status) {
                    if (expectedJobId === undefined) {
                        this.reset();
                    }
                    return;
                }
                if (expectedJobId !== undefined && status.jobId !== expectedJobId) {
                    return;
                }
                const sameJob = this.jobId() === status.jobId;
                const wasActivelyObserved = sameJob && this.running();
                this.jobId.set(status.jobId);
                this.mode.set(status.mode ?? this.mode());
                this.revertAvailable.set(status.revertAvailable ?? false);
                const events = this.mergeEvents(sameJob ? this.events() : [], status.events ?? []);
                this.events.set(events);
                const fileSnapshots = status.fileSnapshots ?? [];
                const snapshots = this.mergeSnapshots(sameJob ? this.snapshots() : [], fileSnapshots);
                this.snapshots.set(snapshots);
                const terminalEvent = this.latestTerminalEvent(events);
                if (terminalEvent) {
                    this.restoreTerminalState(terminalEvent);
                    this.running.set(false);
                    if (!sameJob) {
                        this.detailsExpanded.set(false);
                    }
                    if (wasActivelyObserved) {
                        this.emitGenerationCompleted(status.jobId, terminalEvent);
                    }
                    return;
                }
                this.running.set(status.running);
                if (status.running) {
                    this.openStream(status.jobId);
                }
            },
            error: (error: unknown) => {
                if (token === this.loadToken && liveMessageVersion === this.liveMessageVersion) {
                    this.statusLoadAttempts++;
                    if (this.isRetryableStatusError(error) && this.statusLoadAttempts < MAX_STATUS_LOAD_ATTEMPTS) {
                        this.refreshStatusAfterStreamLoss(1_000 * 2 ** (this.statusLoadAttempts - 1));
                    } else {
                        this.statusLoading.set(false);
                        this.statusLoadFailed.set(true);
                    }
                }
            },
        });
    }

    private isRetryableStatusError(error: unknown): boolean {
        return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500;
    }

    private openStream(jobId: string): void {
        this.streamSubscription?.unsubscribe();
        this.streamSubscription = this.service.subscribeToStream(jobId).subscribe({
            next: (message) => this.handleMessage(message),
            error: () => this.refreshStatusAfterStreamLoss(),
            complete: () => this.refreshStatusAfterStreamLoss(),
        });
    }

    private handleMessage(message: HyperionGenerationMessage): void {
        this.liveMessageVersion++;
        this.statusLoading.set(false);
        this.statusLoadFailed.set(false);
        this.statusLoadAttempts = 0;
        this.clearStreamLossRefresh();
        if (isFileSnapshot(message)) {
            this.upsertSnapshot(message);
            return;
        }
        this.events.update((list) => [...list, message].slice(-MAX_RETAINED_EVENTS));
        if (TERMINAL_EVENT_TYPES.has(message.type)) {
            this.running.set(false);
            this.verdict.set(message.verdict);
            this.completionStatus.set(message.completionStatus);
            this.liveExerciseChanged.set(message.liveExerciseChanged);
            this.streamSubscription?.unsubscribe();
            this.streamSubscription = undefined;
            this.emitGenerationCompleted(this.jobId(), message);
            const exerciseId = this.exerciseId();
            const jobId = this.jobId();
            if (exerciseId !== undefined && jobId !== undefined) {
                this.refreshRevertAvailability(exerciseId, jobId);
            }
        }
    }

    private refreshRevertAvailability(exerciseId: number, jobId: string, retry = true): void {
        this.service.getStatus(exerciseId).subscribe({
            next: (response) => {
                const status = response.body ?? undefined;
                if (this.exerciseId() === exerciseId && this.jobId() === jobId && status?.jobId === jobId) {
                    const available = status.revertAvailable ?? false;
                    this.revertAvailable.set(available);
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
        this.clearRevertAvailabilityRefresh();
        this.revertAvailabilityRefreshTimeout = setTimeout(() => {
            this.revertAvailabilityRefreshTimeout = undefined;
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
            updated[index] = snapshot;
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
        if (event.timestamp) {
            completedEvent.completedAt = event.timestamp;
        }
        this.generationCompleted.emit(completedEvent);
    }

    private mergeEvents(current: HyperionGenerationEvent[], retained: HyperionGenerationEvent[]): HyperionGenerationEvent[] {
        const byKey = new Map<string, HyperionGenerationEvent>();
        for (const event of [...retained, ...current]) {
            byKey.set(`${event.type}|${event.timestamp ?? ''}|${event.completionStatus ?? ''}|${event.message ?? ''}`, event);
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

    private handleRevertResult(result: ExerciseAdaptationRevertResult): void {
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
        this.reverted.set(true);
        this.revertAvailable.set(false);
        this.jobId.set(undefined);
        this.verdict.set(undefined);
        this.completionStatus.set(undefined);
        this.liveExerciseChanged.set(undefined);
        this.events.set([]);
        this.clearSnapshots();
        this.adaptationReverted.emit(result.completedAt);
        this.alertService.success('artemisApp.hyperion.generationActivity.revertSuccess');
    }

    private isRevertResult(value: unknown): value is ExerciseAdaptationRevertResult {
        return (
            typeof value === 'object' &&
            value !== null &&
            typeof (value as ExerciseAdaptationRevertResult).fullyReverted === 'boolean' &&
            Array.isArray((value as ExerciseAdaptationRevertResult).revertedRepositories) &&
            typeof (value as ExerciseAdaptationRevertResult).completedAt === 'string'
        );
    }

    private refreshStatusAfterStreamLoss(delay = 1_000): void {
        if (this.streamLossRefreshTimeout !== undefined) {
            return;
        }
        this.streamLossRefreshTimeout = setTimeout(() => {
            this.streamLossRefreshTimeout = undefined;
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

    private clearRevertAvailabilityRefresh(): void {
        if (this.revertAvailabilityRefreshTimeout !== undefined) {
            clearTimeout(this.revertAvailabilityRefreshTimeout);
            this.revertAvailabilityRefreshTimeout = undefined;
        }
    }

    private reset(): void {
        this.loadToken++;
        this.streamSubscription?.unsubscribe();
        this.streamSubscription = undefined;
        this.clearStreamLossRefresh();
        this.clearRevertAvailabilityRefresh();
        this.jobId.set(undefined);
        this.mode.set(undefined);
        this.running.set(false);
        this.statusLoading.set(false);
        this.statusLoadFailed.set(false);
        this.statusLoadAttempts = 0;
        this.reverting.set(false);
        this.reverted.set(false);
        this.revertPartialRepositories.set(undefined);
        this.detailsExpanded.set(true);
        this.emittedTerminalJobs.clear();
        this.events.set([]);
        this.verdict.set(undefined);
        this.completionStatus.set(undefined);
        this.liveExerciseChanged.set(undefined);
        this.revertAvailable.set(false);
        this.cancelRequested.set(false);
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
