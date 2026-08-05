import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/**
 * Renders the top-band index overview of the ingestion-observability dashboard: a "Services" area (Weaviate
 * reachability + address, and whether the Iris module is enabled) and an "Indexed collections" area (one row per
 * tracked collection with its live object count, or "unavailable" when the collection cannot be read). It consumes
 * {@link CourseIngestionDashboardService} directly and is fully read-only.
 */
@Component({
    selector: 'jhi-course-ingestion-overview',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective, TumUiTagComponent, TumUiButtonComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './course-ingestion-overview.component.html',
})
export class CourseIngestionOverviewComponent implements OnInit {
    private readonly dashboardService = inject(CourseIngestionDashboardService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;

    readonly overview = signal<IndexOverview | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    ngOnInit(): void {
        this.reload();
    }

    /** (Re)loads the index overview, re-checking Weaviate reachability and the live collection counts. */
    reload(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getIndexOverview()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.overview.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.overview.set(undefined);
                    this.error.set(true);
                    this.loading.set(false);
                },
            });
    }
}
