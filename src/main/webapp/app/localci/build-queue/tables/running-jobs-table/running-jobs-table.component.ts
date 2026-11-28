import { BuildJobRowComponent } from '../build-job-row.component';
import { BuildJobsHeaderComponent } from '../build-jobs-header.component';
import { BuildJobsTableDirective } from '../build-jobs-table.directive';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TumAetUiTableDirective } from '@tumaet/ui-angular/table-directive';
import { TumAetUiTableLayoutComponent } from '@tumaet/ui-angular/table-layout';
/**
 * Component that displays a table of running build jobs.
 * Supports both admin and course-specific views with optional course ID column.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-running-jobs-table',
    templateUrl: './running-jobs-table.component.html',
    styleUrl: '../build-jobs-table.css',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumAetUiTableLayoutComponent, BuildJobRowComponent, BuildJobsHeaderComponent, TumAetUiTableDirective],
})
export class RunningJobsTableComponent extends BuildJobsTableDirective {
    /** Whether the component is in admin view (enables build agent links) */
    isAdminView = input<boolean>(false);
}
