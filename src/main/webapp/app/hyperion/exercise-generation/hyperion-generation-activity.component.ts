import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import type * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faCircleCheck, faCircleXmark, faRotateLeft, faSpinner, faThumbTack, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
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
const MAX_RETAINED_EVENTS = 50;

interface RepoFileGroup {
    repo: HyperionSnapshotRepo;
    files: ExerciseGenerationFileSnapshot[];
}

export interface HyperionGenerationCompletedEvent {
    mode?: HyperionGenerationMode;
    verdict?: HyperionGenerationVerdict;
    completionStatus?: HyperionGenerationCompletionStatus;
    liveExerciseChanged?: boolean;
}

@Component({
    selector: 'jhi-hyperion-generation-activity',
    templateUrl: './hyperion-generation-activity.component.html',
    styleUrl: './hyperion-generation-activity.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ButtonModule, TagModule, TooltipModule, MonacoEditorComponent],
})
export class HyperionGenerationActivityComponent implements OnDestroy {
    private readonly service = inject(HyperionExerciseGenerationService);
    private readonly alertService = inject(AlertService);

    readonly exerciseId = input<number | undefined>();
    readonly adaptationReverted = output<void>();
    readonly generationCompleted = output<HyperionGenerationCompletedEvent>();

    private readonly monacoPreview = viewChild(MonacoEditorComponent);

    readonly jobId = signal<string | undefined>(undefined);
    readonly mode = signal<HyperionGenerationMode | undefined>(undefined);
    readonly running = signal<boolean>(false);
    readonly events = signal<HyperionGenerationEvent[]>([]);
    readonly snapshots = signal<ExerciseGenerationFileSnapshot[]>([]);
    readonly verdict = signal<HyperionGenerationVerdict | undefined>(undefined);
    readonly completionStatus = signal<HyperionGenerationCompletionStatus | undefined>(undefined);

    readonly follow = signal<boolean>(true);
    readonly pinnedSnapshotKey = signal<string | undefined>(undefined);
    // The path of the file the agent last wrote (created OR re-edited in place). Following tracks this, not array order,
    // so re-editing an earlier file jumps the preview back to it (upsertSnapshot replaces in place, keeping array order).
    readonly lastWrittenSnapshotKey = signal<string | undefined>(undefined);
    readonly cancelRequested = signal<boolean>(false);

    // Revert affordance for a completed, accepted in-place adaptation.
    readonly reverting = signal<boolean>(false);
    readonly reverted = signal<boolean>(false);

    readonly visible = computed(() => this.jobId() !== undefined);

    readonly runningLabelKey = computed(() => (this.mode() === 'ADAPT' ? 'artemisApp.hyperion.generationActivity.adapting' : 'artemisApp.hyperion.generationActivity.running'));

    readonly canRevert = computed(() => this.mode() === 'ADAPT' && !this.running() && !this.reverted() && (this.verdict()?.accepted ?? false));

    readonly filesByRepo = computed<RepoFileGroup[]>(() => {
        const files = this.snapshots();
        return REPO_ORDER.map((repo) => ({ repo, files: files.filter((file) => file.repo === repo) })).filter((group) => group.files.length > 0);
    });

    readonly activeSnapshot = computed<ExerciseGenerationFileSnapshot | undefined>(() => {
        const files = this.snapshots();
        const targetKey = this.follow() ? this.lastWrittenSnapshotKey() : this.pinnedSnapshotKey();
        return files.find((file) => this.snapshotKey(file) === targetKey) ?? (this.follow() ? files.at(-1) : undefined);
    });

    readonly activePath = computed(() => this.activeSnapshot()?.path);

    protected readonly faSpinner = faSpinner;
    protected readonly faBan = faBan;
    protected readonly faThumbTack = faThumbTack;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faRotateLeft = faRotateLeft;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    private streamSubscription?: Subscription;
    private streamLossRefreshTimeout?: ReturnType<typeof setTimeout>;
    private loadedExerciseId?: number;
    // Monotonic token guarding the async status fetch: a newer load or a freshly attached live run (attachToJob) bumps it so a
    // late getStatus response cannot clobber the current run.
    private loadToken = 0;
    private changedLineDecorations?: monaco.editor.IEditorDecorationsCollection;
    private readonly previousContentByPath = new Map<string, string>();
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
        // Text-only Monaco sink; never innerHTML because file contents are LLM-authored.
        effect(() => {
            const snapshot = this.activeSnapshot();
            const editor = this.monacoPreview();
            if (!snapshot || !editor) {
                return;
            }
            editor.changeModel(snapshot.path, snapshot.content);
            this.applyDiffDecorations(editor, snapshot);
        });
    }

    ngOnDestroy(): void {
        this.streamSubscription?.unsubscribe();
        this.clearStreamLossRefresh();
    }

    attachToJob(jobId: string, mode: HyperionGenerationMode): void {
        if (this.exerciseId() === undefined) {
            return;
        }
        // Invalidate any in-flight reconnect status fetch so its late response cannot overwrite this freshly attached live run.
        this.loadToken++;
        this.reset();
        this.mode.set(mode);
        this.jobId.set(jobId);
        this.running.set(true);
        this.openStream(jobId);
        this.loadStatus(this.exerciseId()!, jobId);
    }

    revert(): void {
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

    /** Follows the agent again (jumping back to the latest file) or, when turning follow off, pins the current file so the preview stops auto-swapping. */
    toggleFollow(): void {
        const nowFollowing = !this.follow();
        this.follow.set(nowFollowing);
        if (!nowFollowing) {
            this.pinnedSnapshotKey.set(this.snapshotKey(this.activeSnapshot()));
        }
    }

    /** Pins a file: clicking it stops auto-following and shows it. */
    selectFile(path: string, repo?: HyperionSnapshotRepo): void {
        this.follow.set(false);
        const matchingSnapshot = repo ? undefined : this.snapshots().find((snapshot) => snapshot.path === path);
        this.pinnedSnapshotKey.set(this.snapshotKey(matchingSnapshot ?? (repo ? { repo, path } : undefined)));
    }

    /** Requests cooperative cancellation of the running job (owner only). */
    cancel(): void {
        const id = this.exerciseId();
        const job = this.jobId();
        if (id === undefined || job === undefined || this.cancelRequested()) {
            return;
        }
        this.cancelRequested.set(true);
        this.service.cancel(id, job).subscribe({ error: () => this.cancelRequested.set(false) });
    }

    private loadStatus(exerciseId: number, expectedJobId?: string): void {
        const token = ++this.loadToken;
        this.service.getStatus(exerciseId).subscribe({
            next: (response) => {
                if (token !== this.loadToken) {
                    return; // A newer load or a freshly attached live run superseded this status fetch.
                }
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
                this.jobId.set(status.jobId);
                this.mode.set(status.mode ?? this.mode());
                const events = this.mergeEvents(sameJob ? this.events() : [], status.events ?? []);
                this.events.set(events);
                const fileSnapshots = status.fileSnapshots ?? [];
                const snapshots = this.mergeSnapshots(sameJob ? this.snapshots() : [], fileSnapshots);
                this.snapshots.set(snapshots);
                this.lastWrittenSnapshotKey.set(this.snapshotKey(this.latestSnapshot(snapshots)));
                const terminalEvent = this.latestTerminalEvent(events);
                if (terminalEvent) {
                    this.restoreTerminalState(terminalEvent);
                    this.running.set(false);
                    this.emitGenerationCompleted(status.jobId, terminalEvent);
                    return;
                }
                this.running.set(status.running);
                if (status.running) {
                    this.openStream(status.jobId);
                }
            },
            error: () => {
                if (token === this.loadToken && expectedJobId === undefined) {
                    this.reset();
                }
            },
        });
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
        if (isFileSnapshot(message)) {
            this.upsertSnapshot(message);
            return;
        }
        this.events.update((list) => [...list, message].slice(-MAX_RETAINED_EVENTS));
        if (TERMINAL_EVENT_TYPES.has(message.type)) {
            this.running.set(false);
            this.verdict.set(message.verdict);
            this.completionStatus.set(message.completionStatus);
            this.streamSubscription?.unsubscribe();
            this.streamSubscription = undefined;
            this.emitGenerationCompleted(this.jobId(), message);
        }
    }

    private upsertSnapshot(snapshot: ExerciseGenerationFileSnapshot): void {
        this.lastWrittenSnapshotKey.set(this.snapshotKey(snapshot));
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
    }

    private emitGenerationCompleted(jobId: string | undefined, event: HyperionGenerationEvent): void {
        if (jobId === undefined || this.emittedTerminalJobs.has(jobId)) {
            return;
        }
        this.emittedTerminalJobs.add(jobId);
        this.generationCompleted.emit({
            mode: this.mode(),
            verdict: event.verdict,
            completionStatus: event.completionStatus,
            liveExerciseChanged: event.liveExerciseChanged,
        });
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

    private latestSnapshot(snapshots: ExerciseGenerationFileSnapshot[]): ExerciseGenerationFileSnapshot | undefined {
        return snapshots.reduce<ExerciseGenerationFileSnapshot | undefined>((latest, current) => {
            if (!latest) {
                return current;
            }
            const latestTime = latest.timestamp ? Date.parse(latest.timestamp) : Number.NaN;
            const currentTime = current.timestamp ? Date.parse(current.timestamp) : Number.NaN;
            if (Number.isFinite(currentTime) && (!Number.isFinite(latestTime) || currentTime >= latestTime)) {
                return current;
            }
            return latest;
        }, snapshots.at(-1));
    }

    protected completionSeverity(status: HyperionGenerationCompletionStatus): 'success' | 'warn' | 'danger' {
        if (status === 'SUCCESS') {
            return 'success';
        }
        if (status === 'NEEDS_REVIEW') {
            return 'warn';
        }
        return 'danger';
    }

    private handleRevertResult(result: ExerciseAdaptationRevertResult): void {
        if (!result.fullyReverted) {
            this.alertService.error('artemisApp.hyperion.generationActivity.revertPartialFailed', { repositories: result.revertedRepositories.join(', ') || '-' });
            return;
        }
        this.reverted.set(true);
        this.clearPreview();
        this.adaptationReverted.emit();
        this.alertService.success('artemisApp.hyperion.generationActivity.revertSuccess');
    }

    private isRevertResult(value: unknown): value is ExerciseAdaptationRevertResult {
        return (
            typeof value === 'object' &&
            value !== null &&
            typeof (value as ExerciseAdaptationRevertResult).fullyReverted === 'boolean' &&
            Array.isArray((value as ExerciseAdaptationRevertResult).revertedRepositories)
        );
    }

    private refreshStatusAfterStreamLoss(): void {
        if (this.streamLossRefreshTimeout !== undefined) {
            return;
        }
        this.streamLossRefreshTimeout = setTimeout(() => {
            this.streamLossRefreshTimeout = undefined;
            const id = this.exerciseId();
            if (id !== undefined && this.running()) {
                this.loadStatus(id);
            }
        }, 1_000);
    }

    private clearStreamLossRefresh(): void {
        if (this.streamLossRefreshTimeout !== undefined) {
            clearTimeout(this.streamLossRefreshTimeout);
            this.streamLossRefreshTimeout = undefined;
        }
    }

    /** Cosmetic gutter hint marking lines that changed versus the previous streamed content of this file. Heuristic (line-set membership), so moved/duplicate lines can be missed. */
    private applyDiffDecorations(editor: MonacoEditorComponent, snapshot: ExerciseGenerationFileSnapshot): void {
        const key = this.snapshotKey(snapshot)!;
        const previous = this.previousContentByPath.get(key);
        this.previousContentByPath.set(key, snapshot.content);
        this.changedLineDecorations?.clear();
        this.changedLineDecorations = undefined;
        if (snapshot.action !== 'edit' || previous === undefined || previous === snapshot.content) {
            return;
        }
        const changedLines = this.changedLineNumbers(previous, snapshot.content);
        if (!changedLines.length) {
            return;
        }
        const decorations: monaco.editor.IModelDeltaDecoration[] = changedLines.map((line) => ({
            range: { startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1 },
            options: { isWholeLine: true, className: 'hyperion-snapshot-changed-line', linesDecorationsClassName: 'hyperion-snapshot-changed-gutter' },
        }));
        this.changedLineDecorations = editor.getEditor().createDecorationsCollection(decorations);
    }

    /** Cheap line-level diff: the current line numbers whose text is not present anywhere in the previous content. */
    private changedLineNumbers(previous: string, current: string): number[] {
        const previousLines = new Set(previous.split('\n'));
        const changed: number[] = [];
        current.split('\n').forEach((line, index) => {
            if (!previousLines.has(line)) {
                changed.push(index + 1);
            }
        });
        return changed;
    }

    private reset(): void {
        this.streamSubscription?.unsubscribe();
        this.streamSubscription = undefined;
        this.clearStreamLossRefresh();
        this.jobId.set(undefined);
        this.mode.set(undefined);
        this.running.set(false);
        this.reverting.set(false);
        this.reverted.set(false);
        this.emittedTerminalJobs.clear();
        this.events.set([]);
        this.verdict.set(undefined);
        this.completionStatus.set(undefined);
        this.follow.set(true);
        this.cancelRequested.set(false);
        this.clearPreview();
    }

    private clearPreview(): void {
        this.snapshots.set([]);
        this.pinnedSnapshotKey.set(undefined);
        this.lastWrittenSnapshotKey.set(undefined);
        this.changedLineDecorations?.clear();
        this.changedLineDecorations = undefined;
        this.monacoPreview()?.changeModel('', '');
        this.previousContentByPath.clear();
    }

    private snapshotKey(snapshot: Pick<ExerciseGenerationFileSnapshot, 'repo' | 'path'> | undefined): string | undefined {
        if (!snapshot) {
            return undefined;
        }
        return this.snapshotKeyForParts(snapshot.repo, snapshot.path);
    }

    private snapshotKeyForParts(repo: HyperionSnapshotRepo | undefined, path: string | undefined): string | undefined {
        if (!path) {
            return undefined;
        }
        return `${repo ?? ''}\0${path}`;
    }
}
