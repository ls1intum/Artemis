import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import type * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faCircleCheck, faCircleXmark, faSpinner, faThumbTack, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import {
    HyperionFileSnapshot,
    HyperionGenerationCompletionStatus,
    HyperionGenerationEvent,
    HyperionGenerationMessage,
    HyperionGenerationVerdict,
    HyperionSnapshotRepo,
    isFileSnapshot,
} from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const REPO_ORDER: HyperionSnapshotRepo[] = ['solution', 'template', 'tests', 'other'];
const TERMINAL_EVENT_TYPES = new Set<HyperionGenerationEvent['type']>(['DONE', 'CANCELLED', 'ERROR']);

interface RepoFileGroup {
    repo: HyperionSnapshotRepo;
    files: HyperionFileSnapshot[];
}

/**
 * Owner-only "Generation activity" drawer embedded in the editor while an agentic whole-exercise generation/adaptation runs. It fetches the current run status for reconnect,
 * subscribes to the live progress + whole-file-snapshot stream, and renders a phase timeline, a live file tree (folded from the stream), a read-only auto-following Monaco preview
 * (text-only sink — the file contents are LLM-authored and untrusted), a terminal verdict, and a cancel button. It self-hides when there is no run to show.
 */
@Component({
    selector: 'jhi-hyperion-generation-activity',
    templateUrl: './hyperion-generation-activity.component.html',
    styleUrl: './hyperion-generation-activity.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ButtonModule, TagModule, TooltipModule, MonacoEditorComponent],
})
export class HyperionGenerationActivityComponent implements OnDestroy {
    private readonly service = inject(HyperionExerciseGenerationService);

    /** The exercise whose active run to show; the drawer self-hides while it is undefined or no run is retained. */
    readonly exerciseId = input<number | undefined>();

    private readonly monacoPreview = viewChild(MonacoEditorComponent);

    // Run state, folded from the status endpoint and the live stream.
    readonly jobId = signal<string | undefined>(undefined);
    readonly running = signal<boolean>(false);
    readonly events = signal<HyperionGenerationEvent[]>([]);
    readonly snapshots = signal<HyperionFileSnapshot[]>([]);
    readonly completionStatus = signal<HyperionGenerationCompletionStatus | undefined>(undefined);
    readonly verdict = signal<HyperionGenerationVerdict | undefined>(undefined);
    readonly terminalMessage = signal<string | undefined>(undefined);

    // Preview navigation.
    readonly follow = signal<boolean>(true);
    readonly pinnedPath = signal<string | undefined>(undefined);
    readonly cancelRequested = signal<boolean>(false);

    /** The drawer is visible only once a run (live or recently finished) is known. */
    readonly visible = computed(() => this.jobId() !== undefined);

    readonly progressEvents = computed(() => this.events().filter((event) => event.type === 'STARTED' || event.type === 'PROGRESS' || TERMINAL_EVENT_TYPES.has(event.type)));

    readonly filesByRepo = computed<RepoFileGroup[]>(() => {
        const files = this.snapshots();
        return REPO_ORDER.map((repo) => ({ repo, files: files.filter((file) => file.repo === repo) })).filter((group) => group.files.length > 0);
    });

    /** The file currently shown in the preview: the pinned file, otherwise the most-recently-written file while following. */
    readonly activeSnapshot = computed<HyperionFileSnapshot | undefined>(() => {
        const files = this.snapshots();
        if (!this.follow()) {
            const pinned = this.pinnedPath();
            return files.find((file) => file.path === pinned);
        }
        return files.length ? files[files.length - 1] : undefined;
    });

    readonly activePath = computed(() => this.activeSnapshot()?.path);

    protected readonly faSpinner = faSpinner;
    protected readonly faBan = faBan;
    protected readonly faThumbTack = faThumbTack;
    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    private streamSubscription?: Subscription;
    private loadedExerciseId?: number;
    private changedLineDecorations?: monaco.editor.IEditorDecorationsCollection;
    private readonly previousContentByPath = new Map<string, string>();

    constructor() {
        // Reconnect whenever the target exercise changes: fetch its status, rehydrate the preview, and resume the stream if it is still running.
        effect(() => {
            const id = this.exerciseId();
            if (id === undefined || id === this.loadedExerciseId) {
                return;
            }
            this.loadedExerciseId = id;
            this.reset();
            this.loadStatus(id);
        });
        // Render the active file into the read-only preview whenever it changes (text-only Monaco sink; never innerHTML — file contents are untrusted).
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
    }

    /** Follows the agent again (jumping back to the latest file) or, when turning follow off, pins the current file so the preview stops auto-swapping. */
    toggleFollow(): void {
        const nowFollowing = !this.follow();
        this.follow.set(nowFollowing);
        if (!nowFollowing) {
            this.pinnedPath.set(this.activePath());
        }
    }

    /** Pins a file: clicking it stops auto-following and shows it. */
    selectFile(path: string): void {
        this.follow.set(false);
        this.pinnedPath.set(path);
    }

    /** Requests cooperative cancellation of the running job (owner only). */
    cancel(): void {
        const id = this.exerciseId();
        const job = this.jobId();
        if (id === undefined || job === undefined || this.cancelRequested()) {
            return;
        }
        this.cancelRequested.set(true);
        this.service
            .cancel(id, job)
            .pipe()
            .subscribe({ error: () => this.cancelRequested.set(false) });
    }

    private loadStatus(exerciseId: number): void {
        this.service.getStatus(exerciseId).subscribe({
            next: (response) => {
                if (this.exerciseId() !== exerciseId) {
                    return; // A newer exercise was selected while this request was in flight.
                }
                const status = response.body ?? undefined;
                if (!status) {
                    this.reset();
                    return;
                }
                this.jobId.set(status.jobId);
                this.events.set(status.events ?? []);
                this.snapshots.set(status.fileSnapshots ?? []);
                this.restoreTerminalState(status.events ?? []);
                this.running.set(status.running);
                if (status.running) {
                    this.openStream(status.jobId);
                }
            },
            error: () => this.reset(),
        });
    }

    private openStream(jobId: string): void {
        this.streamSubscription?.unsubscribe();
        this.streamSubscription = this.service.subscribeToStream(jobId).subscribe({
            next: (message) => this.handleMessage(message),
            error: () => this.running.set(false),
        });
    }

    private handleMessage(message: HyperionGenerationMessage): void {
        if (isFileSnapshot(message)) {
            this.upsertSnapshot(message);
            return;
        }
        this.events.update((list) => [...list, message]);
        if (TERMINAL_EVENT_TYPES.has(message.type)) {
            this.running.set(false);
            this.completionStatus.set(message.completionStatus);
            this.verdict.set(message.verdict);
            this.terminalMessage.set(message.message);
        }
    }

    private upsertSnapshot(snapshot: HyperionFileSnapshot): void {
        this.snapshots.update((list) => {
            const index = list.findIndex((file) => file.path === snapshot.path);
            if (index < 0) {
                return [...list, snapshot];
            }
            const updated = list.slice();
            updated[index] = snapshot;
            return updated;
        });
    }

    private restoreTerminalState(events: HyperionGenerationEvent[]): void {
        for (let index = events.length - 1; index >= 0; index--) {
            const event = events[index];
            if (TERMINAL_EVENT_TYPES.has(event.type)) {
                this.completionStatus.set(event.completionStatus);
                this.verdict.set(event.verdict);
                this.terminalMessage.set(event.message);
                return;
            }
        }
    }

    /** Highlights the lines that changed versus the previously streamed content of this file, so an edit is visible at a glance. */
    private applyDiffDecorations(editor: MonacoEditorComponent, snapshot: HyperionFileSnapshot): void {
        const previous = this.previousContentByPath.get(snapshot.path);
        this.previousContentByPath.set(snapshot.path, snapshot.content);
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
        this.jobId.set(undefined);
        this.running.set(false);
        this.events.set([]);
        this.snapshots.set([]);
        this.completionStatus.set(undefined);
        this.verdict.set(undefined);
        this.terminalMessage.set(undefined);
        this.follow.set(true);
        this.pinnedPath.set(undefined);
        this.cancelRequested.set(false);
        this.changedLineDecorations?.clear();
        this.changedLineDecorations = undefined;
        this.previousContentByPath.clear();
    }
}
