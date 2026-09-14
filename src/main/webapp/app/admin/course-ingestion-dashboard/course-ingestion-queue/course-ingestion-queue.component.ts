import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, combineLatest, interval, startWith, switchMap } from 'rxjs';
import { catchError, of } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faListCheck } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { OutboxQueueEntry, OutboxQueueStatus, ReconcileStatus } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/** How often the queue is re-read. Short enough to watch a drain, long enough not to hammer the endpoint. */
const POLL_INTERVAL_MS = 2_000;

/** How long a finished row stays visible before it is dropped, so a fast drain is still readable. */
const DONE_LINGER_MS = 4_000;

/** A queue row as rendered, including the rows that have already finished and are on their way out. */
export interface QueueRow extends OutboxQueueEntry {
    status: OutboxQueueStatus;
}

/**
 * Live view of the Weaviate outbox: what the reconcile passes and live writes have queued, and what is draining.
 * <p>
 * The outbox has no "processing" state to show. The dispatcher never mutates a row while applying it, which is
 * exactly what lets a crash mid-batch leave the row to be re-read and re-applied, so a row is either outstanding
 * or gone. Completion is therefore inferred: a row present in one poll and absent from the next was applied, and
 * is shown as done for {@link DONE_LINGER_MS} before it disappears.
 */
@Component({
    selector: 'jhi-course-ingestion-queue',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
    templateUrl: './course-ingestion-queue.component.html',
    styleUrls: ['./course-ingestion-queue.component.scss'],
})
export class CourseIngestionQueueComponent implements OnDestroy {
    private readonly service = inject(CourseIngestionDashboardService);

    protected readonly faListCheck = faListCheck;

    /** Rows the server still holds, newest poll wins. */
    private readonly pending = signal<OutboxQueueEntry[]>([]);
    /** Rows that vanished from the queue, kept briefly so the reader sees them complete. */
    private readonly finished = signal<OutboxQueueEntry[]>([]);
    protected readonly totalDepth = signal(0);
    /** What the reconcile passes have been doing, so a silent reconciler is distinguishable from a dead one. */
    protected readonly reconcile = signal<ReconcileStatus | undefined>(undefined);
    protected readonly loadFailed = signal(false);
    /** True until the first poll answers, so an empty queue is not announced before it is known. */
    protected readonly loading = signal(true);

    private readonly lingerTimers = new Map<number, ReturnType<typeof setTimeout>>();
    private readonly pollSubscription: Subscription;

    /** Queued and retrying rows in dispatch order, with finished rows kept in place until they expire. */
    protected readonly rows = computed<QueueRow[]>(() => {
        const now = Date.now();
        const live: QueueRow[] = this.pending().map((entry) => cloneWith(entry, { status: this.statusOf(entry, now) }));
        const done: QueueRow[] = this.finished().map((entry) => cloneWith(entry, { status: 'done' as const }));
        return [...done, ...live].sort((a, b) => a.id - b.id);
    });

    protected readonly isEmpty = computed(() => !this.loading() && this.rows().length === 0);

    constructor() {
        this.pollSubscription = interval(POLL_INTERVAL_MS)
            .pipe(
                startWith(0),
                switchMap(() =>
                    combineLatest([
                        this.service.getOutboxQueue().pipe(
                            catchError(() => {
                                this.loadFailed.set(true);
                                return of(undefined);
                            }),
                        ),
                        // Reconcile state is supporting detail; a failure there must not blank the queue.
                        this.service.getReconcileStatus().pipe(catchError(() => of(undefined))),
                    ]),
                ),
                takeUntilDestroyed(),
            )
            .subscribe(([queue, reconcile]) => {
                this.loading.set(false);
                this.reconcile.set(reconcile);
                if (!queue) {
                    return;
                }
                this.loadFailed.set(false);
                this.reconcileRows(queue.entries ?? []);
                this.totalDepth.set(queue.totalDepth ?? 0);
            });
    }

    ngOnDestroy(): void {
        this.pollSubscription.unsubscribe();
        this.lingerTimers.forEach((timer) => clearTimeout(timer));
        this.lingerTimers.clear();
    }

    /**
     * Folds a fresh poll into the view: rows that left the queue were applied, so they move to the finished list
     * for a few seconds. A row already lingering is never resurrected, which keeps a re-enqueued id from flickering.
     */
    private reconcileRows(entries: OutboxQueueEntry[]): void {
        const incomingIds = new Set(entries.map((entry) => entry.id));
        const vanished = this.pending().filter((entry) => !incomingIds.has(entry.id) && !this.lingerTimers.has(entry.id));

        this.pending.set(entries.filter((entry) => !this.lingerTimers.has(entry.id)));

        if (vanished.length === 0) {
            return;
        }
        this.finished.update((current) => [...current, ...vanished]);
        for (const entry of vanished) {
            const timer = setTimeout(() => {
                this.lingerTimers.delete(entry.id);
                this.finished.update((current) => current.filter((done) => done.id !== entry.id));
            }, DONE_LINGER_MS);
            this.lingerTimers.set(entry.id, timer);
        }
    }

    /** A row waiting out a backoff after a failure is retrying; anything else is simply queued. */
    private statusOf(entry: OutboxQueueEntry, now: number): OutboxQueueStatus {
        const due = entry.nextAttemptAt ? Date.parse(entry.nextAttemptAt) : 0;
        return entry.attempts > 0 && due > now ? 'retrying' : 'queued';
    }

    /** Semantic state colour for a status, so the tint in CSS can follow `currentColor` rather than name a colour. */
    protected statusClass(status: OutboxQueueStatus): string {
        switch (status) {
            case 'retrying':
                return 'text-state-warning';
            case 'done':
                return 'text-state-success';
            default:
                return 'text-state-info';
        }
    }

    /** Label for the row's subject: bulk operations carry no single entity. */
    protected subjectOf(row: QueueRow): string {
        return row.entityType ? `${row.entityType}${row.entityId !== undefined ? ' ' + row.entityId : ''}` : row.operation;
    }

    /** Total ledger rows across all types: the universe the missing and drift passes reason about. */
    protected readonly ledgerTotal = computed(() => (this.reconcile()?.ledger ?? []).reduce((sum, entry) => sum + entry.count, 0));

    /** True when reconcile has never recorded a run, which is what a switched-off reconciler looks like. */
    protected readonly reconcileNeverRan = computed(() => (this.reconcile()?.passes ?? []).every((pass) => !pass.lastRunAt));

    /** True when the queue holds more rows than the page the server returned. */
    protected readonly hasMore = computed(() => this.totalDepth() > this.pending().length);
}
