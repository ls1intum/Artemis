import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective } from '@tumaet/ui-angular';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionActivity, IngestionEventKind } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/**
 * How each event kind reads on the feed. `severity` drives the dot colour through a semantic state token, so a
 * reader scanning the column sees problems without reading any of the labels.
 *
 * `DRIFT_DETECTED`, `MISSING_DETECTED` and `ORPHAN_DETECTED` are warnings rather than errors on purpose: each one
 * is a self-healing pass doing its job, and the corresponding `INDEX_REPAIRED` that follows is the success.
 */
const KIND_SEVERITY: Record<IngestionEventKind, 'success' | 'warning' | 'danger' | 'muted'> = {
    INGESTED: 'success',
    INDEX_REPAIRED: 'success',
    CLAIMED: 'muted',
    REQUEUED: 'warning',
    QUALITY_FLAGGED: 'warning',
    DRIFT_DETECTED: 'warning',
    MISSING_DETECTED: 'warning',
    ORPHAN_DETECTED: 'warning',
    FAILED: 'danger',
    STALLED: 'danger',
};

/** Tailwind text colour per severity. Semantic tokens only; `bg-current` on the dot inherits from these. */
const SEVERITY_CLASS: Record<'success' | 'warning' | 'danger' | 'muted', string> = {
    success: 'text-state-success',
    warning: 'text-state-warning',
    danger: 'text-state-danger',
    muted: 'text-muted-color',
};

/** The kinds offered as filters, in the order a reader looks for them: outcomes first, then findings. */
const FILTERABLE_KINDS: IngestionEventKind[] = [
    'INGESTED',
    'FAILED',
    'STALLED',
    'REQUEUED',
    'QUALITY_FLAGGED',
    'DRIFT_DETECTED',
    'MISSING_DETECTED',
    'ORPHAN_DETECTED',
    'INDEX_REPAIRED',
    'CLAIMED',
];

/**
 * The pipeline activity feed: what the ingestion and searchable-entity pipelines actually did, newest first.
 *
 * This is the only view in the dashboard backed by an append-only record. Every other table in the area is a
 * current-state snapshot overwritten in place, so a drift repair or an orphan removal is invisible seconds after
 * it happens; the feed reads the event log instead and can therefore answer what happened while nobody was looking.
 *
 * Read-only and poll-free: it loads on open and on an explicit refresh, matching the rest of this dashboard.
 */
@Component({
    selector: 'jhi-course-ingestion-activity',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective, TumUiButtonComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
    templateUrl: './course-ingestion-activity.component.html',
})
export class CourseIngestionActivityComponent implements OnInit {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly filterableKinds = FILTERABLE_KINDS;

    readonly activity = signal<IngestionActivity | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    /** The kind the feed is narrowed to, or undefined for every kind. */
    readonly selectedKind = signal<IngestionEventKind | undefined>(undefined);

    readonly events = computed(() => this.activity()?.events ?? []);
    readonly isEmpty = computed(() => !this.loading() && !this.error() && this.events().length === 0);

    /** The summary tiles: every kind that actually occurred in the window, most frequent first. */
    readonly summary = computed(() => {
        const counts = this.activity()?.countsByKind ?? {};
        return FILTERABLE_KINDS.map((kind) => ({ kind, count: counts[kind] ?? 0 }))
            .filter((tile) => tile.count > 0)
            .sort((a, b) => b.count - a.count);
    });

    ngOnInit(): void {
        this.reload();
    }

    /** (Re)loads the feed under the current kind filter. */
    reload(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getActivity({ kind: this.selectedKind() })
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.activity.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.activity.set(undefined);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }

    /** Narrows the feed to one kind, or clears the filter when the selected kind is chosen again. */
    protected selectKind(kind: IngestionEventKind | undefined): void {
        this.selectedKind.set(this.selectedKind() === kind ? undefined : kind);
        this.reload();
    }

    /** The colour class for an event kind, so the template never hardcodes one. */
    protected kindClass(kind: IngestionEventKind): string {
        return SEVERITY_CLASS[KIND_SEVERITY[kind]];
    }

    /** The severity an assistive technology and the E2E tests read, rather than inferring it from a colour. */
    protected kindSeverity(kind: IngestionEventKind): string {
        return KIND_SEVERITY[kind];
    }
}
