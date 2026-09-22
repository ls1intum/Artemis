import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective } from '@tumaet/ui-angular';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { ProcessingPhase, QueueOverview, RunningIngestion } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** The phases worth a depth tile, in the order work moves through them. */
const PHASE_ORDER: ProcessingPhase[] = ['IDLE', 'TRANSCRIBING', 'INGESTING', 'DONE', 'FAILED', 'SKIPPED'];

/** Tailwind colour per phase. Semantic tokens only. */
const PHASE_CLASS: Record<ProcessingPhase, string> = {
    IDLE: 'text-muted-color',
    TRANSCRIBING: 'text-state-info',
    INGESTING: 'text-state-info',
    DONE: 'text-state-success',
    FAILED: 'text-state-danger',
    SKIPPED: 'text-muted-color',
};

/**
 * A run whose progress counter has not advanced for this long, while its worker keeps heartbeating, is flagged
 * as wedged rather than slow. Matches the spirit of the server's stall window without claiming to be it: the
 * server owns the real decision and this is only a reading aid, so it is deliberately the more eager of the two.
 */
const WEDGED_AFTER_MS = 10 * 60 * 1000;

/**
 * Every queue this feature owns, and what each is doing right now.
 *
 * Two unrelated mechanisms are shown side by side on purpose: the lecture ingestion queue is a table that Pyris
 * workers pull from, and the Weaviate outbox is a durable write queue drained on the scheduling node. An operator
 * asking "why is nothing being indexed" has to rule out both, and before this view neither had a UI at all.
 *
 * A point-in-time snapshot per request, with no websocket push: the admin page reloads on demand, so an idle
 * dashboard costs the server nothing.
 */
@Component({
    selector: 'jhi-course-ingestion-queues',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective, TumUiButtonComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
    templateUrl: './course-ingestion-queues.component.html',
})
export class CourseIngestionQueuesComponent implements OnInit {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;

    readonly queues = signal<QueueOverview | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    readonly running = computed(() => this.queues()?.lectureIngestion?.running ?? []);
    readonly nextUp = computed(() => this.queues()?.lectureIngestion?.nextUp ?? []);
    readonly workers = computed(() => this.queues()?.workers ?? []);
    readonly reconcilePasses = computed(() => this.queues()?.reconcilePasses ?? []);
    readonly outbox = computed(() => this.queues()?.weaviateOutbox);

    /** The phase depth tiles, skipping phases that hold nothing so the row stays readable. */
    readonly phaseTiles = computed(() => {
        const counts = this.queues()?.lectureIngestion?.countsByPhase ?? {};
        return PHASE_ORDER.map((phase) => ({ phase, count: counts[phase] ?? 0 })).filter((tile) => tile.count > 0);
    });

    /** True when nothing at all is queued or running, so the view can say so rather than show four empty tables. */
    readonly isIdle = computed(() => this.running().length === 0 && this.nextUp().length === 0 && (this.outbox()?.total ?? 0) === 0 && this.phaseTiles().length === 0);

    ngOnInit(): void {
        this.reload();
    }

    /** (Re)takes the snapshot. */
    reload(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getQueues()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.queues.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.queues.set(undefined);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }

    protected phaseClass(phase: ProcessingPhase): string {
        return PHASE_CLASS[phase];
    }

    /**
     * Whether a run looks wedged: its worker is still renewing the lease, but the progress counter has not moved.
     * That combination is the one an operator most needs pointed out, because the run looks alive from every other
     * column on the row.
     */
    protected isWedged(run: RunningIngestion): boolean {
        if (!run.lastProgressAt) {
            return false;
        }
        return Date.now() - new Date(run.lastProgressAt).getTime() > WEDGED_AFTER_MS;
    }

    /** The stage label with its progress counter, when the stage reports one. */
    protected stageLabel(run: RunningIngestion): string {
        if (!run.stage) {
            return '';
        }
        if (run.stageTotal === undefined || run.stageProgress === undefined) {
            return run.stage;
        }
        return `${run.stage} ${run.stageProgress}/${run.stageTotal}`;
    }
}
