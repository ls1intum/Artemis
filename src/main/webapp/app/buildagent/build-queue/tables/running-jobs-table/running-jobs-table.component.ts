import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faExclamationCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, SlicePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

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
    imports: [TranslateDirective, HelpIconComponent, FaIconComponent, NgClass, RouterLink, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, ArtemisTranslatePipe, SlicePipe],
})
export class RunningJobsTableComponent {
    /** List of running build jobs to display */
    buildJobs = input<BuildJob[]>([]);

    /** Whether to show the course ID column */
    showCourseId = input<boolean>(false);

    /** Whether the component is in admin view (enables build agent links) */
    isAdminView = input<boolean>(false);

    /** Course ID for building navigation routes (undefined for admin view) */
    courseId = input<number | undefined>(undefined);

    /** Emits the job ID when a job should be cancelled */
    cancelJob = output<string>();

    /** Emits when all running jobs should be cancelled */
    cancelAll = output<void>();

    /** Emits the job ID when a job row is clicked for navigation */
    jobClick = output<string>();

    // Font Awesome icons
    readonly faTimes = faTimes;
    readonly faSpinner = faSpinner;
    readonly faExclamationCircle = faExclamationCircle;

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
     * Builds the router link array for navigating to job details.
     * Uses course-specific route when courseId is provided, otherwise admin route.
     * @param jobId The ID of the build job
     */
    getJobDetailRoute(jobId: string | undefined): string[] {
        if (!jobId) {
            return [];
        }
        const courseId = this.courseId();
        if (courseId) {
            return ['/course-management', String(courseId), 'build-overview', jobId, 'job-details'];
        }
        return ['/admin', 'build-overview', jobId, 'job-details'];
    }
}
