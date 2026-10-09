import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import { faMagnifyingGlassChart } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseIngestionOverviewComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-overview/course-ingestion-overview.component';
import { CourseIngestionCoverageTableComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-coverage-table/course-ingestion-coverage-table.component';
import { CourseIngestionBrowserComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser/course-ingestion-browser.component';
import { IngestionCoverage } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/**
 * Admin-only, read-only page for the ingestion-coverage observability dashboard. It shows, per course, how complete the
 * global-search Weaviate index is versus what the database expects. This foundation renders the index overview and a
 * basic coverage table; the rich per-type matrix and its toolbar are intentionally left for later collaboration.
 */
@Component({
    selector: 'jhi-course-ingestion-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, CourseIngestionOverviewComponent, CourseIngestionCoverageTableComponent, CourseIngestionBrowserComponent],
    templateUrl: './course-ingestion-dashboard.component.html',
})
export class CourseIngestionDashboardComponent {
    protected readonly faMagnifyingGlassChart = faMagnifyingGlassChart;

    /** The course whose content browser is open, or undefined while none has been chosen yet. */
    protected readonly selectedCourse = signal<IngestionCoverage | undefined>(undefined);

    protected readonly browserVisible = signal(false);

    private readonly overview = viewChild.required(CourseIngestionOverviewComponent);

    private readonly coverageTable = viewChild.required(CourseIngestionCoverageTableComponent);

    /** Opens the content browser for the course whose row was activated in the matrix. */
    protected openBrowser(course: IngestionCoverage): void {
        this.selectedCourse.set(course);
        this.browserVisible.set(true);
    }

    /** Reloads the matrix when the browser closes: opening the course stored its row afresh, so the matrix shows it. */
    protected onBrowserVisibleChange(visible: boolean): void {
        this.browserVisible.set(visible);
        if (!visible) {
            this.coverageTable().load();
        }
    }

    /** A refresh of the matrix reloads the index overview too, so the whole page is fresh after one click. */
    protected onCoverageRefreshed(): void {
        this.overview().reload();
    }
}
