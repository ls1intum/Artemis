import { ChangeDetectionStrategy, Component } from '@angular/core';
import { faMagnifyingGlassChart } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseIngestionOverviewComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-overview/course-ingestion-overview.component';
import { CourseIngestionCoverageTableComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-coverage-table/course-ingestion-coverage-table.component';

/**
 * Admin-only, read-only page for the ingestion-coverage observability dashboard. It shows, per course, how complete the
 * global-search Weaviate index is versus what the database expects. This foundation renders the index overview and a
 * basic coverage table; the rich per-type matrix and its toolbar are intentionally left for later collaboration.
 */
@Component({
    selector: 'jhi-course-ingestion-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, CourseIngestionOverviewComponent, CourseIngestionCoverageTableComponent],
    templateUrl: './course-ingestion-dashboard.component.html',
})
export class CourseIngestionDashboardComponent {
    protected readonly faMagnifyingGlassChart = faMagnifyingGlassChart;
}
