import dayjs from 'dayjs/esm';
import { BuildJobRowComponent } from '../build-job-row.component';
import { BuildJobsHeaderComponent } from '../build-jobs-header.component';
import { BuildJobsTableDirective } from '../build-jobs-table.directive';
import { ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiTableDirective } from '@tumaet/ui-angular';
/**
 * Component that displays a table of queued build jobs.
 * Supports both admin and course-specific views with optional course ID column.
 *
 * Uses OnPush change detection for optimal performance.
 */
@Component({
    selector: 'jhi-queued-jobs-table',
    templateUrl: './queued-jobs-table.component.html',
    styleUrls: ['../build-jobs-table.scss', './queued-jobs-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [BuildJobRowComponent, BuildJobsHeaderComponent, TranslateDirective, TumAetUiTableDirective],
})
export class QueuedJobsTableComponent extends BuildJobsTableDirective {
    /** The parent refreshes the jobs array every second while retaining the queued job objects. */
    readonly displayTime = computed(() => {
        this.buildJobs();
        return dayjs();
    });
}
