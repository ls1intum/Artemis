import { DestroyRef, Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, Subscription, interval, of, timer } from 'rxjs';
import { debounce, debounceTime, distinctUntilChanged, filter, map, switchMap } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationEvent, HyperionGenerationMode, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/*
 * This registry is deliberately client-owned.
 *
 * The server exposes no cross-exercise "list my generation runs" endpoint — only
 * `GET /api/hyperion/programming-exercises/{exerciseId}/generate-exercise/status`, which answers for a single
 * exercise. So the browser records every run it starts or observes, persists that list in `localStorage`
 * namespaced per login, and reconciles each non-terminal entry against the per-exercise status endpoint.
 * REST stays authoritative: websocket traffic only ever schedules a refresh, it never invents or mutates state.
 *
 * Follow-up: a server-side endpoint that lists the calling user's generation runs. Once it exists, the storage
 * layer here collapses into a plain fetch and this file loses the persistence, pruning and identity handling.
 */

/** Lifecycle of a single generation run as far as the navbar tray is concerned. */
export type HyperionJobStatus = 'queued' | 'running' | 'cancelling' | 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled' | 'unknown';

/** Aggregate state of every tracked run, used to decide whether and how the navbar indicator renders. */
export type HyperionJobIndicatorState = 'idle' | 'running' | 'attention' | 'success';

/** The facts known about a run at the moment it is started or first observed. */
export interface HyperionJobStart {
    jobId: string;
    exerciseId: number;
    courseId: number;
    exerciseTitle: string;
    mode: HyperionGenerationMode;
    /** ISO timestamp; defaults to now when omitted. */
    startedAt?: string;
}

/** A tracked run, as persisted and as rendered in the tray. */
export interface HyperionJobEntry {
    jobId: string;
    exerciseId: number;
    courseId: number;
    exerciseTitle: string;
    mode: HyperionGenerationMode;
    /** ISO timestamp of when the run was started or first observed. */
    startedAt: string;
    status: HyperionJobStatus;
    /** Whether the user has opened this run since it reached a terminal status. */
    seen: boolean;
    /** Last human-readable progress or result message reported by the server. */
    message?: string;
}

/** Statuses from which a run never moves again. */
const TERMINAL_STATUSES: ReadonlySet<HyperionJobStatus> = new Set<HyperionJobStatus>(['saved', 'needsReview', 'partial', 'failed', 'cancelled', 'unknown']);

/** How often a browser with at least one active run re-reads the per-exercise status endpoints. */
export const HYPERION_JOB_POLL_INTERVAL_MS = 30_000;

/** How long the indicator waits before appearing for the first time, so a run that fails immediately never flashes. */
export const HYPERION_JOB_APPEARANCE_DEBOUNCE_MS = 1_000;

/** How long websocket chatter is collected before it is turned into a single authoritative refresh. */
const WEBSOCKET_HINT_DEBOUNCE_MS = 500;

/** Runs older than this are dropped from storage; the status endpoint has long since moved on. */
const ENTRY_MAX_AGE_MS = 24 * 60 * 60 * 1000;

/** Upper bound on remembered dismissals, so storage cannot grow without limit. */
const MAX_DISMISSED = 100;

const STORAGE_KEY_PREFIX = 'artemis.hyperion.jobRegistry.';
const STORAGE_VERSION = 1;

interface PersistedRegistry {
    version: number;
    entries: HyperionJobEntry[];
    dismissed: string[];
}

/** Whether a run has reached a status it can no longer leave. */
export function isTerminalHyperionJobStatus(status: HyperionJobStatus): boolean {
    return TERMINAL_STATUSES.has(status);
}

/**
 * Client-owned registry of the AI exercise-generation runs this browser started or observed.
 *
 * Feeds the navbar indicator. See the file header for why the list lives on the client.
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

    /** Every tracked run, newest first. */
    readonly entries: Signal<readonly HyperionJobEntry[]> = this.entriesSignal.asReadonly();

    /** How many tracked runs have not reached a terminal status. */
    readonly activeCount: Signal<number> = computed(() => this.entriesSignal().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length);

    /** How many finished runs the user has neither opened nor dismissed. */
    readonly unseenCount: Signal<number> = computed(() => this.entriesSignal().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length);

    /**
     * Aggregate state driving the navbar indicator, debounced on first appearance.
     *
     * `running` while anything is active; otherwise `attention` when an unseen run ended in anything but a clean
     * save, `success` when an unseen run was saved, and `idle` when there is nothing worth showing.
     */
    readonly indicatorState: Signal<HyperionJobIndicatorState> = this.displayedIndicatorState.asReadonly();

    /** Whether the last reconciliation attempt failed. */
    readonly loadFailed: Signal<boolean> = this.loadFailedSignal.asReadonly();

    constructor() {
        // Poll only while something is actually running, and stop the moment nothing is.
        this.activeChanges
            .pipe(
                distinctUntilChanged(),
                switchMap((active) => (active ? interval(HYPERION_JOB_POLL_INTERVAL_MS) : EMPTY)),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());

        // A run whose terminal event was missed while the socket was down must still resolve, so re-read the
        // authoritative status every time the connection comes back up. Guards ls1intum/Artemis#13556.
        this.websocketService.connectionState
            .pipe(
                map((state) => state.connected),
                distinctUntilChanged(),
                filter((connected) => connected),
                takeUntilDestroyed(),
            )
            .subscribe(() => this.refresh());

        // Websocket events are only a hint: collect the chatter, then let REST decide what actually happened.
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
            // untracked so only the login itself is a dependency: a refreshed identity object for the same user
            // must not re-trigger a load, and nothing read during the load may become a dependency either.
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

    /** Drop a run from the tray for good; a later `track` for the same job is ignored. */
    dismiss(jobId: string): void {
        if (!this.dismissedJobIds.includes(jobId)) {
            this.dismissedJobIds = [jobId, ...this.dismissedJobIds].slice(0, MAX_DISMISSED);
        }
        this.setEntries(this.entriesSignal().filter((entry) => entry.jobId !== jobId));
    }

    /** Re-read the authoritative status of every non-terminal run. */
    refresh(): void {
        if (!this.loadedLogin) {
            // A logged-out or non-editor user must never reach the editor-only status endpoint.
            return;
        }
        const exerciseIds = new Set(
            this.entriesSignal()
                .filter((entry) => !isTerminalHyperionJobStatus(entry.status))
                .map((entry) => entry.exerciseId),
        );
        if (exerciseIds.size === 0) {
            return;
        }
        for (const exerciseId of exerciseIds) {
            this.generationService.getStatus(exerciseId).subscribe({
                next: (status) => {
                    this.loadFailedSignal.set(false);
                    this.reconcile(exerciseId, status ?? undefined);
                },
                // Never swallowed: a failed reconciliation is surfaced in the tray.
                error: () => this.loadFailedSignal.set(true),
            });
        }
    }

    /** Applies one exercise's authoritative status to every non-terminal entry of that exercise. */
    private reconcile(exerciseId: number, status: HyperionGenerationStatus | undefined): void {
        const next = this.entriesSignal().map((entry) => {
            if (entry.exerciseId !== exerciseId || isTerminalHyperionJobStatus(entry.status)) {
                return entry;
            }
            return cloneWith(entry, this.resolve(entry, status));
        });
        this.setEntries(next);
    }

    /** Derives the status of one entry from the exercise's current server status. */
    private resolve(entry: HyperionJobEntry, status: HyperionGenerationStatus | undefined): { status: HyperionJobStatus; message?: string } {
        if (!status || status.jobId !== entry.jobId) {
            // The exercise has no job any more, or has moved on to a newer one: this run ended without us seeing how.
            return { status: 'unknown' };
        }
        const events = status.events ?? [];
        const lastMessage = [...events].reverse().find((event) => !!event.message)?.message;
        if (status.running) {
            const cancelRequested = events.some((event) => event.type === 'CANCELLED');
            return { status: cancelRequested ? 'cancelling' : events.length > 0 ? 'running' : 'queued', message: lastMessage };
        }
        return { status: terminalStatusOf(events), message: lastMessage };
    }

    /** Single funnel for every entry mutation: prunes, sorts, persists and republishes derived state. */
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
                // Deliberately ignores the payload: an event only says "something changed, go ask REST".
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
            // A full or unavailable storage must not break the tray; the entries simply do not survive a reload.
        }
    }
}

/** Picks the terminal status implied by a finished run's events. */
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

/** Aggregates entries into the indicator state, before the appearance debounce is applied. */
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
        !Number.isNaN(Date.parse(entry.startedAt))
    );
}
