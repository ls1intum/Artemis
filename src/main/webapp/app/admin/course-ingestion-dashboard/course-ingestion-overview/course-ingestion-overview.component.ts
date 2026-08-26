import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumUiButtonComponent, TumUiCardComponent, TumUiMessageComponent, TumUiTableDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/**
 * What each Iris collection actually holds. The names come from the Iris pipeline and do not describe their contents:
 * `Lectures` stores slide page chunks, and `LectureUnits` stores one summary per unit. Showing only the raw name left a
 * reader to guess, and to wonder where the slides were.
 */
const COLLECTION_CONTENTS: Record<string, string> = {
    Lectures: 'artemisApp.courseIngestionDashboard.collections.contents.slides',
    LectureTranscriptions: 'artemisApp.courseIngestionDashboard.collections.contents.transcript',
    LectureUnitSegments: 'artemisApp.courseIngestionDashboard.collections.contents.segments',
    LectureUnits: 'artemisApp.courseIngestionDashboard.collections.contents.unitSummary',
};

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

    /** The label naming what a collection holds, where its own name does not. */
    protected contentLabelKey(collection: string): string | undefined {
        return COLLECTION_CONTENTS[collection];
    }

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
