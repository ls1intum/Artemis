import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent } from '@tumaet/ui-angular';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IngestionLogEntry } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** The levels offered as filters, most severe first. */
const LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG'] as const;

/** Tailwind colour per level. Semantic tokens only. */
const LEVEL_CLASS: Record<string, string> = {
    ERROR: 'text-state-danger',
    WARN: 'text-state-warning',
    INFO: 'text-state-info',
    DEBUG: 'text-muted-color',
    TRACE: 'text-muted-color',
};

/**
 * The live ingestion log of both services, newest first.
 *
 * Artemis and Iris both log only to the console, so when the log collector is unavailable there is no way to
 * see what the indexing pipelines are doing without shell access to either host — and the error key Artemis
 * records for a failed run ("SLIDE_VISION_FAILED") names the failure without explaining it. Each service keeps
 * a small in-memory buffer of its own ingestion records at DEBUG; the server merges them by timestamp so this
 * reads as one story across the two rather than two lists to correlate by eye.
 *
 * The buffers are bounded and lost on restart, and on a clustered deployment the Artemis half holds only the
 * records of the node that answered. The view says so rather than implying it is complete.
 */
@Component({
    selector: 'jhi-course-ingestion-logs',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiCardComponent, TumUiMessageComponent, TumUiButtonComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe],
    templateUrl: './course-ingestion-logs.component.html',
    styleUrls: ['./course-ingestion-logs.component.scss'],
})
export class CourseIngestionLogsComponent implements OnInit {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly levels = LEVELS;

    readonly entries = signal<IngestionLogEntry[]>([]);
    readonly loading = signal(true);
    readonly error = signal(false);

    /** The level the log is narrowed to, or undefined for every level. */
    readonly selectedLevel = signal<string | undefined>(undefined);

    /** Free-text filter, applied client-side over the loaded page. */
    readonly search = signal('');

    /** Which entries are expanded to show their stack trace. */
    private readonly expanded = signal<ReadonlySet<number>>(new Set());

    readonly visibleEntries = computed(() => {
        const needle = this.search().trim().toLowerCase();
        if (!needle) {
            return this.entries();
        }
        return this.entries().filter((entry) => entry.message.toLowerCase().includes(needle) || entry.logger.toLowerCase().includes(needle));
    });

    readonly isEmpty = computed(() => !this.loading() && !this.error() && this.visibleEntries().length === 0);

    /** True once either service has contributed at least one record, so an empty view can explain itself. */
    readonly hasIrisRecords = computed(() => this.entries().some((entry) => entry.source === 'IRIS'));

    ngOnInit(): void {
        this.reload();
    }

    /** (Re)loads the log under the current level filter. */
    reload(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getIngestionLogs(this.selectedLevel())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.entries.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.entries.set([]);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }

    /** Narrows to one level, or clears the filter when the selected level is chosen again. */
    protected selectLevel(level: string): void {
        this.selectedLevel.set(this.selectedLevel() === level ? undefined : level);
        this.reload();
    }

    protected onSearch(value: string): void {
        this.search.set(value);
    }

    protected levelClass(level: string): string {
        return LEVEL_CLASS[level] ?? 'text-muted-color';
    }

    protected isExpanded(index: number): boolean {
        return this.expanded().has(index);
    }

    /** Stack traces are long, so they open on demand rather than making every row tall. */
    protected toggleStackTrace(index: number): void {
        const next = new Set(this.expanded());
        if (!next.delete(index)) {
            next.add(index);
        }
        this.expanded.set(next);
    }
}
