import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faClock, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SlicePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';

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
    imports: [TranslateDirective, HelpIconComponent, FaIconComponent, RouterLink, ArtemisDatePipe, ArtemisTranslatePipe, SlicePipe],
})
export class QueuedJobsTableComponent {
    /** List of queued build jobs to display */
    buildJobs = input<BuildJob[]>([]);

    /** Whether to show the course ID column */
    showCourseId = input<boolean>(false);

    /** Emits the job ID when a job should be cancelled */
    cancelJob = output<string>();

    /** Emits when all queued jobs should be cancelled */
    cancelAll = output<void>();

    /** Emits the job ID when a job row is clicked for navigation */
    jobClick = output<string>();

    // Font Awesome icons
    readonly faTimes = faTimes;
    readonly faClock = faClock;

    /**
     * Handles the cancel button click for a single job.
     * Stops event propagation to prevent row click navigation.
     * @param jobId The ID of the job to cancel
     * @param event The click event
     */
    onCancelJob(jobId: string, event: Event): void {
        event.stopPropagation();
        this.cancelJob.emit(jobId);
    }

    /**
     * Handles the cancel all button click.
     */
    onCancelAll(): void {
        this.cancelAll.emit();
    }

    /**
     * Handles row click for navigation to job details.
     * @param jobId The ID of the clicked job
     */
    onJobClick(jobId: string | undefined): void {
        if (jobId) {
            this.jobClick.emit(jobId);
        }
    }

    /**
     * Stops event propagation for links within the table row.
     * @param event The click event
     */
    onLinkClick(event: Event): void {
        event.stopPropagation();
    }

    /**
     * Calculate the waiting time since submission for a queued job
     * @param submissionDate The submission date of the build job
     * @returns A formatted string showing the waiting time
     */
    calculateWaitingTime(submissionDate: dayjs.Dayjs | undefined): string {
        if (!submissionDate) {
            return '-';
        }
        const now = dayjs();
        const diffSeconds = now.diff(submissionDate, 'seconds');

        if (diffSeconds < 60) {
            return `${diffSeconds}s`;
        } else if (diffSeconds < 3600) {
            const minutes = Math.floor(diffSeconds / 60);
            const seconds = diffSeconds % 60;
            return `${minutes}m ${seconds}s`;
        } else {
            const hours = Math.floor(diffSeconds / 3600);
            const minutes = Math.floor((diffSeconds % 3600) / 60);
            return `${hours}h ${minutes}m`;
        }
    }
}
