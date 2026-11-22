import { BuildJobRowComponent } from '../build-job-row.component';
import { BuildJobsHeaderComponent } from '../build-jobs-header.component';
import { BuildJobsTableDirective } from '../build-jobs-table.directive';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTableDirective } from '@tumaet/ui-angular';
/**
 * Component that displays a table of running build jobs.
 * Supports both admin and course-specific views with optional course ID column.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-running-jobs-table',
    templateUrl: './running-jobs-table.component.html',
    styleUrls: ['../build-jobs-table.scss', './running-jobs-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BuildJobRowComponent, BuildJobsHeaderComponent, TranslateDirective, TumAetUiTableDirective],
})
export class RunningJobsTableComponent extends BuildJobsTableDirective {
    /** Whether the component is in admin view (enables build agent links) */
    isAdminView = input<boolean>(false);
}
