import { DestroyRef, Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, Subscription, interval, of, timer } from 'rxjs';
import { debounce, debounceTime, distinctUntilChanged, filter, map, switchMap, takeUntil } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationEvent, HyperionGenerationMode, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

export type HyperionJobStatus = 'queued' | 'running' | 'cancelling' | 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled' | 'unknown';

export type HyperionJobIndicatorState = 'idle' | 'running' | 'attention' | 'success';

export interface HyperionJobStart {
    jobId: string;
    exerciseId: number;
    courseId: number;
    exerciseTitle: string;
    mode: HyperionGenerationMode;
    /** ISO timestamp; defaults to now when omitted. */
    startedAt?: string;
}

export interface HyperionJobEntry {
    jobId: string;
    exerciseId: number;
    courseId: number;
    exerciseTitle: string;
    mode: HyperionGenerationMode;
    /** ISO timestamp of when the run was started or first observed. */
    startedAt: string;
    /** Server terminal-event timestamp; absent when the outcome is unknown. Never use reconciliation time. */
    endedAt?: string;
    status: HyperionJobStatus;
    /** Whether the user has opened this run since it reached a terminal status. */
    seen: boolean;
    /** Last human-readable progress or result message reported by the server. */
    message?: string;
}

const TERMINAL_STATUSES: ReadonlySet<HyperionJobStatus> = new Set<HyperionJobStatus>(['saved', 'needsReview', 'partial', 'failed', 'cancelled', 'unknown']);

export const HYPERION_JOB_POLL_INTERVAL_MS = 30_000;

/** How long the indicator waits before appearing for the first time, so a run that fails immediately never flashes. */
export const HYPERION_JOB_APPEARANCE_DEBOUNCE_MS = 1_000;

const WEBSOCKET_HINT_DEBOUNCE_MS = 500;

const ENTRY_MAX_AGE_MS = 24 * 60 * 60 * 1000;

const MAX_DISMISSED = 100;

const STORAGE_KEY_PREFIX = 'artemis.hyperion.jobRegistry.';
const STORAGE_VERSION = 1;

interface PersistedRegistry {
    version: number;
    entries: HyperionJobEntry[];
    dismissed: string[];
}

export function isTerminalHyperionJobStatus(status: HyperionJobStatus): boolean {
    return TERMINAL_STATUSES.has(status);
}

/**
 * Browser-local run history, stored per login. REST provides authoritative status per exercise;
 * websocket events trigger reconciliation. Runs started in another browser appear only when observed here.
 */
@Injectable({ providedIn: 'root' })
export class HyperionJobRegistryService {
    private readonly accountService = inject(AccountService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly websocketService = inject(WebsocketService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly entriesSignal = signal<readonly HyperionJobEntry[]>([]);
    private readonly loadFailedSignal = signal(false);
    private readonly displayedIndicatorState = signal<HyperionJobIndicatorState>('idle');

    /** The login whose entries are currently loaded; `undefined` while logged out. */
    private loadedLogin?: string;
    private dismissedJobIds: string[] = [];
    private readonly streamSubscriptions = new Map<string, Subscription>();

    private readonly indicatorStateChanges = new Subject<HyperionJobIndicatorState>();
    private readonly activeChanges = new Subject<boolean>();
    private readonly websocketHints = new Subject<void>();
    private readonly identityChanged = new Subject<void>();

    /** Every tracked run, newest first. */
    readonly entries: Signal<readonly HyperionJobEntry[]> = this.entriesSignal.asReadonly();

    readonly activeCount: Signal<number> = computed(() => this.entriesSignal().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length);

    readonly unseenCount: Signal<number> = computed(() => this.entriesSignal().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length);

    /** Navbar state, delayed only when appearing from idle. */
    readonly indicatorState: Signal<HyperionJobIndicatorState> = this.displayedIndicatorState.asReadonly();

    readonly loadFailed: Signal<boolean> = this.loadFailedSignal.asReadonly();

    constructor() {
        this.activeChanges
            .pipe(
                distinctUntilChanged(),
                switchMap((active) => (active ? interval(HYPERION_JOB_POLL_INTERVAL_MS) : EMPTY)),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());

        // Recover terminal events missed while disconnected.
        this.websocketService.connectionState
            .pipe(
                map((state) => state.connected),
                distinctUntilChanged(),
                filter((connected) => connected),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());

        this.websocketHints.pipe(debounceTime(WEBSOCKET_HINT_DEBOUNCE_MS), takeUntilDestroyed()).subscribe(() => this.refresh());

        // Delay the very first appearance so a run that fails within a second never flashes the navbar. Once the
        // indicator is visible, every further transition is applied immediately.
        this.indicatorStateChanges
            .pipe(
                debounce((state) => (state !== 'idle' && untracked(this.displayedIndicatorState) === 'idle' ? timer(HYPERION_JOB_APPEARANCE_DEBOUNCE_MS) : of(0))),
                takeUntilDestroyed(),
            )
            .subscribe((state) => this.displayedIndicatorState.set(state));

        effect(() => {
            const login = this.accountService.userIdentity()?.login;
            // Registry reads during loading must not retrigger this identity effect.
            untracked(() => this.onLoginChanged(login));
        });

        this.destroyRef.onDestroy(() => this.clearStreamSubscriptions());
    }

    /**
     * Record a run that has just been started or observed in this browser.
     *
     * Ignores runs the user already dismissed and runs that are already tracked.
     */
    track(start: HyperionJobStart): void {
        if (!this.loadedLogin || this.dismissedJobIds.includes(start.jobId)) {
            return;
        }
        if (this.entriesSignal().some((entry) => entry.jobId === start.jobId)) {
            return;
        }
        const entry: HyperionJobEntry = {
            jobId: start.jobId,
            exerciseId: start.exerciseId,
            courseId: start.courseId,
            exerciseTitle: start.exerciseTitle,
            mode: start.mode,
            startedAt: start.startedAt ?? new Date().toISOString(),
            status: 'queued',
            seen: false,
        };
        this.setEntries([entry, ...this.entriesSignal()]);
    }

    /** Mark a finished run as opened, which clears it from the unseen badge. */
    markSeen(jobId: string): void {
        const next = this.entriesSignal().map((entry) => (entry.jobId === jobId && !entry.seen ? cloneWith(entry, { seen: true }) : entry));
        this.setEntries(next);
    }

    /** Remove a run from the tray and remember its dismissal in the bounded history. */
    dismiss(jobId: string): void {
        if (!this.dismissedJobIds.includes(jobId)) {
            this.dismissedJobIds = [jobId, ...this.dismissedJobIds].slice(0, MAX_DISMISSED);
        }
        this.setEntries(this.entriesSignal().filter((entry) => entry.jobId !== jobId));
    }

    /** Re-read the authoritative status of every non-terminal run. */
    refresh(): void {
        if (!this.loadedLogin) {
            return;
        }
        const activeEntries = this.entriesSignal().filter((entry) => !isTerminalHyperionJobStatus(entry.status));
        const exerciseIds = new Set(activeEntries.map((entry) => entry.exerciseId));
        if (exerciseIds.size === 0) {
            return;
        }
        const login = this.loadedLogin;
        for (const exerciseId of exerciseIds) {
            const requestedJobIds = new Set(activeEntries.filter((entry) => entry.exerciseId === exerciseId).map((entry) => entry.jobId));
            this.generationService
                .getStatus(exerciseId)
                .pipe(takeUntil(this.identityChanged), takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: (status) => {
                        if (this.accountService.userIdentity()?.login !== login) {
                            return;
                        }
                        this.loadFailedSignal.set(false);
                        this.reconcile(requestedJobIds, status ?? undefined);
                    },
                    error: () => {
                        if (this.accountService.userIdentity()?.login === login) {
                            this.loadFailedSignal.set(true);
                        }
                    },
                });
        }
    }

    /** Reconciles only the jobs included in the request, not jobs tracked while it was in flight. */
    private reconcile(requestedJobIds: ReadonlySet<string>, status: HyperionGenerationStatus | undefined): void {
        const next = this.entriesSignal().map((entry) => {
            if (!requestedJobIds.has(entry.jobId) || isTerminalHyperionJobStatus(entry.status)) {
                return entry;
            }
            return cloneWith(entry, this.resolve(entry, status));
        });
        this.setEntries(next);
    }

    private resolve(entry: HyperionJobEntry, status: HyperionGenerationStatus | undefined): { status: HyperionJobStatus; message?: string; endedAt?: string } {
        if (!status || status.jobId !== entry.jobId) {
            // The outcome is unknown; do not infer an end time from this response.
            return { status: 'unknown' };
        }
        const events = status.events ?? [];
        const lastMessage = [...events].reverse().find((event) => !!event.message)?.message;
        if (status.running) {
            const cancelRequested = events.some((event) => event.type === 'CANCELLED');
            return { status: cancelRequested ? 'cancelling' : events.length > 0 ? 'running' : 'queued', message: lastMessage };
        }
        return { status: terminalStatusOf(events), message: lastMessage, endedAt: terminalTimestampOf(events) };
    }

    private setEntries(entries: readonly HyperionJobEntry[]): void {
        const cutoff = Date.now() - ENTRY_MAX_AGE_MS;
        const next = entries
            .filter((entry) => !this.dismissedJobIds.includes(entry.jobId) && Date.parse(entry.startedAt) >= cutoff)
            .toSorted((a, b) => Date.parse(b.startedAt) - Date.parse(a.startedAt));
        this.entriesSignal.set(next);
        this.persist();
        this.syncStreamSubscriptions(next);
        this.activeChanges.next(next.some((entry) => !isTerminalHyperionJobStatus(entry.status)));
        this.indicatorStateChanges.next(computeIndicatorState(next));
    }

    /** Keeps exactly one websocket subscription per non-terminal run. */
    private syncStreamSubscriptions(entries: readonly HyperionJobEntry[]): void {
        const wanted = new Set(entries.filter((entry) => !isTerminalHyperionJobStatus(entry.status)).map((entry) => entry.jobId));
        for (const [jobId, subscription] of this.streamSubscriptions) {
            if (!wanted.has(jobId)) {
                subscription.unsubscribe();
                this.streamSubscriptions.delete(jobId);
            }
        }
        for (const jobId of wanted) {
            if (!this.streamSubscriptions.has(jobId)) {
                this.streamSubscriptions.set(
                    jobId,
                    this.generationService.subscribeToStream(jobId).subscribe(() => this.websocketHints.next()),
                );
            }
        }
    }

    private clearStreamSubscriptions(): void {
        for (const subscription of this.streamSubscriptions.values()) {
            subscription.unsubscribe();
        }
        this.streamSubscriptions.clear();
    }

    /** Loads (or clears) the registry when the logged-in user changes. Does nothing for a repeated login. */
    private onLoginChanged(login: string | undefined): void {
        if (login !== this.loadedLogin) {
            this.identityChanged.next();
        }
        if (!login) {
            this.loadedLogin = undefined;
            this.dismissedJobIds = [];
            this.clearStreamSubscriptions();
            this.entriesSignal.set([]);
            this.loadFailedSignal.set(false);
            this.activeChanges.next(false);
            this.indicatorStateChanges.next('idle');
            return;
        }
        if (login === this.loadedLogin) {
            return;
        }
        this.loadedLogin = login;
        const persisted = readStorage(login);
        this.dismissedJobIds = persisted.dismissed;
        this.setEntries(persisted.entries);
        this.refresh();
    }

    private persist(): void {
        if (!this.loadedLogin) {
            return;
        }
        const payload: PersistedRegistry = { version: STORAGE_VERSION, entries: [...this.entriesSignal()], dismissed: this.dismissedJobIds };
        try {
            localStorage.setItem(STORAGE_KEY_PREFIX + this.loadedLogin, JSON.stringify(payload));
        } catch {
            // Keep in-memory history usable when browser storage is unavailable.
        }
    }
}

function terminalTimestampOf(events: readonly HyperionGenerationEvent[]): string | undefined {
    const timestamp = [...events].reverse().find((event) => event.type === 'DONE' || event.type === 'CANCELLED' || event.type === 'ERROR')?.timestamp;
    return timestamp !== undefined && Number.isFinite(Date.parse(timestamp)) ? timestamp : undefined;
}

function terminalStatusOf(events: readonly HyperionGenerationEvent[]): HyperionJobStatus {
    const last = [...events].reverse().find((event) => event.type === 'DONE' || event.type === 'CANCELLED' || event.type === 'ERROR');
    switch (last?.type) {
        case 'ERROR':
            return 'failed';
        case 'CANCELLED':
            return 'cancelled';
        case 'DONE':
            switch (last.completionStatus) {
                case 'NEEDS_REVIEW':
                    return 'needsReview';
                case 'PARTIAL':
                    return 'partial';
                default:
                    return 'saved';
            }
        default:
            return 'unknown';
    }
}

function computeIndicatorState(entries: readonly HyperionJobEntry[]): HyperionJobIndicatorState {
    if (entries.some((entry) => !isTerminalHyperionJobStatus(entry.status))) {
        return 'running';
    }
    const unseen = entries.filter((entry) => !entry.seen);
    if (unseen.some((entry) => entry.status !== 'saved')) {
        return 'attention';
    }
    return unseen.length > 0 ? 'success' : 'idle';
}

/** Reads and validates one login's persisted registry, tolerating anything a previous version may have written. */
function readStorage(login: string): { entries: HyperionJobEntry[]; dismissed: string[] } {
    let raw: string | null;
    try {
        raw = localStorage.getItem(STORAGE_KEY_PREFIX + login);
    } catch {
        return { entries: [], dismissed: [] };
    }
    if (!raw) {
        return { entries: [], dismissed: [] };
    }
    try {
        const parsed = parseJson<Partial<PersistedRegistry> | undefined>(raw);
        if (!parsed || parsed.version !== STORAGE_VERSION) {
            return { entries: [], dismissed: [] };
        }
        const entries = Array.isArray(parsed.entries) ? parsed.entries.filter(isPersistedEntry) : [];
        const dismissed = Array.isArray(parsed.dismissed) ? parsed.dismissed.filter((jobId): jobId is string => typeof jobId === 'string') : [];
        return { entries, dismissed: dismissed.slice(0, MAX_DISMISSED) };
    } catch {
        return { entries: [], dismissed: [] };
    }
}

function isPersistedEntry(candidate: unknown): candidate is HyperionJobEntry {
    const entry = candidate as Partial<HyperionJobEntry> | undefined;
    return (
        !!entry &&
        typeof entry.jobId === 'string' &&
        typeof entry.exerciseId === 'number' &&
        typeof entry.courseId === 'number' &&
        typeof entry.exerciseTitle === 'string' &&
        typeof entry.startedAt === 'string' &&
        typeof entry.status === 'string' &&
        (entry.endedAt === undefined || typeof entry.endedAt === 'string') &&
        !Number.isNaN(Date.parse(entry.startedAt))
    );
}
