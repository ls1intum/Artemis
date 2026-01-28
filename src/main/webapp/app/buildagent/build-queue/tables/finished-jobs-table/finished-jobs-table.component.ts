import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, SlicePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-finished-jobs-table',
    templateUrl: './finished-jobs-table.component.html',
    styleUrl: './finished-jobs-table.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, NgClass, RouterLink, SortDirective, SortByDirective, ResultComponent, ArtemisDatePipe, SlicePipe],
})
export class FinishedJobsTableComponent {
    // Inputs
    buildJobs = input<FinishedBuildJob[]>([]);
    showCourseId = input<boolean>(false);
    isAdminView = input<boolean>(false);

    // Two-way bindings
    predicate = model<string>('buildCompletionDate');
    ascending = model<boolean>(false);

    // Outputs
    jobClick = output<string>();
    viewLogs = output<string>();
    sortChange = output<void>();

    // Font Awesome icons
    readonly faSort = faSort;
    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;

    /**
     * Handles row click to navigate to job details
     * @param jobId The ID of the build job
     */
    onJobClick(jobId: string | undefined): void {
        if (jobId) {
            this.jobClick.emit(jobId);
        }
    }

    /**
     * Handles build logs link click
     * @param event The click event
     * @param jobId The ID of the build job
     */
    onViewLogs(event: Event, jobId: string | undefined): void {
        event.stopPropagation();
        if (jobId) {
            this.viewLogs.emit(jobId);
        }
    }

    /**
     * Handles sort change event
     */
    onSortChange(): void {
        this.sortChange.emit();
    }
}
