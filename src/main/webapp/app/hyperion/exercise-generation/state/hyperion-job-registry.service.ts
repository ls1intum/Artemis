import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, Subscription, defer, from, interval } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter, finalize, map, mergeMap, switchMap, take, takeUntil, tap, timeout } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { AuthoringRun } from 'app/openapi/model/authoring-run';
import { HyperionGenerationEvent, HyperionGenerationMode, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

export type HyperionJobStatus = 'queued' | 'running' | 'cancelling' | 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled' | 'unknown' | 'reverted';

export interface HyperionJobStart {
    jobId: string;
    exerciseId: number;
    /** Different from exerciseId when this run creates a variant. */
    sourceExerciseId?: number;
    kind?: 'CREATE' | 'ADAPT' | 'VARIANT';
    courseId: number;
    exerciseTitle: string;
    mode: HyperionGenerationMode;
    /** ISO timestamp; defaults to now when omitted. */
    startedAt?: string;
}

export interface HyperionJobEntry {
    jobId: string;
    exerciseId: number;
    /** Different from exerciseId when this run creates a variant. */
    sourceExerciseId?: number;
    kind?: 'CREATE' | 'ADAPT' | 'VARIANT';
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
    /** Last server-reported phase; never inferred from message text. */
    phase?: HyperionGenerationEvent['phase'];
    /** Authoritative server permission, absent until reconciliation. */
    cancellable?: boolean;
}

const TERMINAL_STATUSES: ReadonlySet<HyperionJobStatus> = new Set<HyperionJobStatus>(['saved', 'needsReview', 'partial', 'failed', 'cancelled', 'unknown', 'reverted']);

export const HYPERION_JOB_POLL_INTERVAL_MS = 30_000;

const WEBSOCKET_HINT_DEBOUNCE_MS = 500;
const MAX_CONCURRENT_STATUS_REQUESTS = 4;
const STATUS_REQUEST_TIMEOUT_MS = 10_000;
const MAX_STREAM_SUBSCRIPTIONS = 20;
const MAX_ACCESS_BATCH_SIZE = 500;

const STORAGE_KEY_PREFIX = 'artemis.hyperion.jobRegistry.';
const STORAGE_VERSION = 2;

interface PersistedRegistry {
    version: number;
    seenJobIds: string[];
}

export function isTerminalHyperionJobStatus(status: HyperionJobStatus): boolean {
    return TERMINAL_STATUSES.has(status);
}

/**
 * Server-backed owner history and live progress. Only acknowledgement ids are stored in this browser;
 * exercise identities and outcomes are discovered anew after each login.
 */
@Injectable({ providedIn: 'root' })
export class HyperionJobRegistryService {
    private readonly accountService = inject(AccountService);
    private readonly generationService = inject(HyperionExerciseGenerationService);
    private readonly websocketService = inject(WebsocketService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly entriesSignal = signal<readonly HyperionJobEntry[]>([]);
    private readonly loadFailedSignal = signal(false);
    private readonly historyFailed = signal(false);
    private readonly accessFailed = signal(false);
    private revalidatingAccess = false;
    private accessOffset = 0;
    private readonly nextBeforeId = signal<number | undefined>(undefined);
    private seenJobIds = new Set<string>();
    private historyLoading = false;
    readonly hasMoreHistory = computed(() => this.nextBeforeId() !== undefined);

    /** The login whose entries are currently loaded; `undefined` while logged out. */
    private loadedLogin?: string;
    private readonly streamSubscriptions = new Map<string, Subscription>();

    private readonly activeChanges = new Subject<boolean>();
    private readonly websocketHints = new Subject<void>();
    private readonly identityChanged = new Subject<void>();
    private refreshing = false;

    /** Every tracked run, newest first. */
    readonly entries: Signal<readonly HyperionJobEntry[]> = this.entriesSignal.asReadonly();

    readonly loadFailed = computed(() => this.loadFailedSignal() || this.historyFailed() || this.accessFailed());

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
     * Ignores runs that are already tracked.
     */
    track(start: HyperionJobStart): void {
        if (!this.loadedLogin) {
            return;
        }
        if (this.entriesSignal().some((entry) => entry.jobId === start.jobId)) {
            return;
        }
        const entry: HyperionJobEntry = {
            jobId: start.jobId,
            exerciseId: start.exerciseId,
            sourceExerciseId: start.sourceExerciseId,
            kind: start.kind,
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
        const entries = this.entriesSignal();
        const current = entries.find((entry) => entry.jobId === jobId);
        if (!current || current.seen || !isTerminalHyperionJobStatus(current.status)) {
            return;
        }
        this.seenJobIds.add(jobId);
        const next = entries.map((entry) => (entry.jobId === jobId ? cloneWith(entry, { seen: true }) : entry));
        this.setEntries(next);
    }

    /** Discover authorized runs, then revalidate retained history and active progress with bounded concurrency. */
    refresh(): void {
        this.discover();
    }

    loadMoreHistory(): void {
        const cursor = this.nextBeforeId();
        if (cursor !== undefined) this.discover(cursor);
    }

    private discover(beforeId?: number): void {
        if (!this.loadedLogin || this.historyLoading) return;
        const login = this.loadedLogin;
        this.historyLoading = true;
        defer(() => this.generationService.getRuns(beforeId))
            .pipe(
                timeout(STATUS_REQUEST_TIMEOUT_MS),
                take(1),
                takeUntil(this.identityChanged),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => (this.historyLoading = false)),
            )
            .subscribe({
                next: (page) => {
                    if (this.accountService.userIdentity()?.login !== login) return;
                    this.historyFailed.set(false);
                    if (beforeId !== undefined || this.nextBeforeId() === undefined) this.nextBeforeId.set(page.nextBeforeId);
                    const entries = new Map(this.entriesSignal().map((entry) => [entry.jobId, entry]));
                    for (const run of page.runs) entries.set(run.jobId, this.fromHistory(run, entries.get(run.jobId)));
                    this.setEntries([...entries.values()]);
                    this.revalidateRetainedAccess(new Set(page.runs.map((run) => run.jobId)));
                    this.refreshStatuses();
                },
                error: () => {
                    if (this.accountService.userIdentity()?.login === login) {
                        this.historyFailed.set(true);
                        this.revalidateRetainedAccess(new Set());
                        this.refreshStatuses();
                    }
                },
            });
    }

    private fromHistory(run: AuthoringRun, previous?: HyperionJobEntry): HyperionJobEntry {
        const states: Record<AuthoringRun['status'], HyperionJobStatus> = {
            QUEUED: 'queued',
            SAVED: 'saved',
            NEEDS_REVIEW: 'needsReview',
            PARTIAL: 'partial',
            ERROR: 'failed',
            CANCELLED: 'cancelled',
            UNKNOWN: 'unknown',
        };
        return {
            jobId: run.jobId,
            exerciseId: run.exerciseId,
            sourceExerciseId: run.sourceExerciseId,
            kind: run.kind,
            courseId: run.courseId,
            exerciseTitle: run.exerciseTitle ?? '',
            mode: run.kind === 'CREATE' ? 'GENERATE' : 'ADAPT',
            startedAt: run.startedAt,
            endedAt: run.finishedAt,
            status: run.revertedAt ? 'reverted' : run.running ? (previous?.status === 'cancelling' ? 'cancelling' : 'running') : states[run.status],
            seen: this.seenJobIds.has(run.jobId),
            message: previous?.message,
            phase: previous?.phase,
            cancellable: run.running ? previous?.cancellable : false,
        };
    }

    /** One bounded authorization batch per discovery, rotating through older pages without polling terminal replay. */
    private revalidateRetainedAccess(authorizedJobIds: ReadonlySet<string>): void {
        if (!this.loadedLogin || this.revalidatingAccess) return;
        const retained = this.entriesSignal().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !authorizedJobIds.has(entry.jobId));
        if (retained.length === 0) {
            this.accessFailed.set(false);
            this.accessOffset = 0;
            return;
        }
        const start = this.accessOffset % retained.length;
        const requested = retained.slice(start, start + MAX_ACCESS_BATCH_SIZE).map((entry) => entry.jobId);
        this.accessOffset = (start + requested.length) % retained.length;
        const requestedIds = new Set(requested);
        const login = this.loadedLogin;
        this.revalidatingAccess = true;
        defer(() => this.generationService.getRunAccess(requested))
            .pipe(
                timeout(STATUS_REQUEST_TIMEOUT_MS),
                take(1),
                takeUntil(this.identityChanged),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => (this.revalidatingAccess = false)),
            )
            .subscribe({
                next: (authorized) => {
                    if (this.accountService.userIdentity()?.login !== login) return;
                    const allowed = new Set(authorized);
                    this.accessFailed.set(false);
                    this.setEntries(this.entriesSignal().filter((entry) => !requestedIds.has(entry.jobId) || allowed.has(entry.jobId)));
                },
                error: (error: unknown) => {
                    if (this.accountService.userIdentity()?.login !== login) return;
                    if (error instanceof HttpErrorResponse && error.status === 403) {
                        this.accessFailed.set(false);
                        this.setEntries(this.entriesSignal().filter((entry) => !requestedIds.has(entry.jobId)));
                    } else {
                        this.accessFailed.set(true);
                    }
                },
            });
    }

    /** Re-read exact identities, not whichever job happens to be latest on their exercise. */
    private refreshStatuses(): void {
        if (!this.loadedLogin || this.refreshing) {
            return;
        }
        const retainedEntries = this.entriesSignal().filter((entry) => !isTerminalHyperionJobStatus(entry.status));
        if (retainedEntries.length === 0) {
            this.loadFailedSignal.set(false);
            return;
        }
        const login = this.loadedLogin;
        this.refreshing = true;
        this.loadFailedSignal.set(false);
        from(retainedEntries)
            .pipe(
                mergeMap((entry) => {
                    const requestedJobIds = new Set([entry.jobId]);
                    return defer(() => this.generationService.getRunStatus(entry.exerciseId, entry.jobId)).pipe(
                        timeout(STATUS_REQUEST_TIMEOUT_MS),
                        take(1),
                        tap((status) => {
                            if (this.accountService.userIdentity()?.login === login) {
                                this.reconcile(requestedJobIds, status ?? undefined);
                            }
                        }),
                        catchError((error: unknown) => {
                            if (this.accountService.userIdentity()?.login === login) {
                                if (error instanceof HttpErrorResponse && (error.status === 403 || error.status === 404)) {
                                    this.setEntries(this.entriesSignal().filter((current) => current.jobId !== entry.jobId));
                                } else {
                                    this.loadFailedSignal.set(true);
                                }
                            }
                            return EMPTY;
                        }),
                    );
                }, MAX_CONCURRENT_STATUS_REQUESTS),
                takeUntil(this.identityChanged),
                takeUntilDestroyed(this.destroyRef),
                finalize(() => (this.refreshing = false)),
            )
            .subscribe();
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

    private resolve(
        entry: HyperionJobEntry,
        status: HyperionGenerationStatus | undefined,
    ): { status: HyperionJobStatus; message?: string; endedAt?: string; phase?: HyperionGenerationEvent['phase']; cancellable?: boolean } {
        if (!status || status.jobId !== entry.jobId) {
            // The outcome is unknown; do not infer an end time from this response.
            return { status: 'unknown' };
        }
        const events = status.events ?? [];
        const lastMessage = [...events].reverse().find((event) => !!event.message)?.message;
        const phase = [...events].reverse().find((event) => event.type !== 'DONE' && event.phase)?.phase;
        if (status.running) {
            const cancelRequested = events.some((event) => event.type === 'CANCELLED');
            return { status: cancelRequested ? 'cancelling' : events.length > 0 ? 'running' : 'queued', message: lastMessage, phase, cancellable: status.cancellable };
        }
        return { status: terminalStatusOf(events), message: lastMessage, endedAt: terminalTimestampOf(events), phase };
    }

    private setEntries(entries: readonly HyperionJobEntry[]): void {
        const next = entries.toSorted((a, b) => Date.parse(b.startedAt) - Date.parse(a.startedAt));
        this.entriesSignal.set(next);
        this.persist();
        this.syncStreamSubscriptions(next);
    }

    /** Streams the most recent active runs; bounded REST sweeps reconcile every other run without dropping it. */
    private syncStreamSubscriptions(entries: readonly HyperionJobEntry[]): void {
        const wanted = new Set(
            entries
                .filter((entry) => !isTerminalHyperionJobStatus(entry.status))
                .slice(0, MAX_STREAM_SUBSCRIPTIONS)
                .map((entry) => entry.jobId),
        );
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
            this.accessFailed.set(false);
            this.accessOffset = 0;
        }
        if (!login) {
            this.loadedLogin = undefined;
            this.nextBeforeId.set(undefined);
            this.historyFailed.set(false);
            this.seenJobIds.clear();
            this.clearStreamSubscriptions();
            this.entriesSignal.set([]);
            this.loadFailedSignal.set(false);
            this.activeChanges.next(false);
            return;
        }
        if (login === this.loadedLogin) {
            return;
        }
        this.loadedLogin = login;
        this.nextBeforeId.set(undefined);
        this.historyFailed.set(false);
        this.seenJobIds = new Set();
        this.activeChanges.next(!!login);
        this.seenJobIds = readAcknowledgements(login);
        this.setEntries([]);
        this.refresh();
    }

    private persist(): void {
        if (!this.loadedLogin) {
            return;
        }
        const payload: PersistedRegistry = { version: STORAGE_VERSION, seenJobIds: [...this.seenJobIds].slice(-1000) };
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

/** Browser storage is presentation state, never a substitute for current server authorization. */
function readAcknowledgements(login: string): Set<string> {
    try {
        const parsed = parseJson<Partial<PersistedRegistry> | undefined>(localStorage.getItem(STORAGE_KEY_PREFIX + login) ?? 'null');
        return new Set(
            parsed?.version === STORAGE_VERSION && Array.isArray(parsed.seenJobIds) ? parsed.seenJobIds.filter((id): id is string => typeof id === 'string').slice(-1000) : [],
        );
    } catch {
        return new Set();
    }
}
